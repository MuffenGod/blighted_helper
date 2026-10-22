package com.blighteddreams.helper.client.voice;

import com.blighteddreams.helper.ai.TtsProvider;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.SourceDataLine;
import java.util.function.Consumer;
import java.util.function.DoubleSupplier;

/**
 * Plays one spoken line through a javax.sound line as the PCM arrives. One session per line; a new line stops the old
 * one with {@link #stop()}. Writes block when the line's buffer is full, which gives the HTTP stream natural
 * back-pressure. JDK-only, so it can be tested without Minecraft.
 */
public final class PcmSession implements TtsProvider.Sink {
    private static final int LINE_BUFFER_MS = 300;
    private static final int PREBUFFER_MS = 120; // start playing once this much is queued, to avoid a stuttering start
    private static final int DRAIN_TIMEOUT_MS = 8000;

    private final DoubleSupplier gain; // read for every write, so a volume change applies mid-line
    private final java.util.function.Supplier<float[]> spatialGains; // {leftGain, rightGain}
    private final Consumer<Exception> onProblem;
    private final Object lock = new Object();

    private volatile SourceDataLine line;
    private volatile VoiceDsp dsp;
    private int prebufferBytes;
    private long writtenBytes;
    private boolean started;
    private volatile boolean stopped;
    private volatile boolean ended;
    private long firstAudioNanos;

    /**
     * @param gain      linear volume, 1.0 = as generated
     * @param onProblem told once if the audio device cannot be opened
     */
    public PcmSession(DoubleSupplier gain, Consumer<Exception> onProblem) {
        this(gain, null, onProblem);
    }

    public PcmSession(DoubleSupplier gain, java.util.function.Supplier<float[]> spatialGains, Consumer<Exception> onProblem) {
        this.gain = gain;
        this.spatialGains = spatialGains;
        this.onProblem = onProblem;
    }

    @Override
    public void onFormat(int sampleRate) {
        dsp = new VoiceDsp(sampleRate);
        AudioFormat format = new AudioFormat(sampleRate, 16, 2, true, false);
        try {
            SourceDataLine l = (SourceDataLine) AudioSystem.getLine(new DataLine.Info(SourceDataLine.class, format));
            l.open(format, bytesFor(sampleRate, LINE_BUFFER_MS));
            synchronized (lock) {
                if (stopped) {
                    l.close();
                    return;
                }
                line = l;
                prebufferBytes = bytesFor(sampleRate, PREBUFFER_MS);
            }
        } catch (LineUnavailableException | IllegalArgumentException e) {
            stopped = true;
            onProblem.accept(e);
        }
    }

    @Override
    public void onPcm(byte[] data, int offset, int length) {
        SourceDataLine l = line;
        if (l == null || stopped || length <= 0) {
            return;
        }
        double baseGain = gain.getAsDouble();
        float leftMult = (float) baseGain;
        float rightMult = (float) baseGain;
        if (spatialGains != null) {
            float[] sp = spatialGains.get();
            if (sp != null && sp.length >= 2) {
                leftMult *= sp[0];
                rightMult *= sp[1];
            }
        }

        int sampleCount = length / 2;
        byte[] mono = data;
        int monoOffset = offset;
        VoiceDsp processor = dsp;
        if (processor != null) {
            mono = processor.process(data, offset, length, HelperVoice.environment());
            monoOffset = 0;
        }
        byte[] out = new byte[sampleCount * 4];
        int outIdx = 0;
        for (int i = 0; i < sampleCount * 2; i += 2) {
            int s = (short) ((mono[monoOffset + i] & 0xFF) | (mono[monoOffset + i + 1] << 8));
            int leftSample = Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, Math.round(s * leftMult)));
            int rightSample = Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, Math.round(s * rightMult)));

            out[outIdx++] = (byte) leftSample;
            out[outIdx++] = (byte) (leftSample >> 8);
            out[outIdx++] = (byte) rightSample;
            out[outIdx++] = (byte) (rightSample >> 8);
        }

        if (writtenBytes == 0) {
            firstAudioNanos = System.nanoTime();
        }
        try {
            l.write(out, 0, out.length); // blocks while the line buffer is full; returns early if stop() closes the line
            int rate = (int) l.getFormat().getSampleRate();
            int durationMs = Math.max(250, (int) ((sampleCount / (float) (rate > 0 ? rate : 24000)) * 1000) + 150);
            HelperVoice.markSpeaking(durationMs);
        } catch (RuntimeException closedUnderUs) {
            if (!stopped) {
                throw closedUnderUs;
            }
            return;
        }
        writtenBytes += out.length;
        if (!started && writtenBytes >= prebufferBytes) {
            startLine();
        }
    }

    @Override
    public void onComplete() {
        SourceDataLine l = line;
        if (l != null && !stopped) {
            startLine(); // short lines never reach the prebuffer size
            // Wait until everything queued has been played, without line.drain() (it can block forever on a stopped line).
            long deadline = System.nanoTime() + DRAIN_TIMEOUT_MS * 1_000_000L;
            try {
                while (!stopped && l.available() < l.getBufferSize() && System.nanoTime() < deadline) {
                    Thread.sleep(10);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        finish();
    }

    @Override
    public void onError(Exception error) {
        finish();
    }

    /** Cuts the audio off immediately. Safe from any thread, and safe to call more than once. */
    public void stop() {
        stopped = true;
        finish();
    }

    public boolean isEnded() {
        return ended;
    }

    /** Milliseconds of audio handed to the device so far (for tests and diagnostics). */
    public long writtenMillis(int sampleRate) {
        return writtenBytes * 1000L / (sampleRate * 4L);
    }

    /** System.nanoTime() when the first PCM bytes arrived, or 0 if none did. */
    public long firstAudioNanos() {
        return firstAudioNanos;
    }

    private void startLine() {
        synchronized (lock) {
            if (!started && line != null && !stopped) {
                line.start();
                started = true;
            }
        }
    }

    private void finish() {
        synchronized (lock) {
            if (line != null) {
                line.stop();
                line.flush();
                line.close();
                line = null;
            }
            ended = true;
        }
    }

    private static int bytesFor(int sampleRate, int millis) {
        return (int) ((long) sampleRate * 4 * millis / 1000) & ~3;
    }
}
