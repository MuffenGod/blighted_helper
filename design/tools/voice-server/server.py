"""Blighted Dreams local voice server.

An OpenAI-style text-to-speech endpoint (POST /v1/audio/speech) backed by Qwen3-TTS: the 0.6B Base model clones a
voice from cached per-mood reference clips and streams audio through faster-qwen3-tts CUDA graphs.
Design notes and measurements: design/HELPER_PLAN.md section 6c.

    start_voice_server.ps1                      (uses C:\\Users\\srafa\\tts for the venv, models and line cache)

Startup: the HTTP port opens at once and /health says "loading" while the model loads, CUDA graphs are captured, one
clone prompt per mood is built and cached in memory, and one throwaway line per mood is synthesized. Speech requests
get 503 + Retry-After until the state is "ready".

Speech request (JSON): input (text), voice ("emily" or "emily:worried"), mood (optional), response_format ("pcm" =
streamed raw 16-bit little-endian mono, the OpenAI pcm format, or "wav" = one complete WAV), chunk_size (optional,
codec steps per streamed chunk, 12 steps = 1 s), cache (optional, keep/replay the finished line on disk).
The sample rate is in the X-Sample-Rate response header (24000). Closing the connection cancels the synthesis.

Only one line is synthesized at a time (one GPU, one CUDA-graph set); further requests wait their turn. Binds to
127.0.0.1 by default. Set VOICE_SERVER_KEY (or --api-key) to require "Authorization: Bearer <key>".
"""
import argparse
import asyncio
import hashlib
import io
import json
import os
import re
import sys
import threading
import time
import wave
from concurrent.futures import ThreadPoolExecutor
from dataclasses import dataclass, field
from pathlib import Path
from typing import Any, Dict, Optional

HERE = Path(__file__).resolve().parent
DEFAULT_HOME = Path(os.environ.get("BD_TTS_HOME", r"C:\Users\srafa\tts"))
MAX_INPUT_CHARS = 1500


def log(msg: str) -> None:
    print(f"[voice {time.strftime('%H:%M:%S')}] {msg}", flush=True)


# --------------------------------------------------------------------------------------------- text handling

_SENTENCE_END = re.compile(r"(?<=[.!?…])\s+")
_SOFT_BREAK = re.compile(r"(?<=[,;:])\s+")
_STAGE_DIRECTION = re.compile(r"\*[^*]+\*")
_PARENTHETICAL = re.compile(r"\([^)]*\)")
_BRACKETED_TAG = re.compile(r"\[[^\]]*\]")


