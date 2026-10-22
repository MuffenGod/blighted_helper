package com.blighteddreams.helper.client.voice;

import com.blighteddreams.helper.VoiceConfig;

/**
 * Runs on her audio thread and makes the same voice sit in the world instead of on top of it:
 * a gentle high-pass so it does not fight the game's bass, a broadcast-style presence curve, a slow compressor so
 * whispers and shouts stay intelligible, soft-knee limiting, a small room/cave reverb, and a low-pass when a wall is
 * between her and the camera. No voice model, reference audio or TTS engine is changed by this class.
 */
public final class VoiceDsp {
    /** How much space is around her; values are intentionally conservative. */
    public record Environment(float reverb, float occlusion, boolean indoor) {
        public static final Environment OUTDOOR = new Environment(0.035F, 0.0F, false);

        public Environment {
            reverb = Math.max(0.0F, Math.min(0.8F, reverb));
            occlusion = Math.max(0.0F, Math.min(1.0F, occlusion));
        }
    }

    private final int rate;
    private final Biquad highPass;
    private final Biquad lowShelf;
    private final Biquad presence;
    private final Biquad highShelf;
    private final OnePole occlusionLowPass;

    private float compressorEnvelope;
    private float compressorGain = 1.0F;
    private final float attackCoeff;
    private final float releaseCoeff;
    private static final float COMPRESSOR_THRESHOLD = 0.11F; // about -19 dBFS
    private static final float COMPRESSOR_RATIO = 2.6F;
    private static final float COMPRESSOR_MAKEUP = 1.32F;

    private final Reverb reverb;
    private float targetReverb = Environment.OUTDOOR.reverb();
    private float targetOcclusion;
    private float smoothReverb = targetReverb;
    private float smoothOcclusion;

    private byte[] scratch = new byte[0];

    public VoiceDsp(int sampleRate) {
        this.rate = Math.max(8000, sampleRate);
        this.highPass = Biquad.highPass(rate, 75.0F, 0.707F);
        this.lowShelf = Biquad.lowShelf(rate, 190.0F, -0.8F, 0.8F);
        this.presence = Biquad.peak(rate, 3100.0F, 0.9F, 1.0F);
        this.highShelf = Biquad.highShelf(rate, 7600.0F, 0.5F, 0.8F);
        this.occlusionLowPass = new OnePole(lowPassCoeff(rate, 5200.0F));
        this.attackCoeff = 1.0F - (float) Math.exp(-1.0 / (0.006 * rate));
        this.releaseCoeff = 1.0F - (float) Math.exp(-1.0 / (0.16 * rate));
        this.reverb = new Reverb(rate);
    }

