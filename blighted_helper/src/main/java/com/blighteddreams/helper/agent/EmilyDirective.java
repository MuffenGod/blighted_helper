package com.blighteddreams.helper.agent;

import java.util.Locale;

/**
 * One order for Emily, whether it came from the player's chat, a command, or her own planner.
 * Kept deliberately small and plain so it can travel over the network and be logged.
 *
 * <p>{@code extra} carries free-form data such as coordinates ("x y z"), a structure query, or a spell id.
 * {@code amount} is a target count; 0 means "whatever makes sense".</p>
 */
public record EmilyDirective(String verb, String target, int amount, String extra) {

    public EmilyDirective {
        verb = verb == null ? "" : verb.trim().toLowerCase(Locale.ROOT);
        target = target == null ? "" : target.trim();
        extra = extra == null ? "" : extra.trim();
        if (amount < 0) amount = 0;
    }

    public EmilyDirective(String verb, String target) {
        this(verb, target, 0, "");
    }

    public EmilyDirective(String verb) {
        this(verb, "", 0, "");
    }

    public boolean is(String name) {
        return verb.equals(name);
    }

    public String describe() {
        StringBuilder sb = new StringBuilder(verb);
        if (!target.isBlank()) sb.append(' ').append(target);
        if (amount > 0) sb.append(" x").append(amount);
        if (!extra.isBlank()) sb.append(" (").append(extra).append(')');
        return sb.toString();
    }

    /** Parses "x y z" (or "x,y,z") out of {@link #extra}; returns null when it is not a coordinate triplet. */
    public int[] coordinates() {
        String s = extra.replace(',', ' ').trim();
        if (s.isEmpty()) return null;
        String[] parts = s.split("\\s+");
        if (parts.length < 3) return null;
        try {
            return new int[]{Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2])};
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