def normalize_for_speech(text: str) -> str:
    """Normalize text for natural TTS delivery.

    Strips stage directions, asterisks, parentheticals, and bracketed tags so the neural TTS
    never pronounces tags, bracket names, or artificial phonetic sound tokens aloud. It also
    expands chat shorthand and turns written pauses into commas, which gives the same voice
    model much better material to perform without changing the voice itself.
    """
    if not text:
        return ""
    # Remove markdown / stage actions: *giggles* or *draws blade*
    text = _STAGE_DIRECTION.sub("", text)
    # Remove parenthetical cues: (laughs), (sighs)
    text = _PARENTHETICAL.sub("", text)
    # Remove bracketed tags: [laugh], [giggle], [chuckle], [happy], etc.
    text = _BRACKETED_TAG.sub("", text)

    # Written punctuation and symbols that a voice actor would phrase differently.
    text = text.replace("\u2014", " ").replace("\u2013", " ").replace("&", " and ").replace("\u2192", " ")
    text = re.sub(r"(?i)\bw/o\b", "without", text)
    text = re.sub(r"(?i)\bw/\b", "with", text)
    text = re.sub(r"(?i)\be\.g\.", "for example", text)
    text = re.sub(r"(?i)\bi\.e\.", "that is", text)
    text = re.sub(r"(?i)\betc\.", "et cetera", text)
    text = re.sub(r"(?i)\bvs\.?", "versus", text)
    text = re.sub(r"(?i)\bmr\.", "mister", text)
    text = re.sub(r"(?i)\bmrs\.", "missus", text)
    text = re.sub(r"(?i)\bdr\.", "doctor", text)

    # Keep game acronyms speakable.
    for pattern, spoken in (
        (r"(?i)\bPvP\b", "player versus player"),
        (r"(?i)\bXP\b", "experience"),
        (r"(?i)\bHP\b", "health"),
        (r"(?i)\bAFK\b", "away from keyboard"),
        (r"(?i)\bNPC\b", "non player character"),
        (r"(?i)\bDPS\b", "damage per second"),
        (r"(?i)\bTTS\b", "text to speech"),
    ):
        text = re.sub(pattern, spoken, text)

    # Chat shorthand that otherwise gets spelled out letter by letter.
    for pattern, spoken in (
        (r"(?i)\bngl\b", "not gonna lie"),
        (r"(?i)\btbh\b", "to be honest"),
        (r"(?i)\bidk\b", "I don't know"),
        (r"(?i)\bimo\b", "in my opinion"),
        (r"(?i)\bbtw\b", "by the way"),
        (r"(?i)\brn\b", "right now"),
        (r"(?i)\bbrb\b", "be right back"),
        (r"(?i)\bgtg\b", "got to go"),
        (r"(?i)\bpls\b", "please"),
        (r"(?i)\bthx\b", "thanks"),
        (r"(?i)\blol\b", "heh"),
        (r"(?i)\bomg\b", "oh my god"),
        (r"(?i)\bwtf\b", "what the hell"),
        (r"(?i)\birl\b", "in real life"),
    ):
        text = re.sub(pattern, spoken, text)

    # Written pauses become short spoken pauses, not long dead air.
    text = re.sub(r"\s*\u2026\s*", ", ", text)
    text = re.sub(r"\s*\.{2,}\s*", ", ", text)
    text = re.sub(r"\s+-\s+", ", ", text)
    # Clean up leading punctuation left from stripped tags (e.g. ", That was close" -> "That was close")
    text = re.sub(r"^\s*[,;:]+\s*", "", text)
    # Normalize multiple punctuation or spaces
    text = re.sub(r"\s+", " ", text).strip()
    return text


def split_sentences(text: str, min_chars: int = 24, max_chars: int = 260) -> list:
    """Split text into sentence-sized pieces to synthesize one after another.

    Tiny fragments are merged into the next piece (so "Oh. Yes." is one piece), and very long sentences are broken at
    commas, then at spaces, so no single synthesis runs long enough to drift.
    """
    text = normalize_for_speech(text)
    pieces = []
    for sentence in _SENTENCE_END.split(text):
        if len(sentence) <= max_chars:
            pieces.append(sentence)
            continue
        buf = ""
        for part in _SOFT_BREAK.split(sentence):
            while len(part) > max_chars:  # no commas: hard split at the last space before the limit
                cut = part.rfind(" ", 0, max_chars)
                cut = cut if cut > 0 else max_chars
                pieces.append((buf + " " + part[:cut]).strip() if buf else part[:cut])
                buf, part = "", part[cut:].strip()
            if buf and len(buf) + 1 + len(part) > max_chars:
                pieces.append(buf)
                buf = part
            else:
                buf = f"{buf} {part}".strip()
        if buf:
            pieces.append(buf)

    merged, carry = [], ""
    for p in pieces:
        if not re.search(r"\w", p):
            continue
        carry = f"{carry} {p}".strip() if carry else p
        if len(carry) >= min_chars:
            merged.append(carry)
            carry = ""
    if carry:
        if merged and len(merged[-1]) + 1 + len(carry) <= max_chars:
            merged[-1] = f"{merged[-1]} {carry}"
        else:
            merged.append(carry)
    return merged


# --------------------------------------------------------------------------------------------- voices


@dataclass
class Mood:
    name: str
    wav: Path
    ref_text: str
    prompt: Any = None  # cached voice-clone prompt, built at startup


@dataclass
class Voice:
    name: str
    language: str
    default_mood: str
    version: str
    moods: Dict[str, Mood] = field(default_factory=dict)
    postfx: Optional[dict] = None  # optional streaming EQ from voice.json, see PostFx


