# Personal Helper (AI companion) - plan v1, 2026-10-16

Design only. Nothing is built, no pack files changed.

> 2026-10-17: the next stage (local MiniCPM5 mind, memory, a player-grade body, mining/home, human-like chat) is planned in
> `HELPER_BRAIN_PLAN.md`. Sections 6c-6i below remain the record of the voice, brain, persona and body experiments.

## 1. Goal and decisions so far

An original floating companion that follows the player, listens (push-to-talk or typed chat), answers in a voice,
knows where the player is in the Mayor's Path, and can do a few safe things in the world. Inspired by the Verity
mods, but not a copy of them.

User answers 2026-10-16: brain = full AI (not scripted), form = floating companion entity, voice = full voice in
and out. Provider idea from the user: xAI "Grok voice".

Do NOT copy or decompile Verity JE: it is All Rights Reserved, and it is an adaptation of ThatMob's character.
Our companion needs its own name, look and personality.

## 2. How Verity does it (researched, mostly from public pages)

Confidence: HIGH = the mod's own CurseForge/Modrinth page or a source repo, MED = fan guide sites, LOW = guess.

| Topic | Finding | Conf. |
|---|---|---|
| Shape | Client+server Forge/NeoForge mod. Verity is a yellow smiling sphere entity that follows you. | HIGH |
| Brain | LLM over the web. Official pages say "powered by Groq.com", each player pastes their own free Groq key into the mod config (nothing baked in). Optional Ollama (local) switch. A later pack ("Smiley's Better Brain") swaps in Gemini. | HIGH |
| Ears | Hold V (push-to-talk) and release to submit; M cycles microphones. Speech goes through Whisper STT (Groq cloud or an offline Whisper). Recognised text is posted as ordinary chat. | MED |
| Voice | Pluggable TTS: native/offline voice, Kokoro or Piper (offline), Fish Audio (cloud, "Better Voice" add-on). | MED |
| Eyes | Add-on "Better Vision": press G, screen capture goes to a multimodal Gemini model. | HIGH |
| Context | Open-source fan clone (MrPauk335, Fabric 1.21.1) feeds the prompt: coordinates, health, biome, held item, time of day, nearby ore/villages/home, entity counts and threats in a 32-block scan. | MED (clone, not the original) |
| Memory | Clone keeps the last 40 dialogue entries plus a list of "known facts" in NBT. Original reportedly has memory and a reputation system, undocumented. | MED/LOW |
| Actions | Clone: `/verity ...` commands, kill mobs, lead you to ore/village/home, teleport. Original: spawns a box and a "Demon". | MED |
| Architecture | Three swappable services: STT, LLM, TTS, each behind a config switch. Async so the server tick never waits. | HIGH |

Takeaways to keep: bring-your-own-key, three swappable services, push-to-talk, game-state in the prompt, small
NBT memory, async everywhere. Things to avoid: XOR-"hiding" API keys in the jar (the clone does), speech posted as public chat,
letting the model run arbitrary commands.

## 3. xAI Grok voice API (docs.x.ai, fetched 2026-10-16)

One xAI key covers all three services, so setup is "paste one key".

- **STT** `POST https://api.x.ai/v1/stt`, multipart, model `grok-voice-transcribe-2.0`. Accepts WAV directly.
  `keyterm` field (up to 100 terms) biases recognition toward names. Batch price $0.10/hour.
- **TTS** `POST https://api.x.ai/v1/tts`, body `{text, voice_id, language, output_format}`. Voices include `eve`
  (default), `ara`, `rex`; `GET /v1/tts/voices` lists the rest. Output `wav`/`pcm`/`mp3`. Speech tags (whispers, laughter, pauses).
  `speed` 0.7-1.5. Streaming over `wss://api.x.ai/v1/tts` (text deltas in, audio chunks out, `text.clear` cancels = barge-in).
  Custom voice: `POST /v1/custom-voices` from a clip of up to 120 s, then pass its `voice_id`. That would give the helper
  a voice nobody else has.
- **Realtime voice agent** `wss://api.x.ai/v1/realtime?model=grok-voice-latest` (OpenAI-Realtime-compatible): one
  socket does speech in, speech out, server-side turn detection, and function tools
  (`response.function_call_arguments.done` -> send `function_call_output` -> `response.create`).
  PCM16 24 kHz. Third-party sources say about $0.05/min.
- **Chat models** (prices per million tokens): grok-4.3 $1.25 in / $2.50 out (cached in $0.20), grok-4.7 $2.00 / $6.00.
  The docs page I fetched did not state the chat endpoint URL or confirm tool calling for chat models. Needs a test call (section 8).
- xAI says audio is processed in real time and not stored or used for training.
- TTS price ($15 per million characters) comes from third-party articles, not xAI's own page. Confirm in the console.

Groq is a different company (inference host, Whisper STT). It stays as an optional provider.

## 4. Recommended architecture

Pipeline, not realtime, for v1. A provider interface keeps the Verity-style "swap any service" property, and the same
brain serves typed chat, Grok and a local Ollama model (your machine has an RTX 5070, 32 GB RAM and Ollama installed).

```
V held -> MicCapture (javax.sound, 16 kHz mono PCM16) -> WAV bytes
       -> SttProvider (Grok /v1/stt, keyterms = pack vocabulary)           [async, HTTP]
       -> Brain: system prompt (persona + pack knowledge + live state) + history + tools
                 provider: Grok chat | Groq | Ollama                       [async, HTTP, tool calling]
       -> tool calls  -> client sends a request packet -> SERVER validates and runs it
       -> reply text  -> TtsProvider (Grok /v1/tts wav)                    [async]
       -> AudioPlayer (javax.sound first, OpenAL positional at the entity later) + subtitles
```

Why not Grok realtime for v1: continuous streaming and a second code path for typed chat and Ollama. It is the nicest
conversation (barge-in, sub-second turns), so it is **Phase 4: optional "live mode"** opened only while talking.

Where the code lives: new package `com.blighteddreams.enhancements.helper` inside the existing `blighted_enhancements` mod.

| Package | Job |
|---|---|
| `helper.entity` | The companion entity (GeckoLib 4.9.3 is already in the pack), follow-owner goal, face/mood synced via EntityData, one per player, spawned/recalled by an item or command |
| `helper.client.voice` | `MicCapture` (V push-to-talk, M mic cycle), `AudioPlayer`, device errors shown in chat |
| `helper.ai` | `SttProvider`, `BrainProvider`, `TtsProvider` interfaces; `GrokProvider`, `OllamaProvider`, later `GroqProvider` |
| `helper.context` | Builds the live-state block: position, biome, health, held item, time, nearby threats, colony level, Colony Keys, current quests |
| `helper.tools` | Tool registry. Every tool is validated on the SERVER |
| `helper.memory` | Last N turns plus "known facts" in the player's NBT |
| `helper.knowledge` | Pack knowledge text loaded from a resource file (generated from the design docs and quest book) |
| `helper.config` | Client-only config: provider, key, voice, PTT/mic keys, volume, subtitles, mic mode |

Networking: client does STT/LLM/TTS itself with its own key (the key never leaves the client). Only compact
`HelperAction` request packets go to the server. In multiplayer each player's helper is private by default.

## 5. What makes it ours (pack-aware)

Verity answers generic Minecraft questions. This one knows Blighted Dreams:

- **State it can read:** FTB Quests progress, MineColonies colony level and finished huts, the Colony Keys stages
  (`key_miner`, `key_sawmill`, ...), the Mayor's Path step, difficulty tier, current temperature trouble.
