package com.blighteddreams.helper.client.voice;

import com.blighteddreams.helper.HelperEntity;
import com.blighteddreams.helper.VoiceConfig;
import com.blighteddreams.helper.ai.LocalTtsProvider;
import com.blighteddreams.helper.ai.TtsProvider;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Client-side entry point for the companion's voice: {@code HelperVoice.speak("text", "worried")}. The brain (later) and
 * the /helpervoice test commands both go through here. A new line replaces whatever is still playing. Client only:
 * never reference this class from code that also runs on a dedicated server.
 */
public final class HelperVoice {
    private static final Logger LOGGER = LoggerFactory.getLogger("blighted_enhancements/voice");
    private static final long REPORT_COOLDOWN_MS = 10_000;

    private static LocalTtsProvider provider;
    private static String providerKey = "";
    private static PcmSession session;
    private static TtsProvider.Handle handle;
    private static long lastReportMs;
    private static volatile long speakingUntilMs;
    private static volatile VoiceDsp.Environment cachedEnvironment = VoiceDsp.Environment.OUTDOOR;
    private static volatile long environmentUntilMs;

    private HelperVoice() {}

    /** Call from audio playback threads to keep the speaking state alive while audio plays. */
    public static void markSpeaking(int extraMs) {
        speakingUntilMs = Math.max(speakingUntilMs, System.currentTimeMillis() + extraMs);
    }

    /** True if Emily is actively playing spoken audio. */
    public static boolean isSpeaking() {
        return System.currentTimeMillis() < speakingUntilMs;
    }

    private static final java.util.regex.Pattern CLEAN_TAGS = java.util.regex.Pattern.compile("\\[[^\\]]*\\]|\\([^)]*\\)|\\*[^*]*\\*");

    /**
     * Turns written text into something a voice actor would actually read: no stage directions or tags, expanded
     * chat shorthand, spoken punctuation, and cleaner Minecraft terms. The voice and reference takes are unchanged;
     * this only gives the same Qwen voice better material to perform.
     */
    public static String sanitize(String input) {
        if (input == null) return "";
        String s = CLEAN_TAGS.matcher(input).replaceAll(" ");
        s = s.replace('\u2014', ' ').replace('\u2013', ' ').replace('→', ' ');
        s = s.replace("&", " and ");
        s = s.replaceAll("(?i)\\bw/o\\b", "without");
        s = s.replaceAll("(?i)\\bw/\\b", "with");
        s = s.replaceAll("(?i)\\be\\.g\\.", "for example");
        s = s.replaceAll("(?i)\\bi\\.e\\.", "that is");
        s = s.replaceAll("(?i)\\betc\\.", "et cetera");
        s = s.replaceAll("(?i)\\bvs\\.?", "versus");
        s = s.replaceAll("(?i)\\bmr\\.", "mister");
        s = s.replaceAll("(?i)\\bmrs\\.", "missus");
        s = s.replaceAll("(?i)\\bdr\\.", "doctor");
        s = s.replaceAll("(?i)\\bPvP\\b", "player versus player");
        s = s.replaceAll("(?i)\\bXP\\b", "experience");
        s = s.replaceAll("(?i)\\bHP\\b", "health");
        s = s.replaceAll("(?i)\\bAFK\\b", "away from keyboard");
        s = s.replaceAll("(?i)\\bNPC\\b", "non player character");
        s = s.replaceAll("(?i)\\bDPS\\b", "damage per second");
        s = s.replaceAll("(?i)\\bTTS\\b", "text to speech");
        s = s.replaceAll("(?i)\\bngl\\b", "not gonna lie");
        s = s.replaceAll("(?i)\\btbh\\b", "to be honest");
        s = s.replaceAll("(?i)\\bidk\\b", "I don't know");
        s = s.replaceAll("(?i)\\bimo\\b", "in my opinion");
        s = s.replaceAll("(?i)\\bbtw\\b", "by the way");
        s = s.replaceAll("(?i)\\brn\\b", "right now");
        s = s.replaceAll("(?i)\\bbrb\\b", "be right back");
        s = s.replaceAll("(?i)\\bgtg\\b", "got to go");
        s = s.replaceAll("(?i)\\bpls\\b", "please");
        s = s.replaceAll("(?i)\\bthx\\b", "thanks");
        s = s.replaceAll("(?i)\\blol\\b", "heh");
        s = s.replaceAll("(?i)\\bomg\\b", "oh my god");
        s = s.replaceAll("(?i)\\bwtf\\b", "what the hell");
        s = s.replaceAll("(?i)\\birl\\b", "in real life");
        s = s.replaceAll("\\s*…\\s*", ", ");
        s = s.replaceAll("\\s*\\.{2,}\\s*", ", ");
        s = s.replaceAll("\\s+-\\s+", ", ");
        s = s.replaceAll("\\s+", " ").trim();
        return s;
    }

