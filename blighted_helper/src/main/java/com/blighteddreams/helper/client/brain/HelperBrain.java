package com.blighteddreams.helper.client.brain;

import com.blighteddreams.helper.BrainConfig;
import com.blighteddreams.helper.VoiceConfig;
import com.blighteddreams.helper.ai.Conversation;
import com.blighteddreams.helper.ai.HelperMoods;
import com.blighteddreams.helper.ai.HelperPrompt;
import com.blighteddreams.helper.ai.OpenAiCompatBrain;
import com.blighteddreams.helper.ai.ReplyStreamer;
import com.blighteddreams.helper.client.voice.HelperVoice;
import com.blighteddreams.helper.client.voice.SpeechQueue;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.neoforged.fml.loading.FMLPaths;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Consumer;

/**
 * The companion's brain, client side (design/HELPER_PLAN.md section 6d): {@code HelperBrain.ask("question")} gathers the
 * state of the world, asks the configured language model, and speaks the answer sentence by sentence through the local
 * voice server, showing each sentence in the chat as she says it. Without a voice server she answers in text only.
 * Client only: never reference this class from code that also runs on a dedicated server.
 */
public final class HelperBrain {
    private static final Logger LOGGER = LoggerFactory.getLogger("blighted_enhancements/brain");
    private static final String KNOWLEDGE_RESOURCE = "/assets/blighted_helper/helper/knowledge.txt";
    private static final String KNOWLEDGE_OVERRIDE = "blighted_helper-knowledge.txt";
    private static final long REPORT_COOLDOWN_MS = 8_000;
    private static final long SERVER_FACTS_TIMEOUT_MS = 500;

    /** What the config and the provider preset add up to. {@code problem} is set when she cannot answer yet. */
    private record Settings(String provider, String url, String model, String fallback, String key, String keySource,
                            String effort, String problem) {}

    private static OpenAiCompatBrain brain;
    private static Conversation conversation;
    private static SpeechQueue queue;
    private static String builtFor = "";
    private static String knowledge;
    private static volatile String dynamicKnowledge;
    private static volatile long lastSpokeMs;
    private static volatile long requestGeneration;
    private static long lastReportMs;

    private HelperBrain() {}

    /** True if the brain is enabled and has a valid model & key/provider configured. */
    public static boolean isReadyForProactive() {
        if ("local".equals(BrainConfig.provider()) && !MindServerLauncher.isReady()) {
            MindServerLauncher.ensureRunningAsync();
            return false;
        }
        return BrainConfig.enabled() && settings().problem() == null;
    }

    /** The player asks her something. Call on the game thread. Returns at once; the answer streams in. */
    public static void ask(String text) {
        askInternal(text, false, false);
    }

    /** Proactive/system prompt: asks the brain if ready, without printing red error alerts if the server is busy. */
    public static void askSilent(String text) {
        askInternal(text, true, false);
    }

    /** Proactively reacts to an in-game event without injecting fake user dialogue. */
    public static void react(String eventCue) {
        askInternal(eventCue, true, true);
    }

    /** Checks if Emily spoke within the last 30 seconds for active follow-up conversation. */
    public static boolean inConversationWindow() {
        return System.currentTimeMillis() - lastSpokeMs < 30_000L;
    }

    public static long lastSpokeMs() {
        return lastSpokeMs;
    }

