package com.blighteddreams.helper.ai;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

/**
 * One ongoing chat between the player and the companion: keeps the last few turns, sends each question with the live
 * state, and turns the streamed answer into sentences (with moods) as they complete. JDK-only.
 *
 * Only one question is answered at a time: asking again cancels the one in flight. History keeps the player's words and
 * what she actually said (as "[mood] sentence" text, so the model keeps using the tags); the state block is not kept.
 */
public final class Conversation {

    /** Where the answer goes. Called on provider threads, never the game thread. */
    public interface Output {
        /** A sentence is ready to be spoken and shown. */
        void segment(ReplyStreamer.Segment segment);

        /** The answer is complete. {@code spokenAnyText} is false if the model said nothing speakable. */
        void finished(boolean spokenAnyText);

        /** The model could not answer. The message is readable by the player. */
        void failed(String message);

        /**
         * The model asked her body to do something, e.g. the body of a {@code <<ACT {"verb":"mine"}>>} block. The block
         * is not spoken. Default is to ignore it.
         */
        default void action(String body) {
        }
    }

    private final BrainProvider brain;
    private final Supplier<String> systemPrompt;
    private final int maxTurns;
    private final Set<String> moods;
    private final String defaultMood;
    private final String speakerName;

    private final List<ChatMessage> history = new ArrayList<>(); // alternating user / assistant
    private BrainProvider.Handle current;
    private long generation;

    /**
     * @param systemPrompt evaluated for every question, so config and knowledge changes apply at once
     * @param maxTurns     how many question/answer pairs to remember
     */
    public Conversation(BrainProvider brain, Supplier<String> systemPrompt, int maxTurns,
                        Set<String> moods, String defaultMood, String speakerName) {
        this.brain = brain;
        this.systemPrompt = systemPrompt;
        this.maxTurns = Math.max(0, maxTurns);
        this.moods = moods;
        this.defaultMood = defaultMood;
        this.speakerName = speakerName;
    }

    /** Asks a question. Any answer still being written is cancelled first. */
    public synchronized void ask(String playerText, String state, Output out) {
        dispatch(HelperPrompt.userTurn(state, playerText), playerText, out);
    }

    /** Proactively reacts to an ambient in-game event. Any answer still being written is cancelled first. */
    public synchronized void react(String eventCue, String state, Output out) {
        dispatch(HelperPrompt.reactionTurn(state, eventCue), "[Moment: " + eventCue + "]", out);
    }

    private synchronized void dispatch(String promptText, String memoryUserLabel, Output out) {
        cancel();
        long mine = ++generation;
        List<ChatMessage> messages = new ArrayList<>(history.size() + 2);
        messages.add(ChatMessage.system(systemPrompt.get()));
        messages.addAll(history);
        messages.add(ChatMessage.user(promptText));

        StringBuilder said = new StringBuilder();
        boolean[] spoke = {false};
        ReplyStreamer streamer = new ReplyStreamer(moods, defaultMood, speakerName, seg -> {
            if (!isCurrent(mine)) {
                return;
            }
            spoke[0] = true;
            appendSaid(said, seg);
            out.segment(seg);
        }, body -> {
            if (isCurrent(mine)) {
                out.action(body);
            }
        });

        current = brain.chat(messages, new BrainProvider.Listener() {
            @Override
            public void onToken(String text) {
                if (isCurrent(mine)) {
                    streamer.accept(text);
                }
            }

            @Override
            public void onComplete() {
                if (!isCurrent(mine)) {
                    return;
                }
                streamer.finish();
                if (spoke[0]) {
                    remember(memoryUserLabel, said.toString().trim());
                }
                out.finished(spoke[0]);
            }

            @Override
            public void onError(Exception error) {
                if (isCurrent(mine)) {
                    out.failed(error.getMessage() == null ? error.toString() : error.getMessage());
                }
            }
        });
    }

    /** Stops the answer in flight, if any. */
    public synchronized void cancel() {
        generation++; // anything the old request still delivers is ignored
        if (current != null) {
            current.cancel();
            current = null;
        }
    }

    /** Forgets the conversation so far. */
    public synchronized void forget() {
        cancel();
        history.clear();
    }

    public synchronized int turns() {
        return history.size() / 2;
    }

    public synchronized boolean isAnswering() {
        return current != null && !current.isFinished();
    }

    private synchronized boolean isCurrent(long id) {
        return id == generation;
    }

    private static void appendSaid(StringBuilder said, ReplyStreamer.Segment seg) {
        if (said.length() > 0) {
            said.append(' ');
        }
        said.append('[').append(seg.mood()).append("] ").append(seg.text()); // one tag per sentence: easy for the model to read back
    }

    private synchronized void remember(String playerText, String answer) {
        if (maxTurns == 0 || answer.isEmpty()) {
            return;
        }
        history.add(ChatMessage.user(playerText));
        history.add(ChatMessage.assistant(answer));
        while (history.size() > maxTurns * 2) {
            history.remove(0);
            history.remove(0);
        }
    }
}
