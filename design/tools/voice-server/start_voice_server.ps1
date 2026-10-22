# Starts the local voice server (Qwen3-TTS) on 127.0.0.1:8791. Loading takes about 30-40 s; /health says "loading" until ready.
# BD_TTS_HOME (default C:\Users\srafa\tts) holds venv\, hf_cache\ (models) and line_cache\.
param([int]$Port = 8791)
$tts = if ($env:BD_TTS_HOME) { $env:BD_TTS_HOME } else { 'C:\Users\srafa\tts' }
$env:HF_HOME = "$tts\hf_cache"
$env:HF_HUB_OFFLINE = '1'
$env:PYTHONIOENCODING = 'utf-8'
& "$tts\venv\Scripts\python.exe" -I "$PSScriptRoot\server.py" --port $Port --cache-dir "$tts\line_cache"
