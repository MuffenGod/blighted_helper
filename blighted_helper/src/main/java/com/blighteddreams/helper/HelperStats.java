package com.blighteddreams.helper;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * Per-companion stat identity. These are the real numbers: HelperEntity.applyLevelAttributes multiplies the level curve
 * by them, HelperIronsCompat pushes the spell-power side into Iron's Spells attributes, and the starter screen draws its
 * bars from the same profile so what you see is what she does.
 * Multipliers are relative to the shared level curve (1.0 = unchanged).
 */
public final class HelperStats {

    public enum School {
        NONE("", null), FIRE("Fire", "fire"), HOLY("Holy", "holy"), ENDER("Ender", "ender"),
        LIGHTNING("Lightning", "lightning"), ELDRITCH("Eldritch", "eldritch"), EVOCATION("Arcane", "evocation"),
        COSMIC("Cosmic", "cosmic"), SHADOW("Shadow", "shadow"), RADIANCE("Radiance", "radiance");

        public final String label;
        public final String key;

        School(String label, String key) {
            this.label = label;
            this.key = key;
        }
    }

    /** One extra school bonus: +amount of base power in that school (0.5 = +50%). */
    public record Bonus(School school, float amount) {}

    public record Profile(float health, float attack, float armor, float toughness, float speed, float knockbackResist,
                          float spellPower, float cooldown, Bonus[] schools, String[] perks) {}

    private static final Bonus[] NONE = {};

    private static final Profile GAIA = new Profile(1.35F, 1.50F, 4.0F, 1.0F, 1.00F, 0.35F,
            -0.40F, 0.0F, new Bonus[]{new Bonus(School.LIGHTNING, 0.0F)},
            new String[]{"Brawler: heavy armor and knockback resistance", "Hardest melee hitter of the five"});

    private static final Profile DEMON = new Profile(1.15F, 1.25F, 1.0F, 0.0F, 1.05F, 0.10F,
            0.15F, 0.0F, new Bonus[]{new Bonus(School.FIRE, 0.60F)},
            new String[]{"Hellfire hybrid: melee hits set foes ablaze", "Fire spells hit 60% harder"});

    private static final Profile ANGEL = new Profile(1.00F, 0.55F, 1.0F, 0.0F, 1.00F, 0.0F,
            0.00F, 0.15F, new Bonus[]{new Bonus(School.HOLY, 0.50F), new Bonus(School.RADIANCE, 0.60F)},
            new String[]{"Radiance: melee hits scorch undead", "Holy and Radiance spells, and her heals, +50-60%"});

    private static final Profile ASTRAL = new Profile(0.85F, 0.65F, 0.0F, 0.0F, 1.10F, 0.0F,
            0.60F, 0.20F, new Bonus[]{new Bonus(School.ENDER, 0.50F), new Bonus(School.EVOCATION, 0.30F)},
            new String[]{"Cosmic surge: strongest spell power", "Ender and arcane spells hit harder, shorter cooldowns"});

    private static final Profile JESTER = new Profile(0.95F, 1.20F, 0.0F, 0.0F, 1.20F, 0.0F,
            0.30F, 0.15F, new Bonus[]{new Bonus(School.COSMIC, 0.60F), new Bonus(School.SHADOW, 0.60F), new Bonus(School.ENDER, 0.40F)},
            new String[]{"Cosmic trickster: fastest, hits briefly slow foes", "Cosmic, Shadow and Ender spells +40-60%, shorter cooldowns"});

    private HelperStats() {}

    public static Profile of(HelperVariant v) {
        return switch (v) {
            case GAIA -> GAIA;
            case DEMON -> DEMON;
            case ANGEL -> ANGEL;
            case ASTRAL -> ASTRAL;
            case JESTER -> JESTER;
        };
    }

    // ------------------------------------------------------------------ display ratings (0-10), derived from the real profile

    public static final String[] LABELS = {"Melee", "Vitality", "Spell Power", "Swiftness", "Support"};

    public static int[] ratings(HelperVariant v) {
        Profile p = of(v);
        float best = 1.0F;
        for (Bonus b : p.schools()) {
            best = Math.max(best, 1.0F + b.amount());
        }
        float melee = p.attack() * 6.67F;
        float vit = p.health() * 5.0F + p.armor() * 0.5F + p.knockbackResist() * 2.0F;
        float spell = (1.0F + p.spellPower()) * best * 4.5F;
        float swift = (p.speed() - 0.5F) * 10.0F;
        float holy = 1.0F;
        for (Bonus b : p.schools()) {
            if (b.school() == School.HOLY) holy = 1.0F + b.amount();
        }
        float support = 2.0F + p.cooldown() * 15.0F + Math.max(0.0F, holy - 1.0F) * 6.0F;
        return new int[]{clamp(melee), clamp(vit), clamp(spell), clamp(swift), clamp(support)};
    }

    private static int clamp(float v) {
        return Math.max(1, Math.min(10, Math.round(v)));
    }

    // ------------------------------------------------------------------ application

    private static final ResourceLocation MOD_ARMOR = id("variant_armor");
    private static final ResourceLocation MOD_TOUGH = id("variant_toughness");
    private static final ResourceLocation MOD_KB = id("variant_knockback");

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(BlightedHelper.MOD_ID, path);
    }

    /** Folds the profile into base attributes (after the level curve set them) and adds the modifiers. Safe to call repeatedly. */
    public static void apply(HelperEntity helper) {
        Profile p = of(helper.getVariant());

        AttributeInstance hp = helper.getAttribute(Attributes.MAX_HEALTH);
        if (hp != null) hp.setBaseValue(hp.getBaseValue() * p.health());
        AttributeInstance atk = helper.getAttribute(Attributes.ATTACK_DAMAGE);
        if (atk != null) atk.setBaseValue(atk.getBaseValue() * p.attack());
        AttributeInstance spd = helper.getAttribute(Attributes.MOVEMENT_SPEED);
        if (spd != null) spd.setBaseValue(0.32D * p.speed());

        setMod(helper.getAttribute(Attributes.ARMOR), MOD_ARMOR, p.armor());
        setMod(helper.getAttribute(Attributes.ARMOR_TOUGHNESS), MOD_TOUGH, p.toughness());
        setMod(helper.getAttribute(Attributes.KNOCKBACK_RESISTANCE), MOD_KB, p.knockbackResist());

        com.blighteddreams.helper.magic.HelperIronsCompat.applyStats(helper, p);
    }

    public static void setMod(AttributeInstance inst, ResourceLocation id, double amount) {
        if (inst == null) return;
        inst.removeModifier(id);
        if (amount != 0.0D) {
            inst.addPermanentModifier(new AttributeModifier(id, amount, AttributeModifier.Operation.ADD_VALUE));
        }
    }
}
