package com.blighteddreams.helper;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Companion settings (config/blighted_enhancements-helper.toml). See design/HELPER_PLAN.md. */
public class HelperConfig {
    public static final ModConfigSpec SPEC;
    private static final ModConfigSpec.BooleanValue ENABLED;
    private static final ModConfigSpec.ConfigValue<String> NAME;
    private static final ModConfigSpec.ConfigValue<String> STARTING_WEAPON;
    private static final ModConfigSpec.IntValue STAND_UP_SECONDS;
    private static final ModConfigSpec.IntValue STAND_UP_HEALTH_PERCENT;
    private static final ModConfigSpec.IntValue GRACE_SECONDS;
    private static final ModConfigSpec.IntValue TELEPORT_DISTANCE;
    private static final ModConfigSpec.IntValue REGEN_SECONDS;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        ENABLED = b.comment("Master switch for the companion (summon command, Dream Charm, entity AI).")
                .define("enabled", true);
        NAME = b.comment("Display name of the companion.")
                .define("name", "Emily");
        STARTING_WEAPON = b.comment("Item she holds when first summoned (an item id). Empty = bare hands.")
                .define("startingWeapon", "minecraft:iron_sword");
        STAND_UP_SECONDS = b.comment("She never dies: at 0 health she kneels (downed, untouchable) and stands up by herself after this long.",
                        "If hostile mobs are still within 5 blocks she waits up to twice as long.")
                .defineInRange("standUpSeconds", 10, 2, 120);
        STAND_UP_HEALTH_PERCENT = b.comment("Health she stands back up with, as a percent of her maximum.")
                .defineInRange("standUpHealthPercent", 40, 5, 100);
        GRACE_SECONDS = b.comment("Seconds of damage immunity right after she stands up, so she is not instantly downed again.")
                .defineInRange("graceSeconds", 3, 0, 30);
        TELEPORT_DISTANCE = b.comment("If she is farther than this many blocks from her owner (and not told to stay) she teleports beside them.")
                .defineInRange("teleportDistance", 24, 8, 128);
        REGEN_SECONDS = b.comment("Seconds between 1-health heals while she is out of combat. 0 = no natural regeneration.")
                .defineInRange("regenSeconds", 4, 0, 120);
        SPEC = b.build();
    }

    public static boolean enabled() { return ENABLED.get(); }
    public static String name() { return NAME.get(); }
    public static String startingWeapon() { return STARTING_WEAPON.get(); }
    public static int standUpTicks() { return STAND_UP_SECONDS.get() * 20; }
    public static int standUpHealthPercent() { return STAND_UP_HEALTH_PERCENT.get(); }
    public static int graceTicks() { return GRACE_SECONDS.get() * 20; }
    public static int teleportDistance() { return TELEPORT_DISTANCE.get(); }
    public static int regenTicks() { return REGEN_SECONDS.get() * 20; }
}