class PostFx:
    """Streaming EQ (RBJ biquads) and clean gain scaling for the synthesized audio."""

    def __init__(self, cfg: dict, sample_rate: int):
        import numpy as np

        self.sections = []
        for f in cfg.get("filters", []):
            g, fr, kind = float(f["gain_db"]), float(f["freq"]), f["type"]
            A = 10 ** (g / 40)
            w = 2 * np.pi * fr / sample_rate
            c, s = np.cos(w), np.sin(w)
            if kind == "peak":
                al = s / (2 * float(f.get("q", 1.0)))
                b, a = [1 + al * A, -2 * c, 1 - al * A], [1 + al / A, -2 * c, 1 - al / A]
            else:
                al = s / 2 * np.sqrt(2)
                sq = 2 * np.sqrt(A) * al
                if kind == "lowshelf":
                    b = [A * ((A + 1) - (A - 1) * c + sq), 2 * A * ((A - 1) - (A + 1) * c), A * ((A + 1) - (A - 1) * c - sq)]
                    a = [(A + 1) + (A - 1) * c + sq, -2 * ((A - 1) + (A + 1) * c), (A + 1) + (A - 1) * c - sq]
                elif kind == "highshelf":
                    b = [A * ((A + 1) + (A - 1) * c + sq), -2 * A * ((A - 1) + (A + 1) * c), A * ((A + 1) + (A - 1) * c - sq)]
                    a = [(A + 1) - (A - 1) * c + sq, 2 * ((A - 1) - (A + 1) * c), (A + 1) - (A - 1) * c - sq]
                else:
                    raise ValueError(f"unknown postfx filter type {kind!r}")
            self.sections.append((np.array(b) / a[0], np.array(a) / a[0]))

    def new_state(self) -> list:
        import numpy as np

        return [np.zeros(2) for _ in self.sections]

    def process(self, x, state: list):
        from scipy.signal import lfilter
        import numpy as np

        y = np.asarray(x, dtype=np.float64)
        for i, (b, a) in enumerate(self.sections):
            y, state[i] = lfilter(b, a, y, zi=state[i])
        return y.astype(np.float32)


def read_voices(voices_dir: Path) -> Dict[str, Voice]:
    voices = {}
    for d in sorted(p for p in voices_dir.iterdir() if p.is_dir()):
        cfg_path = d / "voice.json"
        if not cfg_path.exists():
            continue
        raw = cfg_path.read_bytes()
        cfg = json.loads(raw.decode("utf-8"))
        digest = hashlib.sha1(raw)
        v = Voice(cfg["name"], cfg.get("language", "English"), cfg["default_mood"], "")
        for mood, m in cfg["moods"].items():
            wav = d / m["file"]
            digest.update(wav.read_bytes())
            v.moods[mood] = Mood(mood, wav, m["text"])
        if v.default_mood not in v.moods:
            raise ValueError(f"voice {v.name}: default_mood {v.default_mood!r} is not one of {list(v.moods)}")
        v.version = digest.hexdigest()[:12]
        v.postfx = cfg.get("postfx")
        voices[v.name] = v
    if not voices:
        raise SystemExit(f"no voices found in {voices_dir}")
    return voices


# --------------------------------------------------------------------------------------------- engine