    private static void askInternal(String text, boolean silentOnFail, boolean isReaction) {
        if (text == null || text.isBlank()) {
            return;
        }
        if (!BrainConfig.enabled()) {
            if (!silentOnFail) {
                HelperVoice.chat("The companion's brain is switched off in config/blighted_enhancements-brain.toml.");
            }
            return;
        }
        Settings s = settings();
        if (s.problem() != null) {
            if (!silentOnFail) {
                HelperVoice.chat(s.problem());
            }
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }
        if ("local".equals(s.provider()) && !MindServerLauncher.isReady()) {
            MindServerLauncher.ensureRunningAsync();
        }
        String clientFacts = HelperContext.build(mc); // reads the world: game thread only
        Conversation c;
        SpeechQueue q;
        synchronized (HelperBrain.class) {
            ensureBuilt(s);
            c = conversation;
            q = queue;
        }
        if (silentOnFail && c.isAnswering()) {
            return; // a proactive remark never cuts off an answer to the player
        }
        HelperVoice.stop(); // never talk over a line from /helpervoice say
        q.clear();
        long request = beginRequest();
        String question = text.strip();
        ServerStateCache.fetch(SERVER_FACTS_TIMEOUT_MS, serverFacts -> {
            if (request != requestGeneration) {
                return; // a summon/dismiss/forced line happened while the state reply was in flight
            }
            String facts = serverFacts == null || serverFacts.isBlank() ? clientFacts : clientFacts + "\n" + serverFacts;
            dynamicKnowledge = com.blighteddreams.helper.ai.HelperKnowledge.buildRelevantKnowledge(question, facts, knowledge());
            LOGGER.info("{} {} ({} chars of state)", isReaction ? "Reacting with" : "Asking", s.model(), facts.length());
            Conversation.Output out = new Conversation.Output() {
                @Override
                public void segment(ReplyStreamer.Segment seg) {
                    q.enqueue(seg);
                }

                @Override
                public void finished(boolean spokenAnyText) {
                    if (!spokenAnyText && !silentOnFail) {
                        HelperVoice.speak("Hm. Nothing to say to that.", "smug");
                    }
                }

                @Override
                public void failed(String message) {
                    LOGGER.warn("The brain could not answer: {}", message);
                    q.clear();
                    if (!silentOnFail) {
                        report(message);
                    }
                }

                @Override
                public void action(String body) {
                    // The model asked her body to do something. The block was stripped from speech already.
                    HelperAutoAi.handleAction(body);
                }
            };
            if (isReaction) {
                c.react(question, facts, out);
            } else {
                c.ask(question, facts, out);
            }
        });
    }

    private static synchronized long beginRequest() {
        return ++requestGeneration;
    }

    /** Cuts off the answer being written and the speech being played. */
    public static synchronized void stop() {
        requestGeneration++;
        if (conversation != null) {
            conversation.cancel();
        }
        if (queue != null) {
            queue.clear();
        }
    }

    /** Wipes what she remembers of this conversation. */
    public static synchronized void forget() {
        requestGeneration++;
        if (conversation != null) {
            conversation.forget();
        }
        if (queue != null) {
            queue.clear();
        }
        ServerStateCache.clear();
    }

    /** Describes the current setup for the player. Never prints the key. */
    public static void status(Consumer<String> reply) {
        Settings s = settings();
        int turns;
        synchronized (HelperBrain.class) {
            turns = conversation == null ? 0 : conversation.turns();
        }
        StringBuilder sb = new StringBuilder();
        sb.append("Brain: ").append(BrainConfig.enabled() ? "on" : "OFF").append(", provider ").append(s.provider())
                .append(s.model().isEmpty() ? "" : ", model " + s.model())
                .append(s.fallback().isEmpty() ? "" : " (fallback " + s.fallback() + ")")
                .append(", key ").append(s.keySource()).append(", remembers ").append(turns).append(" turn(s)")
                .append(BrainConfig.speak() ? ", speaks aloud" : ", text only").append('.');
        if (s.problem() != null) {
            sb.append(" PROBLEM: ").append(s.problem());
        }
        reply.accept(sb.toString());
        if (BrainConfig.speak()) {
            HelperVoice.status(reply);
        }
    }

    // ------------------------------------------------------------------------------------------------ setup

    private static Settings settings() {
        String provider = BrainConfig.provider();
        BrainConfig.Preset pre = BrainConfig.preset(provider);
        if (pre == null) {
            return failed(provider, "Unknown brain provider '" + provider + "'. Use one of " + BrainConfig.PROVIDERS + " in config/blighted_enhancements-brain.toml.");
        }
        String url = first(BrainConfig.baseUrl(), pre.baseUrl());
        String model = first(BrainConfig.model(), pre.model());
        String fallback = first(BrainConfig.fallbackModel(), pre.fallbackModel());
        String effort = first(BrainConfig.reasoningEffort(), pre.reasoningEffort());
        String envName = first(BrainConfig.apiKeyEnv(), pre.keyEnv());
        String key = BrainConfig.apiKey();
        String source = key.isEmpty() ? "missing" : "from the config file";
        if (key.isEmpty() && !envName.isEmpty()) {
            String fromEnv = System.getenv(envName);
            if (fromEnv != null && !fromEnv.isBlank()) {
                key = fromEnv.trim();
                source = "from the environment variable " + envName;
            }
        }
        if (!pre.needsKey() && key.isEmpty()) {
            source = "not needed";
        }
        String problem = null;
        if (url.isEmpty()) {
            problem = "Set baseUrl in config/blighted_enhancements-brain.toml (provider " + provider + " has no default address).";
        } else if (model.isEmpty()) {
            problem = "Set model in config/blighted_enhancements-brain.toml" + ("ollama".equals(provider) ? " to a model you have pulled (run `ollama list`)." : ".");
        } else if (pre.needsKey() && key.isEmpty()) {
            problem = "No API key for " + provider + ". Make a free key, then set the environment variable " + envName
                    + " (and restart the game) or put it in apiKey in config/blighted_enhancements-brain.toml.";
        }
        return new Settings(provider, url, model, fallback, key, source, effort, problem);
    }