    /** Speaks {@code text} in the given mood (null = the voice's default). Returns at once; audio streams in. */
    public static void speak(String text, String mood) {
        String clean = sanitize(text);
        if (!VoiceConfig.enabled() || clean.isBlank()) {
            return;
        }
        // A line you ask for interrupts her answer. Done before taking this class's lock: the brain takes its own lock
        // and then calls into this class, so the order must always be brain first.
        com.blighteddreams.helper.client.brain.HelperBrain.stop();
        speakLine(clean, mood);
    }

    private static synchronized void speakLine(String text, String mood) {
        stop();
        LocalTtsProvider p = provider();
        PcmSession s = new PcmSession(HelperVoice::gain, HelperVoice::calculateSpatialGains,
                e -> report("Cannot open an audio device for her voice: " + e.getMessage()));
        session = s;
        long t0 = System.nanoTime();
        AtomicBoolean firstAudio = new AtomicBoolean(true);
        LOGGER.info("Speaking {} chars{}", text.length(), mood == null ? "" : " (" + mood + ")");
        handle = p.speak(new TtsProvider.Request(text, VoiceConfig.voice(), mood), new TtsProvider.Sink() {
            @Override public void onFormat(int sampleRate) { s.onFormat(sampleRate); }

            @Override
            public void onPcm(byte[] data, int offset, int length) {
                if (firstAudio.compareAndSet(true, false)) {
                    LOGGER.info("First audio {} ms after the request", (System.nanoTime() - t0) / 1_000_000);
                }
                s.onPcm(data, offset, length);
            }

            @Override
            public void onComplete() {
                s.onComplete();
                LOGGER.info("Finished speaking after {} ms", (System.nanoTime() - t0) / 1_000_000);
            }

            @Override
            public void onError(Exception error) {
                s.onError(error);
                report("Her voice is unavailable: " + p.lastHealth().detail() + ".");
                LOGGER.warn("Voice request failed: {}", error.toString());
            }
        });
    }

    /** Cuts off the current line, if any. */
    public static synchronized void stop() {
        speakingUntilMs = 0;
        if (handle != null) {
            handle.cancel();
            handle = null;
        }
        if (session != null) {
            session.stop();
            session = null;
        }
    }

    /**
     * Full voice reset for summon/dismiss/replacement. Stops the direct line and the brain's sentence queue, then clears
     * the cached acoustic space so a stale cave reverb cannot follow her into a new dimension or a fresh summon.
     */
    public static void reset() {
        com.blighteddreams.helper.client.brain.HelperBrain.stop();
        stop();
        environmentUntilMs = 0L;
        cachedEnvironment = VoiceDsp.Environment.OUTDOOR;
    }

    /** Asks the voice server how it is doing; the reply arrives on a background thread. */
    public static synchronized void status(Consumer<String> reply) {
        LocalTtsProvider p = provider();
        p.checkHealth(h -> reply.accept(p.name() + ": " + h.detail()));
    }

    /** The voice backend built from the voice config, shared with the brain's speech queue. */
    public static synchronized LocalTtsProvider tts() {
        return provider();
    }