class Engine:
    """Owns the model. Every GPU call runs on one dedicated thread (CUDA graphs are captured and replayed there)."""

    def __init__(self, args):
        self.args = args
        self.voices = read_voices(Path(args.voices_dir))
        self.executor = ThreadPoolExecutor(max_workers=1, thread_name_prefix="gpu")
        self.lock = asyncio.Lock()
        self.state = "loading"
        self.error: Optional[str] = None
        self.model = None
        self.sample_rate = 24000
        self.load_seconds: Optional[float] = None
        self.started = time.time()
        self.requests = 0
        self.last_activity = time.monotonic()
        self.trimmed = True
        self.cache_dir = Path(args.cache_dir) if args.cache_dir else None
        if self.cache_dir:
            self.cache_dir.mkdir(parents=True, exist_ok=True)

    # ---- startup (runs on the gpu thread)
    def load(self) -> None:
        t_all = time.perf_counter()
        try:
            os.environ.setdefault("HF_HOME", str(DEFAULT_HOME / "hf_cache"))
            os.environ.setdefault("HF_HUB_OFFLINE", "1")
            import torch
            from faster_qwen3_tts import FasterQwen3TTS

            t0 = time.perf_counter()
            self.model = FasterQwen3TTS.from_pretrained(self.args.model)
            log(f"model loaded in {time.perf_counter() - t0:.1f}s")
            t0 = time.perf_counter()
            self.model.warmup()
            log(f"CUDA graphs captured in {time.perf_counter() - t0:.1f}s")

            self.is_custom_voice = getattr(self.model.model.model, "tts_model_type", None) == "custom_voice"
            if self.is_custom_voice:
                speakers = list(getattr(self.model.model.model.talker.config, "spk_id", {}).keys())
                log(f"custom voice mode active; registered speakers: {speakers}")
                for voice in self.voices.values():
                    t0 = time.perf_counter()
                    for _ in self.synth("Ready.", voice, voice.default_mood, self.args.chunk_size):
                        pass
                    log(f"warm-up line {voice.name} {time.perf_counter() - t0:.2f}s")
            else:
                shared = {}  # moods that use the same reference clip + text share one clone prompt
                for voice in self.voices.values():
                    for mood in voice.moods.values():
                        key = (str(mood.wav), mood.ref_text)
                        if key in shared:
                            mood.prompt = shared[key][0]
                            log(f"prompt {voice.name}:{mood.name} shares the prompt of {shared[key][1]}")
                            continue
                        t0 = time.perf_counter()
                        mood.prompt = self.model.model.create_voice_clone_prompt(
                            ref_audio=str(mood.wav), ref_text=mood.ref_text, x_vector_only_mode=False)
                        shared[key] = (mood.prompt, f"{voice.name}:{mood.name}")
                        log(f"prompt {voice.name}:{mood.name} cached in {time.perf_counter() - t0:.2f}s")

                # one throwaway line per distinct prompt so the first real line pays no first-use cost
                warmed = set()
                for voice in self.voices.values():
                    for mood in voice.moods:
                        if id(voice.moods[mood].prompt) in warmed:
                            continue
                        warmed.add(id(voice.moods[mood].prompt))
                        t0 = time.perf_counter()
                        for _ in self.synth("Ready.", voice, mood, self.args.chunk_size):
                            pass
                        log(f"warm-up line {voice.name}:{mood} {time.perf_counter() - t0:.2f}s")

            torch.cuda.empty_cache()
            self.load_seconds = time.perf_counter() - t_all
            self.state = "ready"
            log(f"READY in {self.load_seconds:.1f}s | VRAM allocated {torch.cuda.memory_allocated() / 1e9:.2f} GB, "
                f"reserved {torch.cuda.memory_reserved() / 1e9:.2f} GB")
        except BaseException as e:  # noqa: BLE001 - report any startup failure through /health
            self.state = "error"
            self.error = f"{type(e).__name__}: {e}"
            log(f"STARTUP FAILED: {self.error}")
            raise

    # ---- synthesis (generator; every next() must run on the gpu thread)
    def synth(self, text: str, voice: Voice, mood: str, chunk_size: int):
        import numpy as np

        m = voice.moods[mood]
        fx = PostFx(voice.postfx, self.sample_rate) if voice.postfx else None
        fx_state = fx.new_state() if fx else None  # one filter state per request: no seams between chunks or sentences

        def shaped(x):
            return fx.process(x, fx_state) if fx else x

        MOOD_PAUSES = {
            "battle": 0.02,
            "whisper": 0.04,
            "flirt": 0.03,
            "sincere": 0.03,
            "worried": 0.02,
            "smug": 0.03,
            "tease": 0.03,
            "calm": 0.02,
            "mock": 0.02,
        }
        pause_sec = MOOD_PAUSES.get(mood, 0.02)

        MOOD_INSTRUCTS = {
            "battle": "Speak with urgency and excitement in battle.",
            "whisper": "Whisper quietly and cautiously.",
            "worried": "Speak with concern and worry.",
            "calm": "Speak in a calm, relaxed, friendly tone.",
            "cheerful": "Speak cheerfully with an upbeat tone.",
            "sincere": "Speak warmly and sincerely.",
            "smug": "Speak with confident, smug amusement.",
            "tease": "Speak in a playful, teasing tone.",
            "mock": "Speak with sarcastic amusement.",
            "flirt": "Speak in a charming, playful tone.",
        }
        instruct = MOOD_INSTRUCTS.get(mood) if getattr(self, "is_custom_voice", False) else None

        first = True
        for sentence in split_sentences(text):
            if not first and pause_sec > 0:  # mood-adaptive pause between sentences
                yield shaped(np.zeros(int(pause_sec * self.sample_rate), dtype=np.float32))
            first = False
            if getattr(self, "is_custom_voice", False):
                spk = voice.name
                spk_id_map = getattr(self.model.model.model.talker.config, "spk_id", {})
                if spk not in spk_id_map and spk_id_map:
                    spk = next(iter(spk_id_map.keys()))
                for audio, sr, _timing in self.model.generate_custom_voice_streaming(
                        text=sentence, speaker=spk, language=voice.language,
                        instruct=instruct, chunk_size=chunk_size):
                    if sr != self.sample_rate:
                        log(f"WARNING: model sample rate {sr} differs from the advertised {self.sample_rate}")
                        self.sample_rate = sr
                    yield shaped(np.asarray(audio, dtype=np.float32))
            else:
                for audio, sr, _timing in self.model.generate_voice_clone_streaming(
                        text=sentence, language=voice.language, ref_text=m.ref_text,
                        voice_clone_prompt=m.prompt, chunk_size=chunk_size, append_silence=False):
                    if sr != self.sample_rate:
                        log(f"WARNING: model sample rate {sr} differs from the advertised {self.sample_rate}")
                        self.sample_rate = sr
                    yield shaped(np.asarray(audio, dtype=np.float32))

    def trim_memory(self) -> None:
        import torch
        before = torch.cuda.memory_reserved()
        torch.cuda.empty_cache()
        log(f"idle: GPU memory reserved {before / 1e9:.2f} -> {torch.cuda.memory_reserved() / 1e9:.2f} GB")

    # ---- line cache
    def cache_key(self, voice: Voice, mood: str, text: str) -> str:
        # Bump NORM_VERSION whenever normalize_for_speech changes, so old cached takes are not replayed with new text.
        blob = json.dumps([voice.name, voice.version, mood, " ".join(text.split()), self.args.model, "norm-2026-10-19"])
        return hashlib.sha256(blob.encode("utf-8")).hexdigest()[:40]

    def cache_path(self, key: str) -> Optional[Path]:
        return self.cache_dir / f"{key}.pcm" if self.cache_dir else None

    def cache_store(self, key: str, pcm: bytes) -> None:
        path = self.cache_path(key)
        if not path:
            return
        tmp = path.with_suffix(".tmp")
        tmp.write_bytes(pcm)
        os.replace(tmp, path)
        limit = self.args.cache_max_mb * 1024 * 1024
        files = sorted(self.cache_dir.glob("*.pcm"), key=lambda p: p.stat().st_mtime)
        total = sum(p.stat().st_size for p in files)
        while files and total > limit:
            victim = files.pop(0)
            total -= victim.stat().st_size
            victim.unlink(missing_ok=True)


