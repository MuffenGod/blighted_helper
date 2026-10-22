package com.blighteddreams.helper.client.voice;

import com.blighteddreams.helper.ai.ReplyStreamer;
import com.blighteddreams.helper.ai.TtsProvider;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.SourceDataLine;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.DoubleSupplier;

/**
 * Speaks a reply sentence by sentence. Each sentence is sent to the voice server the moment it is enqueued (so the next
 * one is being generated while the current one plays), and the audio is played strictly in order on one audio line
 * that stays open between sentences. If a sentence gets no audio (voice server down, line unavailable) its text is
 * still announced in order, so the player always sees the reply. JDK-only.
 */
public final class SpeechQueue implements AutoCloseable {
    private static final int LINE_BUFFER_MS = 400;
    private static final int PREBUFFER_MS = 160;
    private static final long LINE_IDLE_CLOSE_NANOS = 400_000_000L;
    private static final long FIRST_AUDIO_TIMEOUT_NANOS = 20_000_000_000L;

    public interface Listener {
        /** A sentence is about to be heard ({@code withVoice}) or, if it got no audio, is simply being shown. Player thread. */
        void segmentStarted(ReplyStreamer.Segment segment, boolean withVoice);

        /** Something went wrong with the audio. May repeat; the caller should rate-limit what it shows. */
        void problem(String message);

        /** Everything enqueued so far has been played. */
        void idle();
    }

    /** One sentence on its way: the audio chunks the provider has delivered so far. */
    private static final class Item implements TtsProvider.Sink {
        static final byte[] END = new byte[0];
        final ReplyStreamer.Segment seg;
        final LinkedBlockingQueue<byte[]> chunks = new LinkedBlockingQueue<>();
        volatile int rate = 24000;
        volatile Exception error;
        volatile boolean cancelled;
        volatile TtsProvider.Handle handle;

        Item(ReplyStreamer.Segment seg) {
            this.seg = seg;
        }

        @Override public void onFormat(int sampleRate) { rate = sampleRate; }
        @Override public void onPcm(byte[] data, int offset, int length) {
            byte[] copy = new byte[length];
            System.arraycopy(data, offset, copy, 0, length);
            chunks.add(copy);
        }
        @Override public void onComplete() { chunks.add(END); }
        @Override public void onError(Exception e) { error = e; chunks.add(END); }

        void cancel() {
            cancelled = true;
            TtsProvider.Handle h = handle;
            if (h != null) {
                h.cancel();
            }
        }
    }

    private final TtsProvider tts; // null = text only
    private final String voice;
    private final DoubleSupplier gain;
    private final java.util.function.Supplier<float[]> spatialGains; // {leftGain, rightGain}, or null for mono
    private final Listener listener;
    private final LinkedBlockingQueue<Item> pending = new LinkedBlockingQueue<>();
    private final Object lock = new Object();
    private final Thread thread;

    private volatile boolean closed;
    private volatile long generation;
    private volatile boolean busy;
    private volatile Item playing;

    // audio line state, guarded by lock (writes to the line itself happen outside the lock)
    private volatile SourceDataLine line;
    private volatile VoiceDsp dsp;
    private int lineRate;
    private int lineChannels = 1;
    private boolean lineStarted;
    private long lineBytes;
    private int prebufferBytes;
    private volatile long lastWriteNanos = System.nanoTime();

    /**
     * @param tts  the voice backend, or null to show text only
     * @param gain linear volume, read for every chunk
     */
    public SpeechQueue(TtsProvider tts, String voice, DoubleSupplier gain, Listener listener) {
        this(tts, voice, gain, null, listener);
    }

    /**
     * @param tts          the voice backend, or null to show text only
     * @param gain         linear volume, read for every chunk
     * @param spatialGains optional 3D stereo panning gains {leftGain, rightGain}; when non-null, output is 2-channel stereo
     */
    public SpeechQueue(TtsProvider tts, String voice, DoubleSupplier gain,
                       java.util.function.Supplier<float[]> spatialGains, Listener listener) {
        this.tts = tts;
        this.voice = voice;
        this.gain = gain;
        this.spatialGains = spatialGains;
        this.listener = listener;
        this.thread = new Thread(this::run, "blighted-speech");
        this.thread.setDaemon(true);
        this.thread.start();
    }

    /** Queues a sentence. Returns at once. */
    public void enqueue(ReplyStreamer.Segment segment) {
        Item item = new Item(segment);
        busy = true;
        if (tts == null) {
            item.chunks.add(Item.END);
        } else {
            try {
                String clean = HelperVoice.sanitize(segment.text());
                item.handle = tts.speak(new TtsProvider.Request(clean, voice, segment.mood()), item);
                if (item.cancelled) {
                    item.handle.cancel();
                }
            } catch (RuntimeException e) {
                item.error = e;
                item.chunks.add(Item.END);
            }
        }
        pending.add(item);
    }

    /** Stops the audio now and forgets everything queued. */
    public void clear() {
        synchronized (lock) {
            generation++;
            Item p = playing;
            if (p != null) {
                p.cancel();
            }
            for (Item i : pending) {
                i.cancel();
            }
            pending.clear();
            closeLineLocked();
            busy = false;
        }
    }

    public boolean isBusy() {
        return busy;
    }

    @Override
    public void close() {
        closed = true;
        clear();
        thread.interrupt();
    }

    // ------------------------------------------------------------------------------------------------ player thread

    private void run() {
        while (!closed) {
            try {
                Item item = pending.poll(150, TimeUnit.MILLISECONDS);
                if (item == null) {
                    housekeeping();
                    continue;
                }
                playing = item;
                try {
                    play(item, generation);
                } finally {
                    playing = null;
                }
            } catch (InterruptedException e) {
                return;
            } catch (RuntimeException e) {
                listener.problem("speech playback failed: " + e);
            }
        }
    }

