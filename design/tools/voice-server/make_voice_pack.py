"""Build voice/<name>/ (voice.json + one reference WAV per mood) from the VoiceDesign output in the TTS work folder.

The server clones from these clips, so they ARE the voice. Re-run with another --take to try other designed takes.

    venv\\Scripts\\python.exe -I make_voice_pack.py [--take 1] [--name emily]
"""
import argparse
import json
import shutil
from pathlib import Path

ap = argparse.ArgumentParser()
ap.add_argument("--tts-home", default=r"C:\Users\srafa\tts")
ap.add_argument("--name", default="emily")
ap.add_argument("--take", type=int, default=1)
ap.add_argument("--seed-base", type=int, default=1000, help="design_voices.py seeds takes as seed-base + take")
args = ap.parse_args()

home = Path(args.tts_home)
cfg = json.loads((home / "scripts" / "moods.json").read_text(encoding="utf-8"))
out = Path(__file__).resolve().parent / "voice" / args.name
out.mkdir(parents=True, exist_ok=True)

moods = {}
for mood, m in cfg["moods"].items():
    src = home / "out" / "design" / f"{mood}_t{args.take}.wav"
    if not src.exists():
        raise SystemExit(f"missing {src}")
    shutil.copyfile(src, out / f"{mood}.wav")
    moods[mood] = {"file": f"{mood}.wav", "text": m["ref_text"], "style": m["style"]}

voice = {
    "name": args.name,
    "language": "English",
    "default_mood": "calm",
    "design": {
        "model": "Qwen/Qwen3-TTS-12Hz-1.7B-VoiceDesign",
        "base_voice": cfg["base_voice"],
        "take": args.take,
        "seed": args.seed_base + args.take,
        "note": "Reference clips were generated from a text description only; no real person's voice was cloned.",
    },
    "moods": moods,
}
(out / "voice.json").write_text(json.dumps(voice, indent=2, ensure_ascii=False), encoding="utf-8")
print(f"wrote {out} with moods: {', '.join(moods)}")
