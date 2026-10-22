package com.blighteddreams.helper;

import net.minecraft.resources.ResourceLocation;
import java.util.Random;

/**
 * The 4 distinct companion variants:
 * - GAIA: Draconic Knight (Original Emily)
 * - DEMON: Infernal Fiend (Crimson & Obsidian)
 * - ANGEL: Celestial Seraph (Ivory, Gold & Sapphire)
 * - ASTRAL: Starlight Void (Cosmic Indigo & Amethyst)
 * - JESTER: Harlequin (Purple & Black motley, silver hair, red eyes)
 */
public enum HelperVariant {
    GAIA(0, "Dragoness", "Dragon Warrior & Tank", 0xFFE082, 0x38E885,
            "textures/entity/xl_gaia.png", "textures/entity/xl_gaia_glow.png",
            "Master of ancient draconic might. Poised, disciplined, and unwavering.",
            "Draconic Combat",
            "★ Dragon Warrior: High armor, heavy melee strikes, and dragon breath. Protects you in frontline combat.",
            new String[]{"Kaida", "Dracona", "Rayna", "Tiamat", "Pyra", "Ember", "Vritra", "Saphira"}),

    DEMON(1, "Infernal Fiend", "Hellfire Mage & Slayer", 0xFF3D5A, 0xFF1744,
            "textures/entity/xl_demon.png", "textures/entity/xl_demon_glow.png",
            "Fierce infernal warrior born of hellfire. Aggressive, relentless, and mocking.",
            "Fire Magic",
            "★ Fire Mage: Rains firebolts and explosive hellfire upon enemies. High burst AoE damage.",
            new String[]{"Lilith", "Morrigan", "Carmilla", "Raven", "Scarlett", "Nyx", "Malice", "Vespera"}),

    ANGEL(2, "Seraph", "Radiant Healer & Valkyrie", 0xFFF8E7, 0x38E1FF,
            "textures/entity/xl_angel.png", "textures/entity/xl_angel_glow.png",
            "Divine guardian of radiant light. Pure, protective, and benevolent.",
            "Healing Magic",
            "★ Radiant Healer: Heals you and herself in battle, casts holy protective auras, and shields against death.",
            new String[]{"Seraphina", "Celeste", "Aurelia", "Lumina", "Dawn", "Aurora", "Evangelina", "Faith"}),

    ASTRAL(3, "Astral Mystic", "Cosmic Void Sorceress", 0xC084FC, 0xD638FF,
            "textures/entity/emily_astral.png", "textures/entity/emily_astral_glow.png",
            "Mystic empress channeling cosmic void energy. Enigmatic, otherworldly, and elegant.",
            "Astral Magic",
            "★ Void Sorceress: Warps gravity to slow enemies, pierces magic armor, and controls the battlefield.",
            new String[]{"Nova", "Nebula", "Lyra", "Astra", "Stella", "Vesper", "Andromeda", "Selene"}),

    // Skin-style fallback uses the Gaia skin; the Harlequin look is the 3D model (style = model).
    JESTER(4, "Harlequin", "Chaos Trickster", 0xB06CFF, 0xFF3C4E,
            "textures/entity/xl_jester.png", "textures/entity/xl_jester_glow.png",
            "Court fool with a dragon's grin. Playful, unpredictable, and dangerous when the bells stop ringing.",
            "Trickster Magic",
            "★ Chaos Trickster: Slows and withers enemies from a distance, then vanishes in a flurry of bells when hurt.",
            new String[]{"Harley", "Pepper", "Jinx", "Giggles", "Pierrette", "Belle", "Motley", "Joker"});

    public final int id;
    public final String title;
    public final String subtitle;
    public final int titleColor;
    public final int glowColor;
    public final ResourceLocation texture;
    public final ResourceLocation glowTexture;
    public final String description;
    public final String magicSpecialty;
    public final String footnote;
    public final String[] namePool;

    HelperVariant(int id, String title, String subtitle, int titleColor, int glowColor,
                  String texturePath, String glowPath, String description,
                  String magicSpecialty, String footnote, String[] namePool) {
        this.id = id;
        this.title = title;
        this.subtitle = subtitle;
        this.titleColor = titleColor;
        this.glowColor = glowColor;
        this.texture = ResourceLocation.fromNamespaceAndPath(BlightedHelper.MOD_ID, texturePath);
        this.glowTexture = ResourceLocation.fromNamespaceAndPath(BlightedHelper.MOD_ID, glowPath);
        this.description = description;
        this.magicSpecialty = magicSpecialty;
        this.footnote = footnote;
        String[] common = CommonNames.WESTERN_FEMALE;
        String[] merged = new String[namePool.length + common.length];
        System.arraycopy(namePool, 0, merged, 0, namePool.length);
        System.arraycopy(common, 0, merged, namePool.length, common.length);
        this.namePool = merged;
    }

    /** These variants draw with the 256x128 copy of the Gaia model that carries extra parts (see HelperVariantParts). */
    public boolean usesXlModel() {
        return this == GAIA || this == DEMON || this == ANGEL || this == JESTER;
    }

    public static HelperVariant byId(int id) {
        HelperVariant[] v = values();
        return v[Math.floorMod(id, v.length)];
    }

    /**
     * Shared pool of Western / Christian-style female names. It is a nested holder so it can be initialized lazily
     * after the enum constants, while every variant still keeps her own themed names.
     */
    private static final class CommonNames {
        private static final String[] WESTERN_FEMALE = {
                "Abigail", "Adelaide", "Alice", "Amelia", "Anna", "Audrey", "Bella", "Bethany", "Brielle",
                "Caroline", "Charlotte", "Chloe", "Clara", "Daisy", "Eleanor", "Eliza", "Ella", "Emma",
                "Esther", "Evelyn", "Grace", "Hannah", "Hazel", "Hope", "Isabelle", "Ivy", "Josephine",
                "Julia", "Katherine", "Leah", "Lily", "Lucy", "Madeline", "Margaret", "Maria", "Natalie",
                "Nora", "Olivia", "Penelope", "Rachel", "Rose", "Ruth", "Sarah", "Sophie", "Violet",
                "Willow", "Zoe"
        };

        private CommonNames() {}
    }

    private static final Random STATIC_RAND = new Random();

    public String getRandomName() {
        return getRandomName(STATIC_RAND);
    }

    public String getRandomName(Random rand) {
        return namePool[rand.nextInt(namePool.length)];
    }
}
