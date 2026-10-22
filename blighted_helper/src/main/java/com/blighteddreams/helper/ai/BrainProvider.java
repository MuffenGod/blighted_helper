package com.blighteddreams.helper.ai;

import java.util.List;

/**
 * A language model that answers a chat and streams the answer as it is written (design/HELPER_PLAN.md sections 4 and
 * 6d). JDK-only on purpose, so providers can be tested without starting Minecraft.
 */
public interface BrainProvider extends AutoCloseable {

    /**
     * Receives the answer on a provider thread, never the game thread. Calls arrive in order: any number of
     * {@link #onToken}, then exactly one of {@link #onComplete} or {@link #onError}. After {@link Handle#cancel()} no
     * further calls are made.
     */
    interface Listener {
        /** A piece of the answer text (may be a word fragment). */
        void onToken(String text);

        void onComplete();

        /** The message is meant to be readable by the player. */
        void onError(Exception error);
    }

    /** One running request. */
    interface Handle {
        /** Stops the request as soon as possible. Safe from any thread, repeatedly. */
        void cancel();

        boolean isFinished();
    }

    /** Short description for status messages, never containing a key. */
    String name();

    /** Starts the chat and returns at once; the answer arrives through {@code listener}. */
    Handle chat(List<ChatMessage> messages, Listener listener);

    @Override
    void close();
}
