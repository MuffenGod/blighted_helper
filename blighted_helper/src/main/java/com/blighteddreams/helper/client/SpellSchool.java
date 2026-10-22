package com.blighteddreams.helper.client;

import java.util.Locale;

/**
 * Magic schools supported by Emily's casting animations.
 * Covers all 9 core Iron's Spells & Spellbooks schools plus the 2 Hazzenstuff schools (Cosmic and Hydro).
 */
public enum SpellSchool {
    NONE(0, "none", 1.0F, 1.0F, 1.0F),
    FIRE(1, "fire", 1.0F, 0.45F, 0.08F),
    HOLY(2, "holy", 1.0F, 0.85F, 0.28F),
    ICE(3, "ice", 0.35F, 0.85F, 1.0F),
    LIGHTNING(4, "lightning", 0.45F, 0.68F, 1.0F),
    ENDER(5, "ender", 0.70F, 0.15F, 0.95F),
    BLOOD(6, "blood", 0.88F, 0.05F, 0.14F),
    EVOCATION(7, "evocation", 0.18F, 0.94F, 0.58F),
    NATURE(8, "nature", 0.25F, 0.88F, 0.25F),
    ELDRITCH(9, "eldritch", 0.15F, 0.78F, 0.80F),
    COSMIC(10, "cosmic", 0.65F, 0.32F, 1.0F),
    HYDRO(11, "hydro", 0.12F, 0.72F, 0.96F);

    public final int id;
    public final String name;
    public final float r, g, b;

    SpellSchool(int id, String name, float r, float g, float b) {
        this.id = id;
        this.name = name;
        this.r = r;
        this.g = g;
        this.b = b;
    }

    public static SpellSchool byId(int id) {
        SpellSchool[] values = values();
        if (id >= 0 && id < values.length) {
            return values[id];
        }
        return NONE;
    }

    public static SpellSchool fromString(String str) {
        if (str == null || str.isBlank()) return NONE;
        String s = str.toLowerCase(Locale.ROOT);
        for (SpellSchool school : values()) {
            if (school.name.equals(s)) return school;
        }
        // Substring matching
        if (s.contains("cosmic") || s.contains("star") || s.contains("comet") || s.contains("moon") || s.contains("astral")) return COSMIC;
        if (s.contains("hydro") || s.contains("water") || s.contains("typhoon") || s.contains("trident") || s.contains("ocean") || s.contains("bubble")) return HYDRO;
        if (s.contains("fire") || s.contains("pyro") || s.contains("flame") || s.contains("cinder") || s.contains("scorch")) return FIRE;
        if (s.contains("heal") || s.contains("holy") || s.contains("radiance") || s.contains("priest") || s.contains("divine") || s.contains("bless")) return HOLY;
        if (s.contains("ice") || s.contains("cryo") || s.contains("frost") || s.contains("blizzard") || s.contains("hail") || s.contains("snow")) return ICE;
        if (s.contains("lightning") || s.contains("electro") || s.contains("spark") || s.contains("thunder") || s.contains("shock") || s.contains("storm")) return LIGHTNING;
        if (s.contains("ender") || s.contains("void") || s.contains("shadow") || s.contains("teleport") || s.contains("rift")) return ENDER;
        if (s.contains("blood") || s.contains("sanguine") || s.contains("cultist") || s.contains("vampire") || s.contains("flesh")) return BLOOD;
        if (s.contains("evocation") || s.contains("archevoker") || s.contains("spectral") || s.contains("fang") || s.contains("axe") || s.contains("vex")) return EVOCATION;
        if (s.contains("nature") || s.contains("plagued") || s.contains("poison") || s.contains("vine") || s.contains("briar") || s.contains("spore")) return NATURE;
        if (s.contains("eldritch") || s.contains("abyssal") || s.contains("soul") || s.contains("necro") || s.contains("wither")) return ELDRITCH;
        return NONE;
    }
}
