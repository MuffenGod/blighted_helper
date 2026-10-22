package com.blighteddreams.helper;

import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.List;
import java.util.Locale;

/**
 * The companion's brain settings (config/blighted_enhancements-brain.toml). Client only. See design/HELPER_PLAN.md
 * section 6d. A provider preset fills in the web address, model and the name of the environment variable that holds the
 * key; anything set in this file overrides the preset.
 */
public class BrainConfig {
    public static final ModConfigSpec SPEC;
    private static final ModConfigSpec.BooleanValue ENABLED;
    private static final ModConfigSpec.ConfigValue<String> PROVIDER;
    private static final ModConfigSpec.ConfigValue<String> MODEL;
    private static final ModConfigSpec.ConfigValue<String> FALLBACK_MODEL;
    private static final ModConfigSpec.ConfigValue<String> BASE_URL;
    private static final ModConfigSpec.ConfigValue<String> API_KEY;
    private static final ModConfigSpec.ConfigValue<String> API_KEY_ENV;
    private static final ModConfigSpec.ConfigValue<String> REASONING_EFFORT;
    private static final ModConfigSpec.DoubleValue TEMPERATURE;
    private static final ModConfigSpec.IntValue MAX_TOKENS;
    private static final ModConfigSpec.IntValue HISTORY_TURNS;
    private static final ModConfigSpec.BooleanValue SPEAK;
    private static final ModConfigSpec.BooleanValue SUBTITLES;
    private static final ModConfigSpec.BooleanValue MIND_AUTO_START;
    private static final ModConfigSpec.ConfigValue<String> MIND_HOME;
    private static final ModConfigSpec.ConfigValue<String> MIND_MODEL;
    private static final ModConfigSpec.ConfigValue<String> MIND_BACKEND;
    private static final ModConfigSpec.IntValue MIND_GPU_LAYERS;
    private static final ModConfigSpec.BooleanValue NAME_ANYWHERE;

    /** What a provider name stands for. Empty model means "you must choose one". */
    public record Preset(String baseUrl, String model, String fallbackModel, String keyEnv, String reasoningEffort, boolean needsKey) {}