    /** Processes one mono 16-bit little-endian chunk and returns a mono 16-bit little-endian chunk. */
    public byte[] process(byte[] data, int offset, int length, Environment environment) {
        int evenLength = length & ~1;
        if (evenLength <= 0) return new byte[0];
        if (scratch.length < evenLength) scratch = new byte[evenLength];

        Environment env = environment == null ? Environment.OUTDOOR : environment;
        targetReverb = env.reverb();
        targetOcclusion = env.occlusion();

        if (!VoiceConfig.immersiveDsp()) {
            System.arraycopy(data, offset, scratch, 0, evenLength);
            return scratch;
        }

        for (int i = 0; i < evenLength; i += 2) {
            short raw = (short) ((data[offset + i] & 0xFF) | (data[offset + i + 1] << 8));
            float x = raw / 32768.0F;

            smoothReverb += (targetReverb - smoothReverb) * 0.0015F;
            smoothOcclusion += (targetOcclusion - smoothOcclusion) * 0.010F;

            x = highPass.process(x);
            x = lowShelf.process(x);
            x = presence.process(x);
            x = highShelf.process(x);
            x = compress(x);

            if (smoothOcclusion > 0.001F) {
                float low = occlusionLowPass.process(x);
                x = x + (low - x) * Math.min(1.0F, smoothOcclusion * 1.15F);
                x *= 1.0F - 0.14F * smoothOcclusion;
            }

            if (smoothReverb > 0.001F) {
                float wet = reverb.process(x);
                x = x * (1.0F - smoothReverb * 0.55F) + wet * smoothReverb * 0.55F;
            }

            x = softLimit(x);
            int out = Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, Math.round(x * 32767.0F)));
            scratch[i] = (byte) out;
            scratch[i + 1] = (byte) (out >> 8);
        }
        return scratch;
    }

    private float compress(float x) {
        float abs = Math.abs(x);
        float envCoeff = abs > compressorEnvelope ? attackCoeff : releaseCoeff;
        compressorEnvelope += (abs - compressorEnvelope) * envCoeff;

        float target = 1.0F;
        if (compressorEnvelope > COMPRESSOR_THRESHOLD) {
            target = (float) Math.pow(compressorEnvelope / COMPRESSOR_THRESHOLD, 1.0F / COMPRESSOR_RATIO - 1.0F);
        }
        float gainCoeff = target < compressorGain ? 0.08F : 0.0025F;
        compressorGain += (target - compressorGain) * gainCoeff;
        return x * compressorGain * COMPRESSOR_MAKEUP;
    }

    private static float softLimit(float x) {
        float a = Math.abs(x);
        if (a <= 0.86F) return x;
        float over = a - 0.86F;
        float limited = 0.86F + over / (1.0F + over * 7.0F);
        limited = Math.min(0.985F, limited);
        return x < 0.0F ? -limited : limited;
    }

    private static float lowPassCoeff(int rate, float hz) {
        float dt = 1.0F / rate;
        float rc = 1.0F / (2.0F * (float) Math.PI * hz);
        return dt / (rc + dt);
    }

    /** A tiny transposed-direct-form biquad. */
    private static final class Biquad {
        private final float b0, b1, b2, a1, a2;
        private float z1, z2;

        private Biquad(float b0, float b1, float b2, float a1, float a2) {
            this.b0 = b0;
            this.b1 = b1;
            this.b2 = b2;
            this.a1 = a1;
            this.a2 = a2;
        }

        float process(float x) {
            float y = b0 * x + z1;
            z1 = b1 * x - a1 * y + z2;
            z2 = b2 * x - a2 * y;
            return y;
        }

        static Biquad highPass(int rate, float hz, float q) {
            double w = 2 * Math.PI * hz / rate;
            double cos = Math.cos(w);
            double alpha = Math.sin(w) / (2 * q);
            double a0 = 1 + alpha;
            return new Biquad((float) ((1 + cos) / 2 / a0), (float) (-(1 + cos) / a0),
                    (float) ((1 + cos) / 2 / a0), (float) (-2 * cos / a0), (float) ((1 - alpha) / a0));
        }

        static Biquad peak(int rate, float hz, float gainDb, float q) {
            double a = Math.pow(10, gainDb / 40);
            double w = 2 * Math.PI * hz / rate;
            double cos = Math.cos(w);
            double alpha = Math.sin(w) / (2 * q);
            double a0 = 1 + alpha / a;
            return new Biquad((float) ((1 + alpha * a) / a0), (float) (-2 * cos / a0),
                    (float) ((1 - alpha * a) / a0), (float) (-2 * cos / a0), (float) ((1 - alpha / a) / a0));
        }

        static Biquad lowShelf(int rate, float hz, float gainDb, float q) {
            double a = Math.pow(10, gainDb / 40);
            double w = 2 * Math.PI * hz / rate;
            double cos = Math.cos(w);
            double alpha = Math.sin(w) / (2 * q);
            double sq = 2 * Math.sqrt(a) * alpha;
            double a0 = (a + 1) + (a - 1) * cos + sq;
            return new Biquad((float) (a * ((a + 1) - (a - 1) * cos + sq) / a0),
                    (float) (2 * a * ((a - 1) - (a + 1) * cos) / a0),
                    (float) (a * ((a + 1) - (a - 1) * cos - sq) / a0),
                    (float) (-2 * ((a - 1) + (a + 1) * cos) / a0),
                    (float) ((a + 1) + (a - 1) * cos - sq) / (float) a0);
        }

        static Biquad highShelf(int rate, float hz, float gainDb, float q) {
            double a = Math.pow(10, gainDb / 40);
            double w = 2 * Math.PI * hz / rate;
            double cos = Math.cos(w);
            double alpha = Math.sin(w) / (2 * q);
            double sq = 2 * Math.sqrt(a) * alpha;
            double a0 = (a + 1) - (a - 1) * cos + sq;
            return new Biquad((float) (a * ((a + 1) + (a - 1) * cos + sq) / a0),
                    (float) (-2 * a * ((a - 1) + (a + 1) * cos) / a0),
                    (float) (a * ((a + 1) + (a - 1) * cos - sq) / a0),
                    (float) (2 * ((a - 1) - (a + 1) * cos) / a0),
                    (float) ((a + 1) - (a - 1) * cos - sq) / (float) a0);
        }
    }

    private static final class OnePole {
        private final float alpha;
        private float y;

        private OnePole(float alpha) {
            this.alpha = alpha;
        }

        float process(float x) {
            y += alpha * (x - y);
            return y;
        }
    }

    /** Four damped comb filters into two allpasses: a small, cheap Schroeder room. */
    private static final class Reverb {
        private static final int COMBS = 4;
        private static final int ALLPASSES = 2;
        private static final int[] COMB_MS = {29, 31, 37, 41};
        private static final int[] ALLPASS_MS = {11, 7};
        private static final float FEEDBACK = 0.62F;
        private static final float DAMPING = 0.28F;
        private static final float ALLPASS_FEEDBACK = 0.45F;

        private final float[][] combBuffer = new float[COMBS][];
        private final int[] combIndex = new int[COMBS];
        private final float[] combDamp = new float[COMBS];
        private final float[][] allpassBuffer = new float[ALLPASSES][];
        private final int[] allpassIndex = new int[ALLPASSES];

        private Reverb(int rate) {
            for (int i = 0; i < COMBS; i++) {
                int len = Math.max(8, (int) (COMB_MS[i] * rate / 1000.0));
                combBuffer[i] = new float[len];
            }
            for (int i = 0; i < ALLPASSES; i++) {
                int len = Math.max(4, (int) (ALLPASS_MS[i] * rate / 1000.0));
                allpassBuffer[i] = new float[len];
            }
        }

        float process(float x) {
            float wet = 0.0F;
            for (int i = 0; i < COMBS; i++) {
                int idx = combIndex[i];
                float out = combBuffer[i][idx];
                combDamp[i] = out * (1.0F - DAMPING) + combDamp[i] * DAMPING;
                combBuffer[i][idx] = x + combDamp[i] * FEEDBACK;
                combIndex[i] = (idx + 1) % combBuffer[i].length;
                wet += out;
            }
            wet *= 0.25F;
            for (int i = 0; i < ALLPASSES; i++) {
                int idx = allpassIndex[i];
                float delayed = allpassBuffer[i][idx];
                float out = -wet + delayed;
                allpassBuffer[i][idx] = wet + delayed * ALLPASS_FEEDBACK;
                allpassIndex[i] = (idx + 1) % allpassBuffer[i].length;
                wet = out;
            }
            return wet;
        }
    }
}