def pcm16(audio) -> bytes:
    import numpy as np
    return (np.clip(audio, -1.0, 1.0) * 32767.0).astype("<i2").tobytes()


def _next_pcm(gen) -> Optional[bytes]:
    chunk = next(gen, None)
    return None if chunk is None else pcm16(chunk)


# --------------------------------------------------------------------------------------------- HTTP


def build_app(args):
    from fastapi import FastAPI, Request
    from fastapi.responses import JSONResponse, Response, StreamingResponse
    from pydantic import BaseModel

    engine = Engine(args)
    api_key = args.api_key or os.environ.get("VOICE_SERVER_KEY") or ""

    class SpeechRequest(BaseModel):
        model: Optional[str] = None
        input: str
        voice: str = "emily"
        mood: Optional[str] = None
        response_format: str = "pcm"
        speed: Optional[float] = None  # accepted for OpenAI compatibility, ignored
        chunk_size: Optional[int] = None
        cache: bool = False

    def error(status: int, message: str, **headers):
        return JSONResponse({"error": {"message": message, "type": "voice_server_error"}},
                            status_code=status, headers=headers)

    async def idle_trimmer():
        while True:
            await asyncio.sleep(2.0)
            if (engine.state == "ready" and not engine.trimmed and not engine.lock.locked()
                    and time.monotonic() - engine.last_activity > args.idle_trim_seconds):
                engine.trimmed = True
                await asyncio.get_running_loop().run_in_executor(engine.executor, engine.trim_memory)

    from contextlib import asynccontextmanager

    @asynccontextmanager
    async def lifespan(_app):
        loader = asyncio.get_running_loop().run_in_executor(engine.executor, engine.load)
        loader.add_done_callback(lambda f: f.exception())  # failure already recorded in engine.error
        trimmer = asyncio.create_task(idle_trimmer())
        yield
        trimmer.cancel()
        engine.executor.shutdown(wait=False, cancel_futures=True)

    app = FastAPI(title="Blighted Dreams voice server", lifespan=lifespan)

    def authorized(request: Request) -> bool:
        return not api_key or request.headers.get("authorization", "") == f"Bearer {api_key}"

    @app.get("/health")
    async def health():
        info: Dict[str, Any] = {
            "status": engine.state, "model": args.model, "sample_rate": engine.sample_rate,
            "voices": {v.name: list(v.moods) for v in engine.voices.values()},
            "default_chunk_size": args.chunk_size, "requests": engine.requests,
            "uptime_s": round(time.time() - engine.started, 1), "load_seconds": engine.load_seconds,
        }
        if engine.error:
            info["error"] = engine.error
        if engine.state == "ready":
            import torch
            info["gpu"] = {"allocated_gb": round(torch.cuda.memory_allocated() / 1e9, 2),
                           "reserved_gb": round(torch.cuda.memory_reserved() / 1e9, 2)}
        return info

    @app.get("/v1/voices")
    async def voices():
        return {"voices": [{"name": v.name, "language": v.language, "default_mood": v.default_mood,
                            "moods": list(v.moods)} for v in engine.voices.values()]}

    @app.post("/v1/audio/speech")
    async def speech(req: SpeechRequest, request: Request):
        if not authorized(request):
            return error(401, "invalid or missing bearer token")
        if engine.state == "loading":
            return error(503, "voice model is still loading", **{"Retry-After": "3"})
        if engine.state == "error":
            return error(500, f"voice model failed to start: {engine.error}")

        text = " ".join(req.input.split())
        if not text or not re.search(r"\w", text):
            return error(400, "input must contain some text")
        if len(text) > MAX_INPUT_CHARS:
            return error(400, f"input is longer than {MAX_INPUT_CHARS} characters")
        fmt = req.response_format.lower()
        if fmt not in ("pcm", "wav"):
            return error(400, "response_format must be 'pcm' or 'wav'")

        voice_name, _, mood_from_voice = req.voice.partition(":")
        voice = engine.voices.get(voice_name)
        if voice is None:
            return error(404, f"unknown voice {voice_name!r}; available: {list(engine.voices)}")
        mood = req.mood or mood_from_voice or voice.default_mood
        if mood not in voice.moods:
            log(f"unknown mood {mood!r} for {voice.name}, using {voice.default_mood}")
            mood = voice.default_mood
        chunk_size = max(1, min(req.chunk_size or args.chunk_size, 16))
        key = engine.cache_key(voice, mood, text)
        headers = {"X-Sample-Rate": str(engine.sample_rate), "X-Voice": voice.name, "X-Mood": mood}
        engine.requests += 1

        cached = engine.cache_path(key) if req.cache else None
        if cached is not None and cached.exists():
            pcm = cached.read_bytes()
            cached.touch()
            log(f"speech {voice.name}:{mood} chars={len(text)} CACHE HIT {len(pcm) / 2 / engine.sample_rate:.1f}s")
            if fmt == "wav":
                return Response(_wav_bytes(pcm, engine.sample_rate), media_type="audio/wav",
                                headers={**headers, "X-Cache": "hit"})

            async def replay():
                for i in range(0, len(pcm), 16384):
                    yield pcm[i:i + 16384]
            return StreamingResponse(replay(), media_type="audio/pcm", headers={**headers, "X-Cache": "hit"})

        async def pcm_chunks():
            loop = asyncio.get_running_loop()
            queued = time.perf_counter()
            async with engine.lock:
                waited = time.perf_counter() - queued
                gen = engine.synth(text, voice, mood, chunk_size)
                t0 = time.perf_counter()
                first = None
                total = 0
                done = False
                kept = [] if req.cache or fmt == "wav" else None
                try:
                    while True:
                        pcm = await loop.run_in_executor(engine.executor, _next_pcm, gen)
                        if pcm is None:
                            done = True
                            break
                        if first is None:
                            first = time.perf_counter() - t0
                        total += len(pcm)
                        if kept is not None:
                            kept.append(pcm)
                        yield pcm
                finally:
                    loop.run_in_executor(engine.executor, gen.close)  # queued before the next request's work
                    engine.last_activity = time.monotonic()
                    engine.trimmed = False
                    wall = time.perf_counter() - t0
                    secs = total / 2 / engine.sample_rate
                    log(f"speech {voice.name}:{mood} chars={len(text)} chunk={chunk_size} "
                        f"queued={waited * 1000:.0f}ms ttfa={(first or wall) * 1000:.0f}ms audio={secs:.1f}s "
                        f"wall={wall:.1f}s rtf={secs / wall if wall else 0:.2f} {'done' if done else 'CANCELLED'}")
                if done and req.cache and kept:
                    engine.cache_store(key, b"".join(kept))

        if fmt == "wav":  # one complete file, not streamed
            parts = [chunk async for chunk in pcm_chunks()]
            return Response(_wav_bytes(b"".join(parts), engine.sample_rate), media_type="audio/wav",
                            headers={**headers, "X-Cache": "miss"})
        return StreamingResponse(pcm_chunks(), media_type="audio/pcm", headers={**headers, "X-Cache": "miss"})

    return app