    private void play(Item item, long myGen) throws InterruptedException {
        boolean announced = false;
        long waitStart = System.nanoTime();
        while (!closed && myGen == generation) {
            byte[] c = item.chunks.poll(100, TimeUnit.MILLISECONDS);
            if (c == null) {
                if (!announced && System.nanoTime() - waitStart > FIRST_AUDIO_TIMEOUT_NANOS) {
                    item.error = new java.io.IOException("the voice server took too long to answer");
                    item.cancel();
                    break;
                }
                continue;
            }
            if (c == Item.END) {
                break;
            }
            if (!announced) {
                announced = true;
                openLine(item.rate);
                listener.segmentStarted(item.seg, true);
            }
            write(c, myGen);
        }
        if (myGen != generation || closed) {
            return; // cleared while playing: say nothing more
        }
        if (!announced) {
            listener.segmentStarted(item.seg, false);
            if (item.error != null) {
                listener.problem(item.error.getMessage() == null ? item.error.toString() : item.error.getMessage());
            }
        } else {
            startLineIfNeeded(); // a short sentence may not have reached the prebuffer size
        }
    }

    /** When nothing is queued: close the line once its buffer has drained, then report idle. */
    private void housekeeping() {
        if (!busy || !pending.isEmpty() || playing != null) {
            return;
        }
        synchronized (lock) {
            SourceDataLine l = line;
            if (l != null) {
                boolean drained = l.available() >= l.getBufferSize();
                if (!drained || System.nanoTime() - lastWriteNanos < LINE_IDLE_CLOSE_NANOS) {
                    return;
                }
                closeLineLocked();
            }
            busy = false;
        }
        listener.idle();
    }

    // ------------------------------------------------------------------------------------------------ audio line

    private void openLine(int rate) {
        synchronized (lock) {
            int channels = (spatialGains != null) ? 2 : 1;
            if (line != null && lineRate == rate && lineChannels == channels) {
                return;
            }
            closeLineLocked();
            AudioFormat format = new AudioFormat(rate, 16, channels, true, false);
            try {
                SourceDataLine l = (SourceDataLine) AudioSystem.getLine(new DataLine.Info(SourceDataLine.class, format));
                l.open(format, bytesFor(rate, channels, LINE_BUFFER_MS));
                line = l;
                lineRate = rate;
                lineChannels = channels;
                dsp = new VoiceDsp(rate);
                lineStarted = false;
                lineBytes = 0;
                prebufferBytes = bytesFor(rate, channels, PREBUFFER_MS);
            } catch (LineUnavailableException | IllegalArgumentException e) {
                listener.problem("Cannot open an audio device for her voice: " + e.getMessage());
            }
        }
    }

    private void write(byte[] chunk, long myGen) {
        SourceDataLine l = line;
        if (l == null || myGen != generation) {
            return;
        }
        byte[] source = chunk;
        VoiceDsp processor = dsp;
        if (processor != null) {
            source = processor.process(chunk, 0, chunk.length, HelperVoice.environment());
        }
        byte[] out;
        double baseGain = gain.getAsDouble();

        if (lineChannels == 2) {
            float leftMult = (float) baseGain;
            float rightMult = (float) baseGain;
            if (spatialGains != null) {
                float[] sp = spatialGains.get();
                if (sp != null && sp.length >= 2) {
                    leftMult *= sp[0];
                    rightMult *= sp[1];
                }
            }
            int sampleCount = source.length / 2;
            out = new byte[sampleCount * 4];
            int outIdx = 0;
            for (int i = 0; i < source.length; i += 2) {
                int s = (short) ((source[i] & 0xFF) | (source[i + 1] << 8));
                int leftSample = Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, Math.round(s * leftMult)));
                int rightSample = Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, Math.round(s * rightMult)));

                out[outIdx++] = (byte) leftSample;
                out[outIdx++] = (byte) (leftSample >> 8);
                out[outIdx++] = (byte) rightSample;
                out[outIdx++] = (byte) (rightSample >> 8);
            }
        } else {
            out = source;
            if (baseGain != 1.0) {
                out = new byte[source.length];
                for (int i = 0; i + 1 < source.length; i += 2) {
                    int s = (short) ((source[i] & 0xff) | (source[i + 1] << 8));
                    s = (int) Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, Math.round(s * baseGain)));
                    out[i] = (byte) s;
                    out[i + 1] = (byte) (s >> 8);
                }
            }
        }

        try {
            l.write(out, 0, out.length); // blocks while the line buffer is full; returns early if clear() closes the line
            int rate = lineRate > 0 ? lineRate : 24000;
            int durationMs = Math.max(250, (int) (((source.length / 2.0) / (float) rate) * 1000) + 150);
            HelperVoice.markSpeaking(durationMs);
        } catch (RuntimeException closedUnderUs) {
            if (myGen == generation) {
                throw closedUnderUs;
            }
            return;
        }
        lastWriteNanos = System.nanoTime();
        synchronized (lock) {
            lineBytes += out.length;
            if (!lineStarted && line == l && lineBytes >= prebufferBytes) {
                l.start();
                lineStarted = true;
            }
        }
    }

    private void startLineIfNeeded() {
        synchronized (lock) {
            if (line != null && !lineStarted) {
                line.start();
                lineStarted = true;
            }
        }
    }

    private void closeLineLocked() {
        if (line != null) {
            line.stop();
            line.flush();
            line.close();
            line = null;
        }
    }

    private static int bytesFor(int sampleRate, int channels, int millis) {
        return (int) ((long) sampleRate * channels * 2 * millis / 1000) & ~(channels * 2 - 1);
    }
}
