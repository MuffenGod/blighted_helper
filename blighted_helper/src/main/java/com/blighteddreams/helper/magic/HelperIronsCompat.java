package com.blighteddreams.helper.magic;

import com.blighteddreams.helper.HelperEntity;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.CastSource;
import io.redspace.ironsspellbooks.api.spells.ISpellContainer;
import io.redspace.ironsspellbooks.api.spells.SpellSlot;
import io.redspace.ironsspellbooks.item.Scroll;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.SmallFireball;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;

/**
 * Iron's Spells and Spellbooks integration.
 * Provides spell learning, casting, and progression for companion variants:
 * - Demon (Infernal One): Fire Magic (Firebolt / Fireball)
 * - Angel (Seraph): Healing Magic (Heal / Greater Heal) & Holy Smite (Guiding Bolt)
 * Includes graceful vanilla fallbacks if Iron's Spells is not loaded.
 */
public final class HelperIronsCompat {
    private HelperIronsCompat() {}

    public record SpellUpgrade(String school, String spellName, int level) {}

    public static boolean isAvailable() {
        return ModList.get().isLoaded("irons_spellbooks");
    }

    /**
     * Checks if the item is a spell scroll or spell container.
     */
    public static boolean isSpellItem(ItemStack stack) {
        if (stack.isEmpty() || !isAvailable()) return false;
        try {
            return stack.getItem() instanceof Scroll || ISpellContainer.isSpellContainer(stack);
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * Inspects a spell container (scroll or book) and extracts its primary spell information.
     */
    public static SpellUpgrade inspectSpell(ItemStack stack) {
        if (stack.isEmpty() || !isAvailable()) return null;
        try {
            if (!ISpellContainer.isSpellContainer(stack)) return null;
            ISpellContainer container = ISpellContainer.get(stack);
            if (container == null || container.isEmpty()) return null;

            for (SpellSlot slot : container.getActiveSpells()) {
                if (slot != null && slot.getSpell() != null && slot.getSpell() != SpellRegistry.none()) {
                    AbstractSpell spell = slot.getSpell();
                    ResourceLocation schoolId = spell.getSchoolType() != null ? spell.getSchoolType().getId() : null;
                    String school = schoolId != null ? schoolId.toString() : "";
                    String name = spell.getDisplayName(null).getString();
                    int level = slot.getLevel();
                    return new SpellUpgrade(school, name, level);
                }
            }
        } catch (Throwable t) {
            // Safe fallback
        }
        return null;
    }

    /**
     * Demon casts Fire Magic:
     * - Level 1-4: Firebolt
     * - Level 5+: Chance of Fireball, otherwise Firebolt
     */
    public static void castFire(HelperEntity helper, LivingEntity target, int spellLevel) {
        if (helper == null || target == null || !target.isAlive()) return;

        // Turn towards target
        helper.lookAt(EntityAnchorArgument.Anchor.EYES, target.getEyePosition());
        helper.getLookControl().setLookAt(target, 45.0F, 45.0F);

        if (isAvailable()) {
            try {
                AbstractSpell spell = (spellLevel >= 5 && helper.getRandom().nextFloat() < 0.40F)
                        ? SpellRegistry.FIREBALL_SPELL.get()
                        : SpellRegistry.FIREBOLT_SPELL.get();

                MagicData md = getMagicData(helper);
                spell.onCast(helper.level(), spellLevel, helper, CastSource.MOB, md);

                if (helper.level() instanceof ServerLevel sl) {
                    sl.sendParticles(ParticleTypes.FLAME, helper.getX(), helper.getEyeY() - 0.2, helper.getZ(), 10, 0.2, 0.2, 0.2, 0.05);
                }
                return;
            } catch (Throwable t) {
                // Fall back to vanilla fireball if exception occurs
            }
        }

        // Vanilla fallback: shoot small fireball
        if (helper.level() instanceof ServerLevel sl) {
            Vec3 dir = target.getEyePosition().subtract(helper.getEyePosition()).normalize();
            SmallFireball fireball = new SmallFireball(sl, helper, dir);
            fireball.setPos(helper.getX() + dir.x * 0.8, helper.getEyeY() + dir.y * 0.8, helper.getZ() + dir.z * 0.8);
            sl.addFreshEntity(fireball);
            sl.sendParticles(ParticleTypes.FLAME, helper.getX(), helper.getEyeY(), helper.getZ(), 8, 0.2, 0.2, 0.2, 0.05);
        }
    }

    /**
     * Angel casts Healing Magic:
     * - Level 1-3: Heal
     * - Level 4+: Greater Heal
     */
    public static void castHeal(HelperEntity helper, LivingEntity recipient, int spellLevel) {
        if (helper == null || recipient == null || !recipient.isAlive()) return;

        helper.lookAt(EntityAnchorArgument.Anchor.EYES, recipient.getEyePosition());
        helper.getLookControl().setLookAt(recipient, 45.0F, 45.0F);

        if (isAvailable()) {
            try {
                AbstractSpell spell = (spellLevel >= 4)
                        ? SpellRegistry.GREATER_HEAL_SPELL.get()
                        : SpellRegistry.HEAL_SPELL.get();

                MagicData md = getMagicData(helper);
                float before = recipient.getHealth();
                spell.onCast(helper.level(), spellLevel, recipient, CastSource.MOB, md);
                // The spell resolves on the recipient, so scale the heal by her own holy power relative to theirs.
                float healed = recipient.getHealth() - before;
                if (healed > 0.0F) {
                    float hers = spell.getSpellPower(spellLevel, helper);
                    float theirs = Math.max(0.1F, spell.getSpellPower(spellLevel, recipient));
                    if (hers > theirs) recipient.heal(healed * (hers / theirs - 1.0F));
                }

                if (helper.level() instanceof ServerLevel sl) {
                    sl.sendParticles(ParticleTypes.HEART, recipient.getX(), recipient.getY(0.7), recipient.getZ(), 6, 0.3, 0.3, 0.3, 0.02);
                    sl.sendParticles(ParticleTypes.END_ROD, recipient.getX(), recipient.getY(0.5), recipient.getZ(), 8, 0.3, 0.4, 0.3, 0.04);
                }
                return;
            } catch (Throwable t) {
                // Fall back to vanilla heal if exception occurs
            }
        }

        // Vanilla fallback
        recipient.heal(4.0F + spellLevel * 2.0F);
        recipient.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 120, Math.min(2, spellLevel - 1)));
        if (helper.level() instanceof ServerLevel sl) {
            sl.sendParticles(ParticleTypes.HEART, recipient.getX(), recipient.getY(0.7), recipient.getZ(), 6, 0.3, 0.3, 0.3, 0.02);
        }
    }