    public static final List<String> PROVIDERS = List.of("local", "groq", "gemini", "ollama", "custom");

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        ENABLED = b.comment("Let the companion answer when you ask her something (/helpervoice ask ...).")
                .define("enabled", true);
        PROVIDER = b.comment("Which language model answers: local (MiniCPM5-2B on this PC through llama-server, started by the game), groq, gemini, ollama (a model running on this PC) or custom (any OpenAI-compatible server, set baseUrl).",
                        "groq and gemini have free tiers; make a key at console.groq.com or aistudio.google.com and put it in an environment variable (GROQ_API_KEY / GEMINI_API_KEY) or in apiKey below.")
                .define("provider", "local");
        MODEL = b.comment("Model id. Empty = the provider's default (groq: llama-3.3-70b-versatile, gemini: gemini-3.8-flash). Required for ollama and custom (see `ollama list`).")
                .define("model", "");
        FALLBACK_MODEL = b.comment("If the main model is rate limited or down before it says anything, retry once on this one. Empty = the provider's default (groq: llama-3.1-8b-instant).")
                .define("fallbackModel", "");
        BASE_URL = b.comment("Web address up to /chat/completions. Empty = the provider's default. Required for custom.")
                .define("baseUrl", "");
        API_KEY = b.comment("Your API key. Prefer the environment variable so the key is not stored in a file. Never share this file with a key in it.")
                .define("apiKey", "");
        API_KEY_ENV = b.comment("Name of the environment variable to read the key from. Empty = the provider's default (GROQ_API_KEY, GEMINI_API_KEY).")
                .define("apiKeyEnv", "");
        REASONING_EFFORT = b.comment("For models that think before answering: how much (none, low, medium, high). Empty = the provider's default (ollama: none). Thinking is never spoken but costs time.")
                .define("reasoningEffort", "");
        TEMPERATURE = b.comment("How adventurous her wording is. 0.7 to 0.9 suits a character.")
                .defineInRange("temperature", 0.8, 0.0, 1.5);
        MAX_TOKENS = b.comment("Longest answer, in tokens. Allows natural multi-sentence conversations.")
                .defineInRange("maxTokens", 450, 40, 1200);
        HISTORY_TURNS = b.comment("How many of your questions (and her answers) she remembers. Each one costs tokens, and free plans limit tokens per minute.")
                .defineInRange("historyTurns", 6, 0, 20);
        SPEAK = b.comment("Speak answers aloud through the local voice server. Off = text only.")
                .define("speak", true);
        SUBTITLES = b.comment("Also show each sentence in the chat window as she says it. Off by default: she only talks with her voice.")
                .define("subtitles", false);
        NAME_ANYWHERE = b.comment("Talk to her in normal chat by saying her name anywhere in the message (\"hey Emily, where's iron?\"), not only at the start.")
                .define("nameAnywhere", true);
        b.push("mind");
        MIND_AUTO_START = b.comment("provider = local: start llama-server with the model below when the game starts.")
                .define("autoStart", true);
        MIND_HOME = b.comment("Folder with bin/<backend>/unpacked/llama-server.exe and models/.")
                .define("home", System.getProperty("user.home").replace('\\', '/') + "/mind");
        MIND_MODEL = b.comment("GGUF file, absolute or relative to <home>/models.")
                .define("model", "2b-q4km/MiniCPM5-2B-Q4_K_M.gguf");
        MIND_BACKEND = b.comment("llama.cpp build to use: vulkan, cuda or cpu.")
                .define("backend", "vulkan");
        MIND_GPU_LAYERS = b.comment("Layers on the GPU (99 = all). 0 = run on 4 CPU threads, no VRAM.")
                .defineInRange("gpuLayers", 99, 0, 999);
        b.pop();
        SPEC = b.build();
    }

    public static boolean enabled() { return ENABLED.get(); }
    public static String provider() { return PROVIDER.get().trim().toLowerCase(Locale.ROOT); }
    public static String model() { return MODEL.get().trim(); }
    public static String fallbackModel() { return FALLBACK_MODEL.get().trim(); }
    public static String baseUrl() { return BASE_URL.get().trim(); }
    public static String apiKey() { return API_KEY.get().trim(); }
    public static String apiKeyEnv() { return API_KEY_ENV.get().trim(); }
    public static String reasoningEffort() { return REASONING_EFFORT.get().trim(); }
    public static double temperature() { return TEMPERATURE.get(); }
    public static int maxTokens() { return MAX_TOKENS.get(); }
    public static int historyTurns() { return HISTORY_TURNS.get(); }
    public static boolean speak() { return SPEAK.get(); }
    public static boolean subtitles() { return SUBTITLES.get(); }
    public static boolean nameAnywhere() { return NAME_ANYWHERE.get(); }
    public static boolean mindAutoStart() { return MIND_AUTO_START.get(); }
    public static String mindHome() { return MIND_HOME.get().trim(); }
    public static String mindModel() { return MIND_MODEL.get().trim(); }
    public static String mindBackend() { return MIND_BACKEND.get().trim().toLowerCase(Locale.ROOT); }
    public static int mindGpuLayers() { return MIND_GPU_LAYERS.get(); }

    /** The built-in defaults for a provider name, or null if the name is not one of {@link #PROVIDERS}. */
    public static Preset preset(String provider) {
        return switch (provider) {
            case "local" -> new Preset("http://127.0.0.1:8792/v1", "mind", "", "", "", false);
            case "groq" -> new Preset("https://api.groq.com/openai/v1", "llama-3.3-70b-versatile", "llama-3.1-8b-instant", "GROQ_API_KEY", "", true);
            case "gemini" -> new Preset("https://generativelanguage.googleapis.com/v1beta/openai", "gemini-3.8-flash", "", "GEMINI_API_KEY", "", true);
            case "ollama" -> new Preset("http://127.0.0.1:11434/v1", "", "", "", "none", false);
            case "custom" -> new Preset("", "", "", "", "", false);
            default -> null;
        };
    }
}