    private static LocalTtsProvider provider() {
        VoiceServerLauncher.ensureRunningAsync();
        String key = VoiceConfig.serverUrl() + "|" + VoiceConfig.apiKey() + "|" + VoiceConfig.chunkSize() + "|" + VoiceConfig.cacheLines();
        if (provider == null || !key.equals(providerKey)) {
            if (provider != null) {
                provider.close();
            }
            provider = new LocalTtsProvider(VoiceConfig.serverUrl(), VoiceConfig.apiKey(), VoiceConfig.chunkSize(), VoiceConfig.cacheLines());
            provider.setWakeupHook(VoiceServerLauncher::ensureRunningAsync);
            providerKey = key;
        }
        return provider;
    }

    /** Config volume times Minecraft's Master and Voice sliders. */
    public static double gain() {
        Options o = Minecraft.getInstance().options;
        return VoiceConfig.volume() * o.getSoundSourceVolume(SoundSource.MASTER) * o.getSoundSourceVolume(SoundSource.VOICE);
    }

    /**
     * Samples the space around Emily so the same voice can sound like it is in a cave, a base or the open air. Cached
     * briefly because several audio chunks per second ask for it. Never changes the voice itself.
     */
    public static VoiceDsp.Environment environment() {
        long now = System.currentTimeMillis();
        if (now < environmentUntilMs) {
            return cachedEnvironment;
        }
        environmentUntilMs = now + 220L;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.gameRenderer == null) {
            return cachedEnvironment = VoiceDsp.Environment.OUTDOOR;
        }
        HelperEntity emily = findEmily(mc);
        if (emily == null) {
            return cachedEnvironment = VoiceDsp.Environment.OUTDOOR;
        }

        BlockPos feet = emily.blockPosition();
        boolean nether = mc.level.dimension().location().getPath().contains("nether");
        boolean end = mc.level.dimension().location().getPath().contains("end");
        boolean sky = mc.level.canSeeSky(feet.above(2));
        boolean cave = !sky && feet.getY() < 62;

        int solid = 0;
        int total = 0;
        for (BlockPos pos : BlockPos.betweenClosed(feet.offset(-4, -2, -4), feet.offset(4, 4, 4))) {
            if (!mc.level.isLoaded(pos)) continue;
            total++;
            if (mc.level.getBlockState(pos).isSolidRender(mc.level, pos)) solid++;
        }
        float enclosure = total == 0 ? 0.0F : solid / (float) total;

        float reverb;
        if (nether || end || cave) {
            reverb = 0.30F;
        } else if (enclosure > 0.60F) {
            reverb = 0.18F;
        } else {
            reverb = 0.035F;
        }
        if (emily.isInWater()) reverb = Math.min(0.32F, reverb + 0.06F);