    /**
     * Angel casts Holy Smite (offensive Guiding Bolt) at hostiles.
     */
    public static void castSmite(HelperEntity helper, LivingEntity target, int spellLevel) {
        if (helper == null || target == null || !target.isAlive()) return;

        helper.lookAt(EntityAnchorArgument.Anchor.EYES, target.getEyePosition());
        helper.getLookControl().setLookAt(target, 45.0F, 45.0F);

        if (isAvailable()) {
            // Radiance (Hazen N Stuff) about half the time when installed, guiding bolt otherwise.
            if (HelperHazenCompat.isAvailable() && helper.getRandom().nextFloat() < 0.5F) {
                String id = helper.getRandom().nextFloat() < 0.65F ? "hazennstuff:terraprismic_barrage" : "hazennstuff:call_forth_terraprisma";
                if (com.blighteddreams.helper.agent.EmilySpells.castSpell(helper, id, target, Math.max(1, Math.min(6, spellLevel)))) {
                    return;
                }
            }
            try {
                AbstractSpell spell = SpellRegistry.GUIDING_BOLT_SPELL.get();
                MagicData md = getMagicData(helper);
                spell.onCast(helper.level(), spellLevel, helper, CastSource.MOB, md);

                if (helper.level() instanceof ServerLevel sl) {
                    sl.sendParticles(ParticleTypes.END_ROD, helper.getX(), helper.getEyeY() - 0.2, helper.getZ(), 8, 0.2, 0.2, 0.2, 0.05);
                }
                return;
            } catch (Throwable t) {
                // Fall back to vanilla smite if exception occurs
            }
        }

        // Vanilla fallback: divine magic strike
        target.hurt(helper.damageSources().indirectMagic(helper, helper), 4.0F + spellLevel * 2.5F);
        target.setRemainingFireTicks(60);
        if (helper.level() instanceof ServerLevel sl) {
            sl.sendParticles(ParticleTypes.ELECTRIC_SPARK, target.getX(), target.getY(0.6), target.getZ(), 12, 0.3, 0.4, 0.3, 0.1);
        }
    }

