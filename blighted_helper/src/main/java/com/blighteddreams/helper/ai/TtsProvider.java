package com.blighteddreams.helper.ai;

import java.util.Objects;
import java.util.function.Consumer;

/**
 * A text-to-speech backend (design/HELPER_PLAN.md sections 4 and 6c). Implementations stream raw audio and never block
 * the caller. JDK-only on purpose, so providers can be tested without starting Minecraft.
 */
public interface TtsProvider extends AutoCloseable {

    /**
     * What to say and how.
     *
     * @param voice provider-specific voice name, for example "emily"
     * @param mood  provider-specific mood, for example "worried"; null means the voice's default mood
     */
    record Request(String text, String voice, String mood) {
        public Request {
            Objects.requireNonNull(text, "text");
            Objects.requireNonNull(voice, "voice");
        }
    }

    /**
     * Receives the audio on a provider thread, never the game thread. Calls arrive in order: {@link #onFormat}, any
     * number of {@link #onPcm}, then exactly one of {@link #onComplete} or {@link #onError}. After
     * {@link Handle#cancel()} no further {@code onComplete}/{@code onError} calls are made.
     */
    interface Sink {
        /** Audio is 16-bit signed little-endian mono at this rate. */
        void onFormat(int sampleRate);

        /** Whole 16-bit samples only: {@code length} is always even. */
        void onPcm(byte[] data, int offset, int length);

        void onComplete();

        void onError(Exception error);
    }

    /** One running speech request. */
    interface Handle {
        /** Stops the request (and the backend's work for it) as soon as possible. Safe to call from any thread, repeatedly. */
        void cancel();

        boolean isFinished();
    }

    /** Result of a health check. */
    record Health(boolean ready, String detail) {}

    String name();

    /** Starts speaking and returns at once; audio arrives through {@code sink}. */
    Handle speak(Request request, Sink sink);

    /** The outcome of the latest health check or request. Does not touch the network. */
    boolean isAvailable();

    /** Asks the backend whether it is ready; the answer arrives on a provider thread. */
    void checkHealth(Consumer<Health> callback);

    /** Cancels everything in flight and releases threads. */
    @Override
    void close();
}