- **Knowledge file:** distilled from `PROGRESSION_PATH.md`, `MAYORS_PATH_PLAN.md`, the quest book text and gating
  scripts, so it can answer "why can't I place a reforging table" with the real reason (needs the Miner key).
  Static prompt text is cheap with xAI's cached-input rate.
- **Voice keyterms:** pass pack words (MineColonies, Builder, Draugr, Dwemer, Apotheosis, Tom's, ...) to STT so it hears them.

Tools (server-validated, all with cooldowns and a per-day cap in config):

| Tool | Does | Risk |
|---|---|---|
| `get_status` | Returns the state block above | none |
| `explain_gate` | Looks up why something is locked and what unlocks it | none |
| `locate` | Nearest structure/biome/colony building, writes a compass or waypoint | low |
| `remind_quest` | Points to the next quest in the book | none |
| `give_item` | Only from a config allowlist (food, torches, ...). **Default list excludes anything the Keys gate**, so it cannot bypass progression | medium |
| `set_mood` | Face/animation | none |

Not offered: arbitrary commands, killing mobs, teleporting, demolishing. Model output is untrusted input.

## 6. Security, privacy, cost

- No key in the pack. Each player pastes their own into a CLIENT-only config. Never logged, never sent to the server.
- The mic opens only while V is held, with a visible indicator. Audio goes to xAI only then.
- Speech is NOT posted as public chat (unlike Verity). Replies appear as private subtitles.
- Server clamps tool arguments, rate-limits per player, and ignores unknown tools.
- Rough cost per exchange (estimate, grok-4.3 pricing): STT well under 1 cent, 200 characters of TTS about 0.3 cent,
  LLM turn with a cached 8k-token prompt about 0.5-1 cent. Order of $1 per ~100 exchanges. Verify with real usage.
- Latency estimate: 2-4 s from releasing V to first audio. Sentence-by-sentence TTS cuts this; the streaming TTS socket helps more.

## 6b. Update 2026-10-16: she is an anime girl who walks and fights (replaces the floating orb)

User wants a humanoid anime girl companion that moves around the world and fights monsters.

- **Entity:** a ground-walking owner-bound `PathfinderMob`, not a floating sphere. Goals: follow owner, teleport
  to owner when too far, melee and optional bow/spell attack on what threatens the owner or herself, flee at low HP,
  sit/stay, hold position. Uses a real held weapon, so gear = progression.
- **Never dies:** at 0 HP she goes "downed" (kneels, can't fight, takes no damage) and stands up on her own after a
  config timer (default ~10 s, longer if monsters are still next to her; she stands at partial HP). No revive
  action needed (user decision 2026-10-16). Damage that would kill her is always converted into the downed state,
  including void and /kill-style damage where possible.
- **Model decision 2026-10-16:** start with the humanoid model and quality skin. Next step: study the "Steve
  Avatar" mod for the best-looking model approach, then decide whether to upgrade to a custom model.
- **Brain controls fight style, not fight ticks:** the LLM only sets high-level modes through tools
  (`set_stance`: follow/guard/stay/aggressive, `set_target_priority`). Combat itself is plain vanilla AI goals,
  so it never waits on the network and works with no key.
- **Balance:** damage and HP scale with a config curve tied to the Key stages and the 1-5 star monsters
  (see `BOSS_BALANCE.md`), capped so she supports the player instead of replacing them. Does not trigger
  MineColonies guard behaviour and is ignored by raid targeting unless configured.
- **Yes Steve Model (YSM) research 2026-10-16:** user's reference for "best models". Facts: Forge/Fabric/NeoForge
  incl. 1.21.1, built on GeckoLib, models are Bedrock-format geometry plus animation files, models sync server to
  client, per-model permissions, Alt+Y switcher, model folder + reload command. Licence **All Rights Reserved**,
  model files are encrypted, a C++ core (Windows/Linux only). Not in this pack. No public API found, and nothing says
  it can skin non-player entities. So: **we cannot reuse YSM's code or other people's YSM models in our mod.**
  What we can do: use the same technique (GeckoLib + Bedrock-style geometry and animation JSON, which GeckoLib 4.9.3
  already loads) with a model we author or have a licence for, and use YSM models only as visual reference.
  Open question for the user: install YSM just so the *player* can wear anime models too (separate from the helper).
- **Look, two options:**
  - A (recommended first): vanilla humanoid model with a custom 64x64 skin, anime styled, plus large anime
    portrait art (Qwen-Image, same pipeline as the quest book) in the chat/subtitle UI. Cheap, animations come free.
  - B (later): GeckoLib model (4.9.3 already in the pack) with anime proportions, made in Blockbench, with
    custom walk/attack/emote animations.
- Expressions and mood (face swaps, emotes) become skin variants in A, bones/animations in B.
- Also worth checking: `ModernCompanions-4.60.jar` is already in the pack. Compare its follower AI and
  inventory before writing our own, to avoid duplicating a feature.

## 6c. Update 2026-10-16: voice = local Qwen3-TTS (free, real time, light)

User decision: free voice, as fast and light as possible. Grok/Groq/Fish stay as optional providers behind
`TtsProvider`; the default TTS provider becomes a local Qwen3-TTS server. Nothing is installed yet.

Facts (QwenLM/Qwen3-TTS README, andimarafioti/faster-qwen3-tts README, Serveurperso/Qwen3-TTS-GGUF, HF model cards):

| Topic | Finding |
|---|---|
| Models | Apache-2.0. 0.6B Base (voice clone, 3 s reference), 1.7B Base, 1.7B CustomVoice (preset speakers), 1.7B VoiceDesign (voice from a text description, **1.7B only**). 24 kHz mono, 10 languages incl. Japanese and English. |
| Download sizes (HF) | 0.6B-Base 2.52 GB (incl. 0.68 GB speech tokenizer), 1.7B-VoiceDesign 4.52 GB. |
| Fast runtime | `faster-qwen3-tts` (MIT): CUDA graphs, streaming, example OpenAI-style `POST /v1/audio/speech` server, native Windows, Blackwell needs the cu128 PyTorch wheels (2.11.0+cu128 wheel is 2.75 GB). |
| Published 0.6B numbers (RTF > 1 = faster than real time; TTFA = time to first audio chunk) | RTX 4090: RTF 4.78, TTFA 156 ms. RTX 4060 on Windows: RTF 2.26, TTFA 413 ms. 1.7B: 4090 4.22 / 174 ms, 4060 1.83 / 460 ms. **No RTX 5070 figure exists; it must be measured.** |
| Light path | `qwentts.cpp` GGUF (MIT): 0.6B Q8_0 = 993 MB, Q4_K_M = 629 MB, plus a 255-291 MB tokenizer; CUDA/Vulkan/CPU. No documented Windows build and no documented streaming API, and this machine has no cmake/nvcc/cl. `faster-qwen3-tts` also has an experimental GGML backend. Evaluate only after the PyTorch baseline. |

Design, "design once, clone at runtime":

1. Offline, once: the 1.7B VoiceDesign model writes reference clips for the companion in a few moods (calm, cheerful,
   worried, battle shout, whisper) from text descriptions. We pick the best takes by ear. No real person is cloned.
2. Each clip goes through `create_voice_clone_prompt` once and the result is cached (the x-vector part is ~4 KB).
3. Runtime loads only the 0.6B Base model and streams with the cached prompt for the current mood (`set_mood` picks it).
   The 1.7B model never runs in game.

Unverified: that a 0.6B clone of a designed voice stays faithful to it, and the `instruct` field is experimental with
x-vector-only mode. Listening test decides; fall back to the 1.7B CustomVoice or VoiceDesign model if it drifts.

Latency design:

- Split the LLM stream into sentences and synthesize each as it arrives (LLM time-to-first-token will probably dominate).
- First chunk small, later chunks larger. The codec runs at 12 steps/s (~83 ms of audio per step): `chunk_size` 2 is
  ~167 ms, 4 is ~333 ms, 8 is ~667 ms. Smaller chunks cut TTFA but cost throughput. Crossfade the joins. The library
  takes ONE `chunk_size` per call, so a small-first-then-larger scheme needs our own loop or one of the forks.
- Play through a queue-backed player (a per-chunk `play()` call gaps, per the library docs).
- Pre-render the stock lines (greetings, raid and hut warnings, quest hints) per mood at build time with the best
  settings and ship them as audio files: zero latency and zero GPU for those. Cache dynamic lines on disk by hash of
  (text, mood).
- Warm up once at startup (`model.warmup`) so CUDA graph capture does not hit the first sentence.

Footprint: see "Measured" below. Still unmeasured: **Minecraft frame time while a line is being synthesized**, because
the 12 GB card is shared with the game, shaders and possibly an Ollama model.

Integration: a separate local process (`design/tools/voice-server/`) exposes `/v1/audio/speech` and streams 24 kHz mono
PCM. The mod's `LocalTtsProvider` calls `127.0.0.1`, plays it with javax.sound (OpenAL positional later), can start the
process if a path is configured, and falls back to a cloud provider or text-only if it is absent. Python + CUDA is a
heavy ask for other players, so treat local voice as an optional power-user install; a prebuilt qwentts.cpp binary is the
possible shippable form later.

Machine: RTX 5070 12 GB (compute 12.0), driver 610.88, 32 GB RAM, C: 72.9 GB free. No system Python, but `uv` 0.12
with CPython 3.11 and 3.14 is installed. `git` yes; `cmake`, `nvcc`, `cl` no.

Setup DONE 2026-10-16 (user approved the ~10.5 GB download): `C:\Users\srafa\tts` holds `venv` (Python 3.11, torch
2.11.0+cu128, faster-qwen3-tts 0.5.4), `hf_cache` (both models, ~6 GB on disk), `scripts/`, `out/`. **Pin
`transformers==5.15.1`**: pip picks 5.20.1, which crashes loading with `MimiConfig has no attribute rope_theta` (the
library README warns about this). Scripts: `design_voices.py` (1.7B VoiceDesign, 5 moods x 3 takes, from
`moods.json`), `bench.py` (0.6B Base runtime), `similarity.py`. Run them with `venv\Scripts\python.exe -I`.

Measured on the RTX 5070 (3 repeats per cell; RTF = audio seconds / wall seconds, > 1 is faster than real time; TTFA =
call start to first audio chunk; 7 test lines of 1-10 s; ICL clone prompts built from the designed clips):

| Streaming mode | TTFA (median) | RTF (median) |
|---|---|---|
| ICL, chunk_size 2 | 187 ms | 1.79 |
| ICL, chunk_size 4 | 233 ms | 2.21 |
| ICL, chunk_size 8 | 324 ms | 2.52 |
| ICL, chunk_size 12 | 416 ms | 2.73 |
| x-vector only, chunk_size 4 | 204 ms | 2.54 |
| Non-streaming (ICL) | n/a (whole line) | 3.09-3.23 |

- 1.7B VoiceDesign (offline, non-streaming): RTF 2.6-3.0, about 4-5 s per 11 s clip, 4.18 GB after load, 4.90 GB
  peak allocated.
- 0.6B Base at runtime: 2.17 GB allocated after load, 2.48 GB with warmup and 5 prompts, 3.17 GB peak allocated,
  but **5.27 GB peak reserved** by the allocator (transient decode buffers). Likely trimmable with
  `torch.cuda.empty_cache()` or `PYTORCH_CUDA_ALLOC_CONF=expandable_segments:True`; not tried yet. nvidia-smi on this
  WDDM driver reports no per-process figure, so use the system total (idle desktop was 1.4 GB used).
- Startup costs: model load 6.7 s, CUDA graph capture 1.6 s, the FIRST ICL prompt build 14.8 s (one-time encoder
  init), later prompts 0.1 s. So the server must warm up and build its prompts at start, not on the first line.
- Speaker-embedding cosine similarity between all 15 designed clips is 0.97-0.995, so it does not separate takes;
  choosing the final takes is by ear (`out/design/picks.json` overrides the default take 1 per mood).
- Not yet judged: audio quality. 10 samples are in `design/voice_samples/` for the user to listen to.

Conclusion so far: speed and footprint are fine for real time on this card. Chunk 4 (233 ms, RTF 2.2) looks like the
sweet spot; chunk 2 (187 ms) leaves only 1.5-2x headroom. The open risks are audio quality, the transient VRAM peak,
and game frame time.

### 6c-2. BUILT 2026-10-16: local voice server + Java LocalTtsProvider

The user listened to the samples (take 1 of each mood) and approved the voice. Those exact clips are the voice pack.

**Server** `design/tools/voice-server/` (Python, FastAPI, runs in the `C:\Users\srafa\tts` venv):

- `server.py`, `start_voice_server.ps1` (port 8791, binds 127.0.0.1), `make_voice_pack.py` (rebuilds `voice/emily/`
  from designed takes), `voice/emily/` (5 mood WAVs + `voice.json` with the reference texts and the design recipe).
- `POST /v1/audio/speech`, OpenAI style. Body: `input`, `voice` (`"emily"` or `"emily:worried"`), `mood`,
  `response_format` (`pcm` = streamed raw 16-bit LE mono, `wav` = one complete file), `chunk_size`, `cache`. Header
  `X-Sample-Rate` (24000). `GET /health` (status `loading|ready|error`, voices, GPU memory), `GET /v1/voices`.
  Errors are JSON: 400 empty text / bad format / over 1500 chars, 401 bad key, 404 unknown voice, 503 + `Retry-After`
  while loading. Unknown mood falls back to the voice's default. Optional bearer key via `VOICE_SERVER_KEY`.
- Startup: port opens at once, then model load, CUDA graph capture, one cached clone prompt per mood, one throwaway line
  per mood. READY in 15 s with 2.48 GB allocated (the 14.8 s "first prompt" cost seen in the benchmark was a one-time
  first-run cost, not repeated).
- Text is split into sentences server side (merge < 24 chars, break > 260), synthesized one after another with a 100 ms
  pause, so a whole reply can be sent as ONE request.
- One line at a time (one GPU thread, one asyncio lock); other requests wait their turn. **Closing the connection
  cancels the synthesis** (log shows `CANCELLED`) and the next request works at once.
- Optional disk cache of finished lines (`cache: true`, 256 MB cap): a repeat replays in ~6 ms with no GPU.
- Idle trim: after 4 s without a request the allocator's cached GPU memory is released (reserved 4.0-4.2 -> 2.59 GB),
  which removes the transient 5.3 GB peak seen in the benchmark.

**Java** (all in `blighted_enhancements`, jar still version 1.4.0, built with gradle, no errors):

- `helper/ai/TtsProvider` (interface: `speak(Request, Sink) -> Handle`, `checkHealth`, `isAvailable`), JDK only.
- `helper/ai/LocalTtsProvider`: `java.net.http`, HTTP/1.1, streams the PCM, delivers whole samples only, cancel closes
  the connection, quick failure and "unavailable" state when the server is down, JSON escaping for odd text.
- `helper/client/voice/PcmSession`: one javax.sound line per spoken line, 120 ms prebuffer, 300 ms line buffer,
  software volume, `stop()` cuts off in ~2 ms. JDK only.
- `helper/client/voice/HelperVoice`: the call the brain will use, `HelperVoice.speak(text, mood)`; a new line replaces
  the old; volume = config x Minecraft Master x Voice slider; tells the player once per 10 s if the voice is down;
  logs `Speaking`, `First audio N ms`, `Finished`.
- `helper/client/voice/VoiceCommands`: client commands `/helpervoice say <text>`, `mood <mood> <text>`, `stop`, `status`;
  stops speech on logout.
- `helper/VoiceConfig`: client config `config/blighted_enhancements-voice.toml` (enabled, serverUrl, apiKey, voice,
  volume, chunkSize, cacheLines).

**Tests, all passed:**

- `design/tools/voice-server/run_smoke_test.ps1` compiles the JDK-only classes and runs `LocalTtsSmoke` against the
  running server: health, format, whole samples, first PCM 226 ms after `speak()`, awkward-text JSON escaping, 404
  error, cancel then speak again, device playback, `stop()`, dead server (error in 2 ms, marked unavailable). 16/16.
  Use `-NoAudio` to skip the two quiet audible lines.
- In the scratch game (`C:\Users\srafa\bd`, TEST_helper): the config file was generated, `/helpervoice status` answered
  "ready", `/helpervoice mood worried ...` spoke. Game log: first audio 283-295 ms after the request (the server alone
  does ~220 ms; the game's rendering on the same GPU adds about 60 ms), RTF 1.99 with the game running.
- Total GPU memory with the scratch game running: 4.65 GB before speech, 6.02 GB during it, then trimmed after 4 s idle.
  GPU utilisation hit ~94% while synthesizing a line.
- Frame rate in the scratch game (light scene, no shaders, uncapped): idle windows 330 and 367 FPS average, speech
  windows 334 and 306 FPS average, worst one-second readings 232 and 198 FPS (idle worst 261 and 290). In a light scene
  that is at most ~2 ms of extra frame time.

**Not done / still unknown:**

- Frame time with a **shader pack at a GPU-bound frame rate** (the real worst case) is NOT measured. If it hitches:
  pace generation to ~1.3x real time (halves the burst), pre-render more lines, or use chunk_size 6-8.
- Nothing is installed in the real instance (`mods/` still has 1.3.0). The 1.4.0 jar with the voice classes is only in
  the scratch game.
- The server must be started by hand (`start_voice_server.ps1`); the mod does not launch it. Positional (OpenAL) audio,
  subtitles, and the brain's sentence-by-sentence streaming from the LLM are not built.
- Stock lines are not pre-rendered yet (needs the line list), and nobody has judged long-session audio quality.
- Push-to-talk and speech-to-text are not started.

## 6d. BUILT 2026-10-16: the brain (LLM provider, game-state prompt, sentence-by-sentence speech)

User decision: free providers (Groq, Gemini) or local Ollama; the brain asks, the voice speaks sentence by sentence.
Grok is not needed. Everything below is in `blighted_enhancements` 1.4.0 and builds clean.

**Design decisions**

- One provider class for all of them: `OpenAiCompatBrain` speaks the OpenAI streaming chat format, which Groq, Google's
  Gemini compatibility endpoint and Ollama all accept. Presets (`BrainConfig.preset`) fill in the address, default model,
  fallback model and key variable name; the config file overrides them.
- The brain runs on the CLIENT (the key never reaches a server). The few facts only the server has (the colony and its
  Keys) come over a small packet pair, `HelperPayloads.StateRequest/StateReply`.
- Mood without tool calling: the model starts each sentence group with a tag, `[calm] [cheerful] [worried] [battle]
  [whisper]` (ten since 6h: plus `[smug] [tease] [flirt] [mock] [sincere]`, list in `HelperMoods`). `ReplyStreamer` removes the tags and hands the mood to the voice. Works on any model, even small ones. Real
  tool calling (give item, set stance, locate) is deliberately NOT built yet.
- Cost control: free tiers limit tokens per minute, so the prompt is small: persona + knowledge about 870 tokens, state
  about 175, then history. Measured prompt for one question in the game: about 1,050 tokens; with six remembered turns
  expect about 1.5k.
- Rate limits: if the main model answers 429/5xx before saying anything, the request is retried once on `fallbackModel`
  (Groq default: `llama-3.1-8b-instant`).

**Pieces** (JDK-only ones can be tested without Minecraft; `design/tools/brain-test/`)

| Class | Job |
|---|---|
| `helper/ai/OpenAiCompatBrain` (+ `BrainProvider`, `ChatMessage`, `MiniJson`) | Streams a chat over SSE. Reads only `delta.content`, so reasoning output is never spoken. Retry on fallback model on 429/5xx, retry without `reasoning_effort` if a model rejects it, readable errors for 401/404/429/unreachable, cancel drops the connection. Key only in the Authorization header. |
| `helper/ai/ReplyStreamer` | Token stream to sentences with moods. Handles tags split across chunks, `<think>` blocks, `*stage directions*`, markdown, emoji, a leading "Emily:", decimals and ellipses, line breaks, cutting a long first sentence at a comma for a faster start, joining fragments under 8 characters. |
| `helper/ai/HelperPrompt` | Persona (Emily; first written as a polite yandere, since 2026-10-17 sly and bratty, see 6h; speak aloud in 1-3 short sentences, mood tag first, never invent facts, cannot act in the world yet) and the user turn `[State] ... [The player says] ...`. |
| `helper/ai/Conversation` | Last N turns (default 6) as the player's words plus her answer with its tags; state is NOT kept in history; a new question cancels the old answer. |
| `helper/client/voice/SpeechQueue` | Each sentence goes to the voice server the moment it is ready (prefetch), played in order on ONE audio line that stays open between sentences; text-only fallback if no audio; `clear()` stops at once. |
| `helper/client/brain/HelperBrain` | The facade: `HelperBrain.ask(text)`, `forget()`, `stop()`, `status()`. Resolves provider settings, builds the pieces, shows each sentence in chat as she says it. |
| `helper/client/brain/HelperContext` | Client facts: player health/food/armor/level, dimension, biome, position, outdoors, light, time of day, weather, held item, worn armor with durability, hostile mobs within 16 blocks, and HER own state (stance, health, kneeling, distance). |
| `helper/HelperServerState` + `ColonyKeys.snapshot` | Server facts: colony name, citizens, Keys earned, and for each missing Key what it still needs ("Storage Network needs a sawmill of level 1 (best now 0)"). |
| `network/HelperPayloads`, `client/brain/ServerStateCache` | The request/reply, 500 ms timeout then the last known answer. |
| `helper/BrainConfig` | `config/blighted_enhancements-brain.toml` (client): `provider` groq / gemini / ollama / custom, `model`, `fallbackModel`, `baseUrl`, `apiKey`, `apiKeyEnv`, `reasoningEffort`, `temperature`, `maxTokens`, `historyTurns`, `speak`, `subtitles`. |
| `resources/.../helper/knowledge.txt` | Pack knowledge (Keys, University, population caps, Create gates, temperature), distilled from `PROGRESSION_PATH.md`. A player file `config/blighted_enhancements-helper-knowledge.txt` replaces it. |

Commands (client): `/helpervoice ask <question>`, `forget`, `brain` (shows provider, model, key source WITHOUT the key,
remembered turns, voice status), `stop`, plus the earlier `say`, `mood`, `status`.

**Using it:** make a free key at console.groq.com or aistudio.google.com, set it as a user environment variable
(`GROQ_API_KEY` or `GEMINI_API_KEY`) and restart the game, or paste it into `apiKey` in the brain config. Default
provider is Groq (`llama-3.3-70b-versatile`, fallback `llama-3.1-8b-instant`). For Gemini the default model name is the
one in Google's own OpenAI-compatibility example (`gemini-3.8-flash`); check AI Studio's model list. For Ollama set
`provider = "ollama"` and `model` to a SMALL model (the only model installed on this PC, a 27B Q2, takes 25 s to load and
fills the whole GPU, so it is not usable beside the game). Start the voice server with
`design/tools/voice-server/start_voice_server.ps1` for speech; without it she answers in text.

**Facts about the providers (checked 2026-10-16):** Groq production chat models are llama-3.1-8b-instant,
llama-3.3-70b-versatile, openai/gpt-oss-20b and gpt-oss-120b. Its docs only show Developer-plan limits (e.g. gpt-oss-20b
30 requests/min, 8K tokens/min), so the real FREE limits are unverified: read them in the Groq console. Gemini's
OpenAI-compatible base is `https://generativelanguage.googleapis.com/v1beta/openai/`; free-tier limits are only visible
in AI Studio. Ollama: `http://localhost:11434/v1/`, key ignored, `reasoning_effort: none` turns thinking off.

**Tests, all passed** (`design/tools/brain-test/run_brain_test.ps1`, 50+ checks):

- JSON reader, ReplyStreamer (tags/think/markdown/emoji/ellipsis/first-sentence cut at several chunk sizes), the streaming
  client against a mock server (request shape, key only in the header, reasoning ignored, 429 then fallback, 401 not
  retried, 400-reasoning retry, non-streaming answer, cancel drops the connection, unreachable server in ~2 ms),
  Conversation (history trimming, state not kept, failure not remembered, new question cancels the old one), SpeechQueue
  text-only and voice-down fallbacks.
- Full chain with the real voice server (silent): mock LLM streaming slowly, 3 sentences, first audio 464 ms after the
  question (the first sentence was ready after 133 ms; the voice adds ~330 ms), no audible gap between sentences (the
  audio line never ran dry), 3 prefetched requests, `clear()` silences in 2 ms.
- One real question to the local Ollama model with the real prompt: correct answer from the state, short sentences,
  no markup; 25.6 s to the first sentence because the 27B model had to load.
- In the scratch game (TEST_keys colony world): the config was generated, `/helpervoice brain` and the voice status
  answered; `/helpervoice ask` spoke a 4-sentence answer with chat subtitles in step with the audio; the prompt the game
  sent contains the real state and the real colony Keys; a follow-up carried the history; a new question cancelled the
  three prefetched sentences of the old one at the server; `/helpervoice stop` did the same; the LLM down, no API key
  (message says how to set it) and voice-server-down (text only plus one note) cases all give readable messages; the config
  hot-reloads.

**Not done / still unknown**

- Not tested against real Groq or Gemini (no key on this PC, and I never ask for one in chat): the code path is the same
  one the mock and Ollama tests use, but the first real call will confirm the model names and free-tier behaviour.
- No tool calling and no actions in the world; no quest progress in the state (FTB Quests data is readable on the client,
  not wired); no persistence of memory across sessions; no typed chat screen or push-to-talk yet (the only entry is
  `/helpervoice ask`); English only; subtitles are chat lines, not a HUD overlay.
- Not installed in the real instance (`mods/` still has 1.3.0). The scratch game has the 1.4.0 jar.
- The shader-pack frame-time test from 6c-2 is still open.

## 6e. Direction check 2026-10-16 (late): a better voice, and a different body for Emily

The user wants (1) a better voice: the cheerful-but-eerie "helpful assistant" performance style of the Verity series, but
female and more yandere, for Emily; (2) a different kind of body, because the cube model does not fit: maybe a 2D
avatar or a 2.5D character. Path not chosen yet.

**Voice, interpretation:** the target is the *performance style*, not anyone's voice. The voices are designed from text
descriptions only; no recording of the series' actor is used as a reference or as a source for conversion.

**Voice lab (done, awaiting the user's pick):** `C:\Users\srafa\tts\scripts\lab.json` + `voice_lab.py` made 5 candidate
voices x 7 moods (calm/sweet, cheerful, worried, battle, whisper, plus new yandere moods `possessive` and `cold`), same
lines for all, with one ~50 s reel per voice in `design/voice_samples/lab/`: A_bright (bubbly, eerie sweetness, closest
to the cheerful-helper style), B_honey (soft, breathy, too calm), C_giddy (excitable, slips into cold), D_velvet (mature,
low, seductive, matches the tall noble look in EMILY_LOCK.md), E_current (the earlier approved voice, as a baseline).
Generation cost: ~3 s per clip. Once one is picked: `make_voice_pack.py` equivalent for the lab clips, then make the
mood list data-driven (today `calm/cheerful/worried/battle/whisper` is hard-coded in `HelperBrain`, `VoiceCommands` and
the persona prompt; the server already reports a voice's moods at `GET /v1/voices`) so `possessive` and `cold` can be used.

**Body options, researched:**

| Path | Look | Effort | Risk | Notes |
|---|---|---|---|---|
| A. 2D HUD portrait (visual-novel style) | Highest: the painted art itself | Small | Low | Expression sprites per mood, mouth driven by the audio level, idle breathing, blink. Starts as "PNGTuber" (one image that bounces with her voice) and gains expression art over time. |
| B. 2.5D standee in the world | High from the front, flat from the side | Small to medium | Medium | Camera-facing painted figure; sway, walk bob, attack lunge, kneel when downed. Never has real limb animation. The entity logic (follow, fight, downed) stays as it is; only the renderer changes. |
| C. Both (recommended) | | | | Portrait is her face when she speaks, standee is her presence. Same art for both. |
| D. Layered puppet (Live2D-like): blink, breath, hair, wing and tail sway, lip-sync | Highest motion quality | Medium to high | Medium | Needs the art split into layers. See-through (SIGGRAPH 2026, Apache-2.0 code, up to 23 inpainted PSD layers from one anime image, ~10 GB VRAM with offload on a 12 GB card, weights licence unchecked) does the splitting; its companion tools StretchyStudio and Anime2.5DRig rig a PSD in the browser (export formats unknown). We would write our own small layer renderer in Java. Must spike first: wings, tail and long hair are the hard cases. |
| E. Real Live2D | Highest | Rig costs money or weeks | Licence | YuanLive2D exists (MIT, bundles Live2D's proprietary runtime) but only for Forge 1.20.1, not NeoForge 1.21.1. Inochi2D (open source) is D with a C interface and needs OpenGL 3.1; its licence is unchecked. |
| F. More 3D (the earlier plan) | Medium so far (user scored v1 about 2/10, mesh about 3/10) | High | High | Not recommended. |

Mockups of A, B and C on a real screenshot with the real art: `design/helper_art/mockups/` (`make_mockups.py`).

**Recommendation:** build C in small steps, A first. (1) PNGTuber-style HUD portrait driven by the voice level and mood,
appearing while she speaks; (2) standee renderer replacing the cube model, with the existing entity logic; (3) expression
art for the moods (inpaint the face of the locked art; the face-fix scripts in `helper_art/` already do similar work);
(4) only then spike See-through on her art to see whether a layered puppet is worth it.

**Open decisions for the user:** which voice (A to E, or a mix); which body path (A+B first, or spike D first).

## 6f. BUILT 2026-10-16 (late): the layered standee prototype

The user liked the layer-stack idea but was unsure how well it would do, and asked for the cheap part as a prototype.
`style = standee` is now the default way Emily is drawn; `style = model` still draws the old cube model for comparison.

**What it is:** `helper/client/HelperAvatarRenderer` (replaces the registered renderer, wraps the old `HelperGeoRenderer`),
`helper/client/StandeeLayers` (reads `assets/blighted_enhancements/helper/standee/layers.json`; a resource pack can override
it, F3+T reloads), `helper/AvatarConfig` (`config/blighted_enhancements-avatar.toml`, client), `/helperavatar` commands in
`HelperClient` (`style`, `depth`, `turn`, `maxview`, `debug`, `status`; values are written back to the config).

- Layers are same-size, same-framing RGBA images, each a quad at its own depth (`z`, in blocks), drawn far to near with
  `RenderType.entityTranslucent`. Current layers: `wing_l`, `wing_r` (z -0.10, each flaps a few degrees about its shoulder
  pivot) and `body` (z 0, breathes, its bottom 12% fades into mist). Art is `design/helper_art/standee/` (cut by
  `make_layers.py` from the locked art with hand polygons; each wing is extended 75 px under the body so sliding never
  opens a hole; `--install` copies the PNGs and layers.json into the mod resources and the scratch `emily_dev` pack).
- She turns only part of the way toward the camera (`turnTowardPlayer` 0.65, view angle capped at `maxViewAngle` 30), so
  walking around her leaves a sideways view and the layers slide against each other.
- Hover 0.12 and a slow bob, roll and pitch from her movement, forward lunge when attacking, downed = lower, dim,
  tilted. Vanilla blob shadow stays on the ground. Hurt flash comes free through the entity overlay. Cherry-leaf petals
  drift off her (more while moving or downed). Fast movement or a teleport leaves violet afterimages.
- Texture sizes: 1089x1536 per layer, bilinear. Default `figureHeight` 2.5 (2.15 looked small next to the old model).

**Seen in the scratch game (screenshots in `design/helper_shots/standee/`):** the standee renders correctly with the real
art, shadow and mist hem; from 30 degrees to either side the wings visibly shift against the body (clear at depth x3,
subtle but present at x1); the downed pose and the petal burst work; the wing flap shows in motion; before/after against
the cube model is dramatic; commands and config hot-reload work. The old model and the standee gave the same frame rate in
an A/B (both were slow only because another game was using the GPU at the time).

**Not verified:** the afterimages (speed and teleport ghosts) were never caught on a screenshot, so treat them as
untested; the attack lunge; the standee beside real terrain walls (clipping); behaviour under a shader pack; a dedicated
server (client-only code, should be safe, not tried).

**Known flaws of this crude cut:** the layer split is hand-made. Beyond about 35 degrees dark filler blocks and straight
cut edges show near the wing roots (that is why the view angle is capped); the tail, hair and arms are not separate
layers; there is no head layer, so no look-at-you or blinking yet; the figure is paper-thin from the side. A proper
decomposition (See-through, section 6e) would add ~20 inpainted layers and remove most of this, but has NOT been tried.

**Next steps, in order of value:** (1) decide how it feels in the real game; (2) lip-sync and mood: a head/face layer with
mouth and expression swaps driven by `SpeechQueue` (needs better layers, or See-through); (3) a See-through spike to learn
whether wings, hair and tail split cleanly; (4) occlusion/clipping and shader-pack checks; (5) the HUD portrait.

## 6h. Emily's locked voice (2026-10-17): sly, a bit bratty, mature, bright, slightly younger

The user chose the voice by ear over many rounds (reels and scripts in `design/voice_samples/lab2` to `lab10`, tools in `C:\Users\srafa\tts\scripts`).
It is the DEFAULT: the pack is `design/tools/voice-server/voice/emily/` and `VoiceConfig` already defaults to voice `emily`, mood `calm`.

**How it was made:** VoiceDesign 1.7B, base text "A deep, low-pitched voice, mature woman, smoky and velvety." + a smug/flirty style, seed 906
(`flirt_s1`); user picked it ("0:26"); brightened with EQ; made slightly younger by shifting the reference up 2 semitones by plain RESAMPLING;
the pack clones that ONE clip (`locked.wav`, 8.4 s, ref text = its own line) for every mood. Old per-mood pack is in `design/backup/voice-emily-v1-2026-10-17`.

**Lessons (do not repeat these):**
- VoiceDesign text barely steers pitch: the same prompt varies 150-240 Hz by seed. Generate many takes and pick by measurement and by ear.
- Separately designed clips per mood sound like different people even at 0.98 x-vector similarity. Ears decide; clone one reference for all moods.
- The clone model keeps only part of the EQ and pitch shift baked into a reference (shift +1 st -> no change, +2 st -> +15 Hz, +3 st -> +35 Hz), so tone is applied
  to the OUTPUT: new streaming `PostFx` in `server.py` reads `postfx` from `voice.json` (low shelf -2 dB @220 Hz, +1.4 dB @3.2 kHz, +0.6 dB @5.5 kHz, high shelf +1.2 dB @7 kHz).
- NEVER pitch-shift a reference with a phase vocoder (librosa `pitch_shift`): it adds an echo the clone copies (cepstral echo score 2.4 vs 1.75 clean).

**Server changes:** moods that share a reference clip share one clone prompt and one warm-up line (startup 16.5 s instead of 30-40 s); the line cache key already
hashes `voice.json` + wavs, so old cached audio is invalidated automatically.
**Verified (2026-10-17, real HTTP path):** first audio 236-284 ms, about 2x real time, brightness 1653 Hz / 21.2% above 4 kHz vs 1744 / 21.8% in the approved reel,
echo score 1.52-1.98 over 18 requests (one 2.41 outlier in 25); `run_smoke_test.ps1 -NoAudio` 14/14 PASS; mood names calm, cheerful, whisper accepted.

**Not done:** all ten mood names point at the same clip, so the brain's `[mood]` tags change nothing in the DELIVERY today (the text carries her personality);
per-mood references could be cloned FROM the locked voice later. (The persona and the hard-coded mood list were still old when this was written: DONE the same day, see the three lines below.) Not installed in the real instance (mods/ still has 1.3.0). Server is started by hand.

- **Persona DONE (2026-10-17):** `HelperPrompt` now describes a sly, bratty, a-little-smug half-dragon who teases and pouts, is warm underneath, is never cruel, jealous or possessive, and drops the act when the player is in danger or stuck; short dry spoken sentences. Persona + `knowledge.txt` is 3,620 chars, about 905 tokens (old yandere prompt 3,492 chars, about 873; +3.7%). `knowledge.txt` is unchanged.
- **Moods DONE:** one shared list, `helper/ai/HelperMoods` (calm cheerful worried battle whisper smug tease flirt mock sincere, default calm, plus a few-word hint per mood that the prompt prints), used by `HelperBrain`, `VoiceCommands` and `HelperPrompt`. `ReplyStreamer` accepts all ten, still drops unknown tags (the default mood if none was set yet), and the names are passed to the voice server unchanged; a test compares the list with `voice.json`.
- **Tests:** `design/tools/brain-test/run_brain_test.ps1` now runs 132 checks, all PASS (new: mood list vs voice.json, every tag at several chunk sizes, unknown tags, persona and knowledge facts, prompt size cap, moods through `SpeechQueue` and `LocalTtsProvider`); the full voice-server chain was SKIPPED because the server was not running. Not tried against a real LLM, so how well it keeps the persona is still open.

## 6g. 3D body from the low-poly chibi sheet, and why its textures were bad (2026-10-16, late)

**Geometry:** a clean low-poly chibi sheet (Front / Side / Back, Idle dropped) fed to Pixal3D multi-view gives a much better
mesh than the painted art did: game-like silhouette, tail and wings in the right place. Best views: front + back + right (the
left view is the mirrored side). Scripts in `helper_art/lowpoly_sheet2/`.

**Why the textures were bad: my Pixal3D settings were not the official ones.** Compared with the official ComfyUI template
(`Comfy-Org/workflow_templates` `3d_pixal3d_multi_views.json`, read 2026-10-16):

| setting | I used | official |
|---|---|---|
| `Trellis2UpsampleStage.target_resolution` | 1024 | **1536** |
| `DecimateMesh.target_face_count` (before unwrap) | 300000 | **700000** |
| `BakeTextureFromVoxel.texture_size` | 2048 | **4096** |

At 1024 the face came out as black-hole eyes and a smudge for a mouth; at 1536/700k/4096 Pixal draws real green eyes, brows
and a smile, with the deep colours. It takes only ~4 minutes on the RTX 5070 (`run_pixal_lp2.py fbr 1536 42 700000 4096`).

**Second problem: the raw output is not a usable low-poly model.** It is a 680k-triangle double-walled shell with thousands of
non-manifold edges, and its texture atlas is a shattered jigsaw (PEC unwrap). Decimating it in Blender stalls at 25-45k
triangles and wrecks the texture, so my first "fixes" went wrong (projecting the reference views gave the right face but pale,
blurry colours; the user preferred Pixal's deeper colours).

**The pipeline that works (`bake_to_lowpoly.py`, Blender 5.2 headless, CPU Cycles, ~30 s):** treat the Pixal mesh as the HIGH
poly; build the LOW poly from a copy (weld, voxel remesh at 450/height into one clean surface, light smooth, collapse-decimate
by TRIANGLE count, the remesh makes quads); smart-project UVs; bake the high poly's colour into a 4096 texture (swap its shader
for an emission of the base colour, bake type EMIT, selected-to-active, cage extrusion 2% of height). Chosen outputs:
`mesh/emily_lp2_baked_20k.glb` and `emily_lp2_baked_12k.glb`. Fair comparison renders (`render_any.py`: Standard colour
transform, unlit albedo mode + lit mode) in `lowpoly_sheet2/renders/cmp/`, before/after `before_after_v2.png`.
(Blender's default AgX view transform washes colours out; always render comparisons with Standard.)

**Still imperfect:** horns came out grey (art: black), tail has a bluish scale pattern, hair has sliver triangles at the edges,
UV islands are tiny and scattered (fine for baking, not hand-editable), and Pixal varies by seed, so rolling a few seeds and
picking the best is a cheap next step. Superseded experiment: `texture_from_views.py` (projecting the reference views).
Not done: rigging/animation, a mod-usable format, an in-game test of the 3D body, 256-512 px game texture.

**Verdict (user, 2026-10-16 night):** the baked Pixal3D result "does not look good at all" (lumpy hair/hands/boots, no edge flow,
no rig); the user also tried Meshy's free tier with the three views: "better but still clayish and mushy". Conclusion: image-to-3D
tops out below what is wanted for this stylised character; stop tuning generators (the seed-roll proposal was NOT started). Options
left, none started: (a) commission a low-poly artist from the Front/Side/Back sheet (spec: rigged, <15k tris, one 512 texture,
bones for wings/tail/hair, idle/walk/talk clips, glTF/FBX; I would write the in-game renderer), (b) a scripted "model it in
Blender with a render-and-compare loop" spike (primitives lofted to the sheet silhouettes, art projected for the face; judged by
turntable vs the sheet; drop it if not clearly better than the AI meshes), (c) VRoid Studio (free, clean rig + blink/lip-sync
blendshapes, but VRoid look), (d) keep the layered standee (section 6f) as her body. User paused the 3D work for now.

**Recommended direction if/when resumed (2026-10-16):** keep the art 2D and animate it as a layered puppet. Run See-through
(github `shitagaki-lab/see-through`, ~14-15 GB weights on Hugging Face, Python 3.12 / PyTorch 2.8 / CUDA 12.8; outputs up to 23 PSD
layers with eyes, brows, mouth, hair front/back, inpainted hidden areas and a depth image per layer; weak on tails, ponytails,
chibi proportions) on Emily's painted reference art, then extend the standee renderer (6f) to warp layers (blink, mouth from the
TTS audio level, breathing, hair/wing sway, parallax head turn). Optional: image-to-video clips (Wan/Kling) cut out as billboards
for a few big emotes. Unverified: whether See-through fits a 12 GB card, its licence, and how it handles wings/tail on Emily.
User said "Not now", nothing downloaded.

## 6i. Procedural 3D body from the low-poly chibi sheet (2026-10-17, /goal "smooth, well built, highest quality")

The user asked again for a high-quality 3D model of the sheet character, "edit the asset after generation, clean up the textures". After the
AI image-to-3D verdicts in 6g, I did NOT use a generator: the model is built procedurally and measured against the sheet.
Everything is in `design/helper_art/emily3d/`, deliverables in `emily3d/final/` (README there has files, numbers, rebuild commands).

**Pipeline:** `lib.py` (numpy loft/tube/grid/thicken toolkit) + `parts_body/dress/dragon/hair.py` -> `build_all.py` (welds every part, writes
`out/model.npz` and `out/rig_spec.json`) -> `atlas.py` (UV groups packed into two 4096 sheets B = body/dress, X = hair/horn/wings/tail; L/R parts
share texels; texel rasteriser gives position/normal per texel) -> painters `paint_face.py` (vector-drawn face in a planar front projection, 5000 px/m),
`paint_parts.py` (hair strands, scaled tail, membrane veins, dress brocade, gold bevel), `fil.py` (gold filigree on a front plane canvas and on
slit-edge-aligned canvases) -> `bl_bake_ao.py` (Cycles AO, about 9 min) -> `paint_all.py --compose` (albedo x AO per-part strength, normal maps from painted
relief, ORM) -> `bl_rig.py` (160-bone armature, geometry-derived skin weights, Idle loop, packed .blend, .glb) -> `bl_tex.py` renders, `make_sheet.py`
sheets, `bl_pose.py` pose test, `overlay.py` silhouette overlay on the sheet views, `check_mesh.py` manifold/UV report.

**Result:** 120,764 triangles, all parts closed manifold, 2 materials (3 maps each, 4096 and 2048 variants), glTF round-trip verified (re-imported and
rendered), rig verified in four poses (smooth through the slit, wings, tail, skirt, hair). Matches the sheet in silhouette (overlays), features
(elf ears, horns with cream inlay, V-shaped hair, green eyes and smirk, wings, scaled tail, slit gown with gold filigree, arm warmers, heels, gem).
**Not done:** face blendshapes/eyeballs (face is painted), a game LOD, the in-mod renderer for this mesh (not wired into blighted_enhancements, nothing
installed in the real instance), physics for hair/skirt.

**Lessons:** a stray `if` match in a patch script silently deleted half of `bl_rig.py` (restore from the Write history); AO bake depends on the UV
layout so it must be redone after any geometry change; `weld` after building tubes removes pole seams and makes every part closed; Cycles AO bake for two
4096 sheets takes about 9 min even on the RTX 5070; Eevee headless works for quick previews (3 s), Cycles 192 spp at 1200 px takes about 6 s per view.

## 6j. BUILT 2026-10-17 (late): local mind, voice-only chat, inventory, weapons, animations (blighted_enhancements 1.5.0)

**Mind.** New provider `local`, now the default: the game starts llama.cpp `llama-server` itself (`MindServerLauncher`) with
MiniCPM5-2B Q4_K_M (`C:\Users\srafa\mind\models\2b-q4km`), Vulkan build, all layers on the GPU, port 8792, the same flags as the
mind-eval bake-off (`-c 8192 -np 2 -fa on`, q8_0 KV, `--jinja`, thinking off, `--cache-reuse 256`). Settings: the `[mind]` block
of `config/blighted_enhancements-brain.toml` (home, model, backend vulkan/cuda/cpu, gpuLayers; 0 = 4 CPU threads). Log:
`logs/emily-mind-server.log`. Until the model answers its health check, proactive lines use the stock voiced lines.

**Chat, voice only.** Say her name anywhere in normal chat ("hey Emily, where is iron?", "thanks emily"), or start with `Emily,`
or `@emily`: the line is taken off public chat and she answers ALOUD only. `subtitles` now defaults to false; the real and scratch
configs were switched (backups `*.pre-local`). `nameAnywhere = false` limits it to the prefix forms.

**Body rules.** She never places blocks (no code path does). Stuck: if she is trying to reach you (no target, more than 7 blocks
away, you on the ground) and moves less than 1 block a second for 3 seconds, or has no path at all, she teleports beside you.

**Inventory.** Sneak + right-click her, or `/blighted helper inventory`: armor, both hands, a 27-slot bag (attachment
`helper_bag`) and a Curios column with an eye toggle per slot, like the player's Curios screen. Hidden curios keep working
(effects and attributes); only the drawing stops. The bag feeds her arrows. The spike's attribute fix is now production code
(`HelperInventory.onAttributes`). Curios drawing on her is on, wrapped so a curio renderer that expects a player model turns the
layer off instead of crashing.

**Weapons.** `WeaponKind` (Better Combat category first, then item class, then id): fist, sword, dagger, axe, heavy, spear,
staff, bow, crossbow. Bows and crossbows are accepted now; a ranged goal keeps 5-14 blocks, strafes, draws a bow for a second, or
cranks, shoulders and aims a crossbow (it shows loaded). Her arrows never hit you.

**Animations (procedural, `HelperGaiaModel`).** Walk with hip sway and counter-rotating shoulders; run with forward lean,
pumping arms, wings swept back, tail and hair streaming; airborne pose; breathing and weight shift; five idle flourishes
(stretch, tail-flick glance, wing ruffle, weapon play per weapon, hair flip); combat stance with a guard per weapon; relaxed carry
per weapon (heavy on the shoulder, spear/staff upright, bow low at her side, crossbow cradled); bow draw (side-on, string hand to
cheek), crossbow crank (kneeling) and crossbow aim (shouldered, head on the sights). Swings: three-hit combos per weapon
(`HelperSwings`): wind-up, strike, follow-through, recovery, with lunges, crouches and spins, wings flaring, tail/hair/skirt
lagging. Glowing additive slash trails (`SlashTrails`): crescents, thrust streaks, spin rings, a ground shockwave for heavy slams,
coloured per weapon; sparks at the strike; hit particles and sounds per weapon. The weapon now follows the whole body chain
(`translateToHand`).

## 7. Build phases

1. **Spine, no network (no key needed):** humanoid entity + follow/teleport/downed + melee fight goals, stance
   tools, client config, subtitle UI, typed chat screen, `BrainProvider` with a fake echo provider, tool registry
   with `get_status`. Test in the scratch client.
2. **Brain:** DONE except tools and memory persistence (section 6d): `OpenAiCompatBrain` (Groq/Gemini/Ollama/custom),
   state block, knowledge file, mood tags, sentence-by-sentence speech. Still to do: tool calling with server validation,
   quest progress in the state, memory across sessions.
3. **Voice:** TTS half DONE (local Qwen3-TTS server + `LocalTtsProvider` + `PcmSession`, section 6c-2). Still to do:
   `MicCapture` and an STT provider (Grok STT or a local Whisper), positional audio, mic picker.
4. **Polish:** custom Grok voice, realtime live mode, quest-book hook (a quest that hands out the helper item), art pass in the anime style.

## 8. Unknowns to settle with a spike (needs your xAI key, run by you)

1. Does the xAI chat endpoint accept tool definitions for grok-4.3 / grok-4.7? Which base URL (`/v1/chat/completions` vs `/v1/responses`)?
2. TTS `wav` output at 24 kHz: does it play cleanly in javax.sound? Time to first byte for a one-sentence reply.
3. STT accuracy with `keyterm` on pack words, using your Arctis Nova 5 mic.
4. Real per-exchange cost and latency.
Plan: a small script in `design/tools/` that reads `XAI_API_KEY` from your environment, so the key never goes through chat.

## 9. Open questions for the user

- Character: name, look, personality (a lantern wisp / dream spirit fits "Blighted Dreams" and the sakura art). Original only.
- `give_item`: allowlist-only (recommended) or no item giving at all?
- Should the helper speak unprompted (warnings about a raid, a finished hut), or only when spoken to?

## Sources

- xAI docs: voice agent, text-to-speech, speech-to-text, models (docs.x.ai/developers/...).
- Verity JE on Modrinth and CurseForge, the Verity modpack page, The Definitive Verity Pack, veritymod.org (fan guide),
  MrPauk335/Verity-Minecraft-Mod on GitHub (fan clone, licence stated inconsistently as CC0/MIT).

## 6k. Polish pass 2026-10-21 (compiled, NOT yet deployed or played)

- **Pose crossfade** (`HelperGaiaModel.crossfade`): a change of pose family (combat, carry, bow/crossbow aim, airborne, downed, riding, cast) now eases over 5 ticks from the pose she was last drawn in, instead of snapping. Steady poses, swings and flourishes are untouched. State lives in `HelperEntity.animPose*`; a gap over 8 ticks (off screen) snaps.
- **Talking gesture** eases in/out and uses two unrelated rhythms (plus a little hair follow-through); off while downed.
- **Curios**: removed the dead `blighted_enhancements:helper` id from both `curios/entities/helper.json` files (it logged "not a registered entity type" twice at every start).
- **Mind sidecar**: `isReady()` now notices a dead llama-server and lets the next request restart it (before, `ready` stayed true forever); both sidecars get a forcible kill if they ignore `destroy()` for 2 s at game exit.
- **WeaponKind.of** is cached per item (10 s expiry): it was re-reading ids, tags and Better Combat data on every animation frame.
- Deploy: close the game, then `design/tools/brain-test/compile_helper.ps1` (it also copies into the mods folder).