    /** Pushes the variant's spell identity (global power, school powers, cooldown) into Iron's attributes. */
    public static void applyStats(HelperEntity helper, com.blighteddreams.helper.HelperStats.Profile p) {
        if (!isAvailable()) return;
        try {
            for (com.blighteddreams.helper.HelperStats.School s : com.blighteddreams.helper.HelperStats.School.values()) {
                var holder = schoolAttribute(s);
                if (holder == null) continue;
                float amount = 0.0F;
                for (com.blighteddreams.helper.HelperStats.Bonus b : p.schools()) {
                    if (b.school() == s) amount += b.amount();
                }
                com.blighteddreams.helper.HelperStats.setMod(helper.getAttribute(holder), modId("school_" + s.key), amount);
            }
            com.blighteddreams.helper.HelperStats.setMod(helper.getAttribute(
                    io.redspace.ironsspellbooks.api.registry.AttributeRegistry.SPELL_POWER), modId("spell_power"), p.spellPower());
            com.blighteddreams.helper.HelperStats.setMod(helper.getAttribute(
                    io.redspace.ironsspellbooks.api.registry.AttributeRegistry.COOLDOWN_REDUCTION), modId("cooldown"), p.cooldown());
            com.blighteddreams.helper.HelperStats.setMod(helper.getAttribute(
                    io.redspace.ironsspellbooks.api.registry.AttributeRegistry.CAST_TIME_REDUCTION), modId("cast_time"), p.cooldown());
        } catch (Throwable ignored) {
            // Iron's attributes missing on this entity: stats fall back to melee/health only.
        }
    }

    private static ResourceLocation modId(String path) {
        return ResourceLocation.fromNamespaceAndPath(com.blighteddreams.helper.BlightedHelper.MOD_ID, "variant_" + path);
    }

    private static net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> schoolAttribute(
            com.blighteddreams.helper.HelperStats.School s) {
        var r = io.redspace.ironsspellbooks.api.registry.AttributeRegistry.class;
        return switch (s) {
            case FIRE -> io.redspace.ironsspellbooks.api.registry.AttributeRegistry.FIRE_SPELL_POWER;
            case HOLY -> io.redspace.ironsspellbooks.api.registry.AttributeRegistry.HOLY_SPELL_POWER;
            case ENDER -> io.redspace.ironsspellbooks.api.registry.AttributeRegistry.ENDER_SPELL_POWER;
            case LIGHTNING -> io.redspace.ironsspellbooks.api.registry.AttributeRegistry.LIGHTNING_SPELL_POWER;
            case ELDRITCH -> io.redspace.ironsspellbooks.api.registry.AttributeRegistry.ELDRITCH_SPELL_POWER;
            case EVOCATION -> io.redspace.ironsspellbooks.api.registry.AttributeRegistry.EVOCATION_SPELL_POWER;
            case COSMIC, SHADOW, RADIANCE -> HelperHazenCompat.power(s);
            default -> null;
        };
    }

    private static MagicData getMagicData(HelperEntity helper) {
        try {
            MagicData md = MagicData.getPlayerMagicData(helper);
            if (md != null) return md;
        } catch (Throwable ignored) {}
        return new MagicData();
    }
}
