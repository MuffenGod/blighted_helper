package com.blighteddreams.helper;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Companion voice settings (config/blighted_enhancements-voice.toml). Client only. See design/HELPER_PLAN.md section 6c. */
public class VoiceConfig {
    public static final ModConfigSpec SPEC;
    private static final ModConfigSpec.BooleanValue ENABLED;
    private static final ModConfigSpec.ConfigValue<String> SERVER_URL;
    private static final ModConfigSpec.ConfigValue<String> API_KEY;
    private static final ModConfigSpec.ConfigValue<String> VOICE;
    private static final ModConfigSpec.DoubleValue VOLUME;
    private static final ModConfigSpec.IntValue CHUNK_SIZE;
    private static final ModConfigSpec.BooleanValue CACHE_LINES;
    private static final ModConfigSpec.BooleanValue AUTO_START;
    private static final ModConfigSpec.BooleanValue PROACTIVE_AI;
    private static final ModConfigSpec.ConfigValue<String> TTS_HOME;
    private static final ModConfigSpec.BooleanValue IMMERSIVE_DSP;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        ENABLED = b.comment("Speak the companion's lines aloud. Needs the local voice server to be running (auto-started by default).")
                .define("enabled", true);
        AUTO_START = b.comment("Automatically launch the local Qwen3-TTS voice server (design/tools/voice-server/server.py) in the background when joining a world or speaking.")
                .define("autoStart", true);
        PROACTIVE_AI = b.comment("Let Emily proactively react in-game (greeting on summon, low-health warnings, downed/recovery remarks, and natural chat when you say 'Emily, ...').")
                .define("proactiveAi", true);
        TTS_HOME = b.comment("Folder holding venv/, hf_cache/, and line_cache/ for the local voice server. Empty = BD_TTS_HOME environment variable or <home>/tts.")
                .define("ttsHome", "");
        IMMERSIVE_DSP = b.comment("Give her voice an in-world mix: presence EQ, gentle compression/limiting, cave and room reverb, and a low-pass when a wall is in the way. Keeps the same Qwen voice and reference takes.")
                .define("immersiveDsp", true);
        SERVER_URL = b.comment("Where the local voice server listens.")
                .define("serverUrl", "http://127.0.0.1:8791");
        API_KEY = b.comment("Bearer token, only if the voice server was started with VOICE_SERVER_KEY. Leave empty otherwise.")
                .define("apiKey", "");
        VOICE = b.comment("Name of the voice on the server (a folder under design/tools/voice-server/voice).")
                .define("voice", "emily");
        VOLUME = b.comment("Extra volume multiplier for her voice. It is also scaled by Minecraft's Master and Voice sliders.")
                .defineInRange("volume", 1.0, 0.0, 2.0);
        CHUNK_SIZE = b.comment("Codec steps the server streams per chunk (12 = one second of audio). Smaller starts speaking sooner but needs more GPU headroom.",
                        "4 gives about a quarter of a second to first sound on an RTX 5070.")
                .defineInRange("chunkSize", 4, 1, 12);
        CACHE_LINES = b.comment("Let the server keep finished lines on disk and replay repeats without using the GPU.")
                .define("cacheLines", true);
        SPEC = b.build();
    }

    public static boolean enabled() { return ENABLED.get(); }
    public static boolean autoStart() { return AUTO_START.get(); }
    public static void setAutoStart(boolean v) { AUTO_START.set(v); SPEC.save(); }
    public static boolean proactiveAi() { return PROACTIVE_AI.get(); }
    public static void setProactiveAi(boolean v) { PROACTIVE_AI.set(v); SPEC.save(); }
    public static String ttsHome() { return TTS_HOME.get().trim(); }
    public static boolean immersiveDsp() { return IMMERSIVE_DSP.get(); }
    public static String serverUrl() { return SERVER_URL.get(); }
    public static String apiKey() { return API_KEY.get(); }
    public static String voice() { return VOICE.get(); }
    public static double volume() { return VOLUME.get(); }
    public static int chunkSize() { return CHUNK_SIZE.get(); }
    public static boolean cacheLines() { return CACHE_LINES.get(); }
}