        float occlusion = 0.0F;
        try {
            Vec3 from = mc.gameRenderer.getMainCamera().getPosition();
            Vec3 to = emily.getEyePosition();
            if (from.distanceToSqr(to) > 6.25) {
                BlockHitResult hit = mc.level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER,
                        ClipContext.Fluid.NONE, mc.player));
                if (hit.getType() == HitResult.Type.BLOCK) {
                    var state = mc.level.getBlockState(hit.getBlockPos());
                    occlusion = state.isSolidRender(mc.level, hit.getBlockPos()) ? 1.0F : 0.35F;
                }
            }
        } catch (RuntimeException ignored) {
            // Never let a raycast on the audio thread disturb playback.
        }

        return cachedEnvironment = new VoiceDsp.Environment(reverb, occlusion, cave || enclosure > 0.60F);
    }

    private static HelperEntity findEmily(Minecraft mc) {
        if (mc.level == null || mc.player == null) return null;
        List<HelperEntity> helpers = mc.level.getEntitiesOfClass(
                HelperEntity.class,
                mc.player.getBoundingBox().inflate(128.0),
                e -> e.isOwner(mc.player)
        );
        if (helpers.isEmpty()) return null;
        HelperEntity nearest = helpers.get(0);
        for (HelperEntity candidate : helpers) {
            if (candidate.distanceToSqr(mc.player) < nearest.distanceToSqr(mc.player)) nearest = candidate;
        }
        return nearest;
    }

    /**
     * Calculates equal-power stereo panning and distance falloff based on Emily's 3D position
     * relative to the client player's camera.
     * Returns float[]{leftGain, rightGain}.
     */
    public static float[] calculateSpatialGains() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.gameRenderer == null) {
            return new float[]{1.0f, 1.0f};
        }
        var cam = mc.gameRenderer.getMainCamera();
        if (cam == null) {
            return new float[]{1.0f, 1.0f};
        }

        HelperEntity emily = findEmily(mc);
        if (emily == null) {
            return new float[]{1.0f, 1.0f};
        }
        Vec3 camPos = cam.getPosition();
        Vec3 emilyPos = emily.position().add(0, emily.getEyeHeight(), 0);

        double dx = emilyPos.x - camPos.x;
        double dy = emilyPos.y - camPos.y;
        double dz = emilyPos.z - camPos.z;
        double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);

        // Companion near-field curve:
        // Her voice is 100% full volume and crystal clear within normal companion walking/follow range (0 to 4 blocks).
        // It then rolls off smoothly and naturally as distance grows, fading out gently past 24 blocks.
        float attenuation;
        if (dist <= 4.0) {
            attenuation = 1.0f;
        } else if (dist >= 32.0) {
            attenuation = 0.0f;
        } else {
            float excess = (float) (dist - 4.0);
            float curve = 4.0f / (4.0f + excess * 0.75f);
            float fade = Math.max(0.0f, 1.0f - excess / 28.0f);
            attenuation = curve * fade;
        }

        // Stereo panning via camera left vector
        Vector3f leftVec = cam.getLeftVector();
        float leftDot = 0.0f;
        if (dist > 0.001) {
            leftDot = (float) ((dx * leftVec.x + dy * leftVec.y + dz * leftVec.z) / dist);
        }
        leftDot = Math.max(-1.0f, Math.min(1.0f, leftDot));

        // Front vs Rear attenuation: sound coming from behind is softened very subtly (max 10%)
        Vec3 viewVec = mc.player.getViewVector(1.0f);
        float forwardDot = 0.0f;
        if (dist > 0.001) {
            forwardDot = (float) ((dx * viewVec.x + dy * viewVec.y + dz * viewVec.z) / dist);
        }
        float rearFactor = forwardDot < 0 ? (1.0f + forwardDot * 0.10f) : 1.0f;
        attenuation *= rearFactor;

        // Pan: -1 (hard right) -> 0.0, 0 (center) -> 0.5, +1 (hard left) -> 1.0
        float pan = (leftDot + 1.0f) * 0.5f;
        float panAngle = pan * (float) (Math.PI / 2.0);
        float leftPan = (float) Math.sin(panAngle);
        float rightPan = (float) Math.cos(panAngle);

        // Natural acoustic interaural crossfeed (cross-talk):
        // 0.28 allows clear directional localization while keeping both ears engaged so audio doesn't feel thin
        float crosstalk = 0.28f;
        leftPan = leftPan * (1.0f - crosstalk) + 0.7071f * crosstalk;
        rightPan = rightPan * (1.0f - crosstalk) + 0.7071f * crosstalk;

        float norm = 1.4142f * attenuation;
        return new float[]{leftPan * norm, rightPan * norm};
    }

    /** Tells the player once in a while (not for every failed line) that her voice is not working. */
    private static void report(String message) {
        long now = System.currentTimeMillis();
        synchronized (HelperVoice.class) {
            if (now - lastReportMs < REPORT_COOLDOWN_MS) {
                return;
            }
            lastReportMs = now;
        }
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> {
            if (mc.player != null) {
                mc.player.displayClientMessage(Component.literal(message), false);
            }
        });
    }

    /** Shows a message in the chat window from any thread. */
    public static void chat(String message) {
        chat(Component.literal(message));
    }

    /** Shows a message in the chat window from any thread. Only the local player sees it. */
    public static void chat(Component message) {
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> {
            if (mc.player != null) {
                mc.player.displayClientMessage(message, false);
            }
        });
    }
}
