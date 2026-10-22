package com.blighteddreams.helper.ai;

import java.util.Locale;
import java.util.Set;
import java.util.function.Consumer;
import java.util.regex.Pattern;

/**
 * Turns the token stream of a language model's answer into sentences to speak, each with a mood.
 *
 * <ul>
 *   <li>Mood tags such as {@code [worried]} set the mood of the text that follows and are never spoken. Other bracketed
 *       words ({@code [smiles]}) are dropped too. A tag may arrive split across chunks.</li>
 *   <li>{@code <think>...</think>} blocks (reasoning models) are dropped.</li>
 *   <li>Stage directions in asterisks, markdown marks, emoji and a leading "Name:" are removed.</li>
 *   <li>A sentence ends at {@code . ! ? ...} followed by whitespace, or at a line break. Fragments shorter than a few
 *       characters ("Oh.") are joined to the next sentence of the same mood. Within the very first sentence a long run
 *       is cut at a comma so speech can start sooner; any sentence over ~180 characters is cut at a comma too.</li>
 * </ul>
 *
 * Not thread safe: feed it from one thread (the provider's).
 */
public final class ReplyStreamer {

    private static final Pattern BRACKET_TAG = Pattern.compile("\\[[^\\]]*\\]");
    private static final Pattern PARENTHETICAL = Pattern.compile("\\([^)]*\\)");

    /** One piece to speak. {@code text} contains clean spoken text for TTS; {@code displayText} ensures chat subtitles are clean. */
    public record Segment(String text, String mood) {
        public String displayText() {
            String clean = BRACKET_TAG.matcher(text).replaceAll("").trim();
            return clean.isEmpty() ? text : clean;
        }
    }

    public interface Out {
        void segment(Segment segment);
    }

    private static final int MIN_SEGMENT_CHARS = 8;
    private static final int FIRST_SOFT_BREAK_CHARS = 60;
    private static final int LONG_SENTENCE_CHARS = 180;
    private static final String THINK_OPEN = "<think>";
    private static final String THINK_CLOSE = "</think>";
    private static final String ACTION_OPEN = "<<ACT";
    private static final String ACTION_CLOSE = ">>";
    private static final Pattern STAGE_DIRECTION = Pattern.compile("\\*[^*]{0,120}\\*");
    private static final Pattern MARKDOWN = Pattern.compile("[*_`#>~]+");
    private static final Pattern LIST_MARKER = Pattern.compile("^[-•]\\s+");
    private static final Pattern HAS_WORD = Pattern.compile(".*[\\p{L}\\p{N}].*", Pattern.DOTALL);
    private static final Pattern TAG_LIKE = Pattern.compile("[a-z][a-z _-]{0,20}");

    private final Set<String> moods;
    private final String defaultMood;
    private final Out out;
    private final Consumer<String> action;
    private final Pattern speakerPrefix;

    private final StringBuilder in = new StringBuilder();
    private final StringBuilder sentence = new StringBuilder();
    private boolean inThink;
    private String curMood;
    private String sentenceMood;
    private boolean endPending; // the last characters were sentence-ending punctuation; the next character decides
    private String heldText;
    private String heldMood;
    private boolean emittedAny;
    private boolean sawText;

    /**
     * @param moods       the mood names the voice knows (lower case)
     * @param defaultMood used until the model sets one
     * @param speakerName the character's name, so a leading "Emily:" can be removed; may be null
     */
    public ReplyStreamer(Set<String> moods, String defaultMood, String speakerName, Out out) {
        this(moods, defaultMood, speakerName, out, null);
    }

    /**
     * @param action receives the body of a {@code <<ACT {...}>>} block. The block itself is never spoken, so the model
     *               can act in the world without breaking the voice performance.
     */
    public ReplyStreamer(Set<String> moods, String defaultMood, String speakerName, Out out, Consumer<String> action) {
        this.moods = moods;
        this.defaultMood = defaultMood;
        this.curMood = defaultMood;
        this.out = out;
        this.action = action;
        this.speakerPrefix = speakerName == null || speakerName.isBlank() ? null
                : Pattern.compile("^\\s*" + Pattern.quote(speakerName.trim()) + "\\s*:\\s*", Pattern.CASE_INSENSITIVE);
    }

    /** Feeds the next piece of the answer. */
    public void accept(String chunk) {
        in.append(chunk);
        drain(false);
    }

    /** The answer is complete: speak whatever is left. */
    public void finish() {
        drain(true);
        flushSentence();
        flushHeld();
    }

    // ------------------------------------------------------------------------------------------------ scanning