def _wav_bytes(pcm: bytes, sample_rate: int) -> bytes:
    buf = io.BytesIO()
    with wave.open(buf, "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(sample_rate)
        w.writeframes(pcm)
    return buf.getvalue()


def resolve_default_model() -> str:
    # Use the original zero-shot base voice cloning model (Qwen/Qwen3-TTS-12Hz-0.6B-Base)
    # The custom fine-tune was reverted as the original base cloning performs with higher natural fidelity.
    return "Qwen/Qwen3-TTS-12Hz-0.6B-Base"


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--host", default="127.0.0.1")
    ap.add_argument("--port", type=int, default=8791)
    ap.add_argument("--model", default=resolve_default_model())
    ap.add_argument("--voices-dir", default=str(HERE / "voice"))
    ap.add_argument("--cache-dir", default=str(DEFAULT_HOME / "line_cache"), help="empty string disables the line cache")
    ap.add_argument("--cache-max-mb", type=int, default=256)
    ap.add_argument("--chunk-size", type=int, default=4, help="default codec steps per streamed chunk (12 = 1 s)")
    ap.add_argument("--idle-trim-seconds", type=float, default=4.0,
                    help="release cached GPU memory after this long without a request")
    ap.add_argument("--api-key", default="")
    args = ap.parse_args()

    if hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(encoding="utf-8")
    import uvicorn
    log(f"starting on http://{args.host}:{args.port} (model {args.model})")
    uvicorn.run(build_app(args), host=args.host, port=args.port, log_level="warning", access_log=False)


if __name__ == "__main__":
    main()
