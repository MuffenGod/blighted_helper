package com.blighteddreams.helper.ai;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * The one list of mood tags (design/HELPER_PLAN.md section 6h). The model starts a sentence group with {@code [smug]},
 * {@link ReplyStreamer} turns the tag into a {@link ReplyStreamer.Segment} mood, and the voice server gets the name in its
 * {@code mood} field. These ten names are the ones in {@code design/tools/voice-server/voice/emily/voice.json}; a mood the
 * voice server does not know is answered with its {@code default_mood}, so a new name here is always safe to send.
 * JDK-only.
 */
public final class HelperMoods {
    private HelperMoods() {}

    /** Used until the model sets a mood, and for any name that is not in {@link #all()}. */
    public static final String DEFAULT = "calm";

    private static final List<String> ALL = List.of(
            "calm", "cheerful", "worried", "battle", "whisper", "smug", "tease", "flirt", "mock", "sincere");
    private static final Set<String> SET = Set.copyOf(ALL);
    /** Mood to a few words of when to use it; the prompt prints these next to the tags. */
    private static final Map<String, String> HINT = Map.of(
            "calm", "default",
            "cheerful", "bright",
            "worried", "afraid for them",
            "battle", "fighting",
            "whisper", "quiet",
            "smug", "you were right",
            "tease", "needling",
            "flirt", "low, sly",
            "mock", "ribbing",
            "sincere", "honest, soft");

    /** Every mood, in prompt order. */
    public static List<String> all() {
        return ALL;
    }

    /** The same names as a set, for {@link ReplyStreamer}. */
    public static Set<String> set() {
        return SET;
    }

    public static boolean isKnown(String mood) {
        return mood != null && SET.contains(mood.trim().toLowerCase(Locale.ROOT));
    }

    /** The mood to use for a name: itself (lower case) if known, otherwise {@link #DEFAULT}. */
    public static String normalize(String mood) {
        return isKnown(mood) ? mood.trim().toLowerCase(Locale.ROOT) : DEFAULT;
    }

    /** A few words on when to use the mood, or null if there is none. */
    public static String hint(String mood) {
        return HINT.get(mood);
    }
}
