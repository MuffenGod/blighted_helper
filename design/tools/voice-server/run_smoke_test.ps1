# Compiles the voice classes (JDK only, no Minecraft) and runs LocalTtsSmoke against a RUNNING voice server.
# Usage: run_smoke_test.ps1 [-NoAudio]    (start the server first: start_voice_server.ps1)
param([switch]$NoAudio)
$root = Resolve-Path "$PSScriptRoot\..\..\.."
$src = "$root\blighted_enhancements\src\main\java\com\blighteddreams\enhancements\helper"
$out = Join-Path $env:TEMP "bd_voice_smoke"
if (Test-Path $out) { Remove-Item $out -Recurse -Force }
New-Item -ItemType Directory $out | Out-Null
$javac = "C:\Program Files\Microsoft\jdk-21.0.12.101-hotspot\bin\javac.exe"
$java = "C:\Program Files\Microsoft\jdk-21.0.12.101-hotspot\bin\java.exe"
& $javac --release 21 -encoding UTF-8 -d $out "$src\ai\TtsProvider.java" "$src\ai\LocalTtsProvider.java" "$src\client\voice\PcmSession.java" "$PSScriptRoot\LocalTtsSmoke.java"
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
if ($NoAudio) { & $java -cp $out LocalTtsSmoke --no-audio } else { & $java -cp $out LocalTtsSmoke }
exit $LASTEXITCODE
