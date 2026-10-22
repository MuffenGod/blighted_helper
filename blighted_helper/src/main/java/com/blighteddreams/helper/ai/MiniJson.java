package com.blighteddreams.helper.ai;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A small JSON reader and string quoter, so the AI code stays JDK-only and testable without Minecraft. Objects become
 * {@code Map<String,Object>}, arrays {@code List<Object>}, numbers {@code Double}; strings, booleans and null are as
 * expected. Not a general-purpose library: it accepts what OpenAI-style APIs send.
 */
public final class MiniJson {
    private final String s;
    private int i;

    private MiniJson(String s) {
        this.s = s;
    }

    /** Parses one JSON value. Throws IllegalArgumentException with the position on bad input. */
    public static Object parse(String text) {
        MiniJson p = new MiniJson(text);
        p.ws();
        Object v = p.value();
        p.ws();
        if (p.i != p.s.length()) {
            throw p.error("unexpected trailing text");
        }
        return v;
    }

    /** Walks maps by String keys and lists by Integer indexes. Returns null if any step is missing. */
    @SuppressWarnings("unchecked")
    public static Object path(Object root, Object... steps) {
        Object cur = root;
        for (Object step : steps) {
            if (cur instanceof Map<?, ?> m && step instanceof String key) {
                cur = ((Map<String, Object>) m).get(key);
            } else if (cur instanceof List<?> l && step instanceof Integer idx) {
                cur = idx >= 0 && idx < l.size() ? l.get(idx) : null;
            } else {
                return null;
            }
        }
        return cur;
    }

    /** {@link #path} as a String, or null if the value is missing, null, or not a string. */
    public static String string(Object root, Object... steps) {
        return path(root, steps) instanceof String str ? str : null;
    }

    /** Appends {@code s} as a JSON string literal, quotes included. */
    public static void quote(StringBuilder sb, String s) {
        sb.append('"');
        for (int k = 0; k < s.length(); k++) {
            char c = s.charAt(k);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        sb.append('"');
    }

    // ------------------------------------------------------------------------------------------------ parser

    private Object value() {
        if (i >= s.length()) {
            throw error("unexpected end");
        }
        char c = s.charAt(i);
        switch (c) {
            case '{':
                return object();
            case '[':
                return array();
            case '"':
                return string();
            case 't':
                return literal("true", Boolean.TRUE);
            case 'f':
                return literal("false", Boolean.FALSE);
            case 'n':
                return literal("null", null);
            default:
                return number();
        }
    }

    private Map<String, Object> object() {
        Map<String, Object> m = new LinkedHashMap<>();
        i++; // {
        ws();
        if (peek() == '}') {
            i++;
            return m;
        }
        while (true) {
            ws();
            if (peek() != '"') {
                throw error("expected a key");
            }
            String key = string();
            ws();
            expect(':');
            ws();
            m.put(key, value());
            ws();
            char c = next();
            if (c == '}') {
                return m;
            }
            if (c != ',') {
                throw error("expected , or }");
            }
        }
    }

    private List<Object> array() {
        List<Object> l = new ArrayList<>();
        i++; // [
        ws();
        if (peek() == ']') {
            i++;
            return l;
        }
        while (true) {
            ws();
            l.add(value());
            ws();
            char c = next();
            if (c == ']') {
                return l;
            }
            if (c != ',') {
                throw error("expected , or ]");
            }
        }
    }

    private String string() {
        i++; // opening quote
        StringBuilder sb = new StringBuilder();
        while (true) {
            if (i >= s.length()) {
                throw error("unterminated string");
            }
            char c = s.charAt(i++);
            if (c == '"') {
                return sb.toString();
            }
            if (c != '\\') {
                sb.append(c);
                continue;
            }
            if (i >= s.length()) {
                throw error("unterminated escape");
            }
            char e = s.charAt(i++);
            switch (e) {
                case '"' -> sb.append('"');
                case '\\' -> sb.append('\\');
                case '/' -> sb.append('/');
                case 'b' -> sb.append('\b');
                case 'f' -> sb.append('\f');
                case 'n' -> sb.append('\n');
                case 'r' -> sb.append('\r');
                case 't' -> sb.append('\t');
                case 'u' -> {
                    if (i + 4 > s.length()) {
                        throw error("short \\u escape");
                    }
                    try {
                        sb.append((char) Integer.parseInt(s.substring(i, i + 4), 16)); // surrogate pairs arrive as two escapes
                    } catch (NumberFormatException ex) {
                        throw error("bad \\u escape");
                    }
                    i += 4;
                }
                default -> throw error("bad escape \\" + e);
            }
        }
    }

    private Object number() {
        int start = i;
        while (i < s.length() && "+-0123456789.eE".indexOf(s.charAt(i)) >= 0) {
            i++;
        }
        if (start == i) {
            throw error("unexpected character '" + s.charAt(i) + "'");
        }
        try {
            return Double.parseDouble(s.substring(start, i));
        } catch (NumberFormatException e) {
            throw error("bad number");
        }
    }

    private Object literal(String word, Object value) {
        if (!s.startsWith(word, i)) {
            throw error("unexpected token");
        }
        i += word.length();
        return value;
    }

    private void ws() {
        while (i < s.length() && Character.isWhitespace(s.charAt(i))) {
            i++;
        }
    }

    private char peek() {
        return i < s.length() ? s.charAt(i) : '\0';
    }

    private char next() {
        if (i >= s.length()) {
            throw error("unexpected end");
        }
        return s.charAt(i++);
    }

    private void expect(char c) {
        if (next() != c) {
            i--;
            throw error("expected '" + c + "'");
        }
    }

    private IllegalArgumentException error(String msg) {
        return new IllegalArgumentException("JSON: " + msg + " at position " + i);
    }
}