    private static Settings failed(String provider, String problem) {
        return new Settings(provider, "", "", "", "", "missing", "", problem);
    }

    private static String first(String a, String b) {
        return a != null && !a.isEmpty() ? a : b;
    }

    /** (Re)builds the model client, conversation and speech queue when the settings changed. Caller holds the class lock. */
    private static void ensureBuilt(Settings s) {
        String voiceUrl = VoiceConfig.serverUrl();
        String name = HelperAutoAi.companionName();
        String key = String.join("|", s.provider(), s.url(), s.model(), s.fallback(), Integer.toHexString(s.key().hashCode()), s.effort(),
                String.valueOf(BrainConfig.temperature()), String.valueOf(BrainConfig.maxTokens()), String.valueOf(BrainConfig.historyTurns()),
                name, String.valueOf(BrainConfig.speak()), VoiceConfig.voice(), voiceUrl, String.valueOf(VoiceConfig.chunkSize()));
        if (conversation != null && key.equals(builtFor)) {
            return;
        }
        if (queue != null) {
            queue.close();
        }
        if (brain != null) {
            brain.close();
        }
        brain = new OpenAiCompatBrain(label(s.provider()), s.url(), s.key(), s.model(), s.fallback(),
                BrainConfig.temperature(), BrainConfig.maxTokens(), s.effort());
        conversation = new Conversation(brain, () -> HelperPrompt.system(HelperAutoAi.companionName(), HelperMoods.all(), dynamicKnowledge != null ? dynamicKnowledge : knowledge()),
                BrainConfig.historyTurns(), HelperMoods.set(), HelperMoods.DEFAULT, name);
        queue = new SpeechQueue(BrainConfig.speak() ? HelperVoice.tts() : null, VoiceConfig.voice(),
                HelperVoice::gain, HelperVoice::calculateSpatialGains, new SpeechQueue.Listener() {
            @Override
            public void segmentStarted(ReplyStreamer.Segment segment, boolean withVoice) {
                lastSpokeMs = System.currentTimeMillis();
                if (BrainConfig.subtitles()) {
                    HelperVoice.chat(Component.literal("<" + HelperAutoAi.companionName() + "> ").withStyle(ChatFormatting.LIGHT_PURPLE)
                            .append(Component.literal(segment.displayText()).withStyle(ChatFormatting.WHITE)));
                }
            }

            @Override
            public void problem(String message) {
                report(message.contains("ConnectException")
                        ? "Her voice server is not running, so she answers in text only. Start design/tools/voice-server/start_voice_server.ps1."
                        : "Her voice: " + message);
            }

            @Override
            public void idle() {
                // nothing to do: the next question starts everything again
            }
        });
        builtFor = key;
        LOGGER.info("Brain ready: {} at {} (voice {})", s.model(), s.url(), BrainConfig.speak() ? voiceUrl : "off");
    }

    private static String label(String provider) {
        return switch (provider) {
            case "local" -> "her local mind";
            case "groq" -> "Groq";
            case "gemini" -> "Gemini";
            case "ollama" -> "Ollama";
            default -> "your model server";
        };
    }

    /** The pack knowledge: the player's own file in the config folder if there is one, else the one built into the mod. */
    private static synchronized String knowledge() {
        if (knowledge != null) {
            return knowledge;
        }
        Path override = FMLPaths.CONFIGDIR.get().resolve(KNOWLEDGE_OVERRIDE);
        try {
            if (Files.isRegularFile(override)) {
                knowledge = Files.readString(override, StandardCharsets.UTF_8);
                LOGGER.info("Using the knowledge file {}", override);
                return knowledge;
            }
        } catch (IOException e) {
            LOGGER.warn("Could not read {}: {}", override, e.toString());
        }
        try (InputStream in = HelperBrain.class.getResourceAsStream(KNOWLEDGE_RESOURCE)) {
            knowledge = in == null ? "(no knowledge file found)" : new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            knowledge = "(the knowledge file could not be read)";
        }
        return knowledge;
    }

    /** Tells the player about a problem, but not on every failed attempt. */
    private static void report(String message) {
        long now = System.currentTimeMillis();
        synchronized (HelperBrain.class) {
            if (now - lastReportMs < REPORT_COOLDOWN_MS) {
                return;
            }
            lastReportMs = now;
        }
        HelperVoice.chat(Component.literal(message).withStyle(ChatFormatting.RED));
    }
}