    private void drain(boolean last) {
        int p = 0;
        while (p < in.length()) {
            if (inThink) {
                int end = in.indexOf(THINK_CLOSE, p);
                if (end < 0) {
                    p = Math.max(p, in.length() - (THINK_CLOSE.length() - 1)); // keep a possible partial closing tag
                    break;
                }
                p = end + THINK_CLOSE.length();
                inThink = false;
                continue;
            }
            char c = in.charAt(p);
            if (c == '<') {
                int rem = in.length() - p;
                if (startsWithIgnoreCase(in, p, ACTION_OPEN)) {
                    int end = in.indexOf(ACTION_CLOSE, p + ACTION_OPEN.length());
                    if (end < 0) {
                        if (!last) {
                            break; // wait for the rest of the action block
                        }
                        p = in.length();
                        break;
                    }
                    String body = in.substring(p + ACTION_OPEN.length(), end).trim();
                    if (action != null && !body.isEmpty()) {
                        action.accept(body);
                    }
                    p = end + ACTION_CLOSE.length();
                    continue;
                }
                String prefix = in.substring(p);
                if (!last && ACTION_OPEN.regionMatches(true, 0, prefix, 0, prefix.length())) {
                    break; // a split "<<ACT" prefix
                }
                if (in.indexOf(THINK_OPEN, p) == p) {
                    inThink = true;
                    p += THINK_OPEN.length();
                    continue;
                }
                if (in.indexOf(THINK_CLOSE, p) == p) { // a stray closing tag: the thinking started before we were listening
                    p += THINK_CLOSE.length();
                    continue;
                }
                if (!last && rem < THINK_CLOSE.length()) {
                    String tail = in.substring(p);
                    if (THINK_OPEN.startsWith(tail) || THINK_CLOSE.startsWith(tail)) {
                        break; // maybe the start of a tag: wait for more
                    }
                }
                append(c);
                p++;
                continue;
            }
            if (c == '[') {
                int close = in.indexOf("]", p + 1);
                if (close < 0) {
                    if (!last && in.length() - p < 24) {
                        break; // the tag may still be arriving
                    }
                    append(c);
                    p++;
                    continue;
                }
                String tag = in.substring(p + 1, close).trim().toLowerCase(Locale.ROOT);
                if (TAG_LIKE.matcher(tag).matches()) {
                    if (moods.contains(tag)) {
                        setMood(tag);
                        p = close + 1;
                        continue;
                    }
                    // any other tag, such as [chuckle], [laughing], [giggle], [smiles], is dropped completely
                    p = close + 1;
                    continue;
                }
                append(c); // "[1]" and the like are ordinary text
                p++;
                continue;
            }
            append(c);
            p++;
        }
        in.delete(0, p);
    }

    private static boolean startsWithIgnoreCase(CharSequence text, int offset, String prefix) {
        if (offset < 0 || offset + prefix.length() > text.length()) return false;
        for (int i = 0; i < prefix.length(); i++) {
            if (Character.toLowerCase(text.charAt(offset + i)) != Character.toLowerCase(prefix.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    private void setMood(String mood) {
        if (sentence.length() > 0) {
            flushSentence(); // a mood change starts a new piece
        }
        curMood = mood;
    }

    private void append(char c) {
        if (endPending) {
            if (Character.isWhitespace(c)) {
                flushSentence();
                return;
            }
            if (c == '"' || c == '\'' || c == ')' || c == '”' || c == '’') {
                sentence.append(c); // the closing quote of a quoted sentence
                return;
            }
            if (isTerminator(c)) {
                sentence.append(c); // "..." and "?!"
                return;
            }
            endPending = false; // "3.5", "e.g." carry on
        }
        if (c == '\n' || c == '\r') {
            flushSentence();
            return;
        }
        if (sentence.length() == 0) {
            if (Character.isWhitespace(c)) {
                return;
            }
            sentenceMood = curMood;
        }
        sentence.append(c);
        if (isTerminator(c)) {
            endPending = true;
        } else if ((c == ',' || c == ';' || c == ':')
                && ((!emittedAny && heldText == null && sentence.length() >= FIRST_SOFT_BREAK_CHARS) || sentence.length() >= LONG_SENTENCE_CHARS)) {
            endPending = true; // a long run: cut it here so speech can start (or not drift)
        }
    }

    private static boolean isTerminator(char c) {
        return c == '.' || c == '!' || c == '?' || c == '…' || c == '。' || c == '！' || c == '？';
    }

    // ------------------------------------------------------------------------------------------------ output

    private void flushSentence() {
        String text = clean(sentence.toString());
        sentence.setLength(0);
        endPending = false;
        if (text.isEmpty()) {
            return;
        }
        if (!sawText) {
            sawText = true;
            if (speakerPrefix != null) {
                text = speakerPrefix.matcher(text).replaceFirst("").trim();
                if (text.isEmpty()) {
                    return;
                }
            }
        }
        emit(text, sentenceMood != null ? sentenceMood : defaultMood);
    }

    private void emit(String text, String mood) {
        if (heldText != null) {
            if (heldMood.equals(mood)) {
                text = heldText + " " + text;
                heldText = null;
            } else {
                flushHeld();
            }
        }
        if (text.length() < MIN_SEGMENT_CHARS) {
            heldText = text; // too short to be worth a request of its own: join it to the next sentence
            heldMood = mood;
            return;
        }
        out.segment(new Segment(text, mood));
        emittedAny = true;
    }

    private void flushHeld() {
        if (heldText != null) {
            String t = heldText;
            heldText = null;
            out.segment(new Segment(t, heldMood));
            emittedAny = true;
        }
    }

    /** Removes what a speech synthesizer should not read aloud. Returns "" if nothing speakable is left. */
    static String clean(String raw) {
        String t = STAGE_DIRECTION.matcher(raw).replaceAll(" ");
        t = BRACKET_TAG.matcher(t).replaceAll(" ");
        t = PARENTHETICAL.matcher(t).replaceAll(" ");
        t = MARKDOWN.matcher(t).replaceAll("");
        StringBuilder sb = new StringBuilder(t.length());
        t.codePoints().forEach(cp -> {
            boolean emoji = (cp >= 0x1F000 && cp <= 0x1FAFF) || (cp >= 0x2600 && cp <= 0x27BF)
                    || cp == 0xFE0F || cp == 0x200D || (cp >= 0x2190 && cp <= 0x21FF) || (cp >= 0x2B00 && cp <= 0x2BFF);
            if (!emoji) {
                sb.appendCodePoint(cp);
            }
        });
        t = sb.toString().replaceAll("\\s+", " ").trim();
        t = t.replaceAll("\\s+([.,!?;:\u2026])", "$1"); // "back ." left by a removed emoji
        t = LIST_MARKER.matcher(t).replaceFirst("");
        return HAS_WORD.matcher(t).matches() ? t : "";
    }
}
