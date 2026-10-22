package com.blighteddreams.helper.agent;

import com.blighteddreams.helper.HelperEntity;
import com.blighteddreams.helper.HelperVariant;
import com.blighteddreams.helper.magic.HelperIronsCompat;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.CastSource;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** Iron's Spells loadouts for every variant, plus the explicit "cast a spell" order. */
public final class EmilySpells {
    private EmilySpells() {
    }

    public static boolean castBest(HelperEntity helper) {
        if (!HelperIronsCompat.isAvailable()) return false;
        HelperVariant variant = helper.getVariant();
        LivingEntity target = helper.getTarget();

        if (variant == HelperVariant.ANGEL) {
            var owner = helper.getOwner();
            if (owner != null && owner.getHealth() < owner.getMaxHealth() * 0.85F && helper.distanceToSqr(owner) < 256.0) {
                HelperIronsCompat.castHeal(helper, owner, helper.getHolySpellLevel());
                return true;
            }
            if (helper.getHealth() < helper.getMaxHealth() * 0.65F) {
                HelperIronsCompat.castHeal(helper, helper, helper.getHolySpellLevel());
                return true;
            }
            if (target != null) {
                HelperIronsCompat.castSmite(helper, target, helper.getHolySpellLevel());
                return true;
            }
            return false;
        }

        if (variant == HelperVariant.DEMON) {
            if (target != null) {
                HelperIronsCompat.castFire(helper, target, helper.getFireSpellLevel());
                return true;
            }
            return false;
        }

        if (variant == HelperVariant.GAIA) {
            if (helper.getHealth() < helper.getMaxHealth() * 0.45F) {
                return castSpell(helper, "irons_spellbooks:heal", helper, 2);
            }
            if (target != null) {
                String spell = helper.getRandom().nextFloat() < 0.35F
                        ? "irons_spellbooks:chain_lightning" : "irons_spellbooks:lightning_bolt";
                return castSpell(helper, spell, target, 2);
            }
            return false;
        }

        if (variant == HelperVariant.JESTER) {
            // Chaos Trickster: slow and wither from range, evade when hurt.
            if (helper.getHealth() < helper.getMaxHealth() * 0.5F && helper.getRandom().nextFloat() < 0.6F) {
                if (castSpell(helper, "irons_spellbooks:evasion", helper, 2)) return true;
            }
            if (target != null) {
                // Cosmic, Shadow and Ender: Hazen N Stuff spells when installed, Iron's trickster kit otherwise.
                int lvl = Math.max(1, Math.min(6, 1 + helper.getLevel() / 8));
                float roll = helper.getRandom().nextFloat();
                String spell;
                if (roll < 0.28F) spell = "hazennstuff:cosmic_bolt";
                else if (roll < 0.42F) spell = "hazennstuff:shooting_star";
                else if (roll < 0.52F) spell = "hazennstuff:evercomet_barrage";
                else if (roll < 0.72F) spell = "hazennstuff:nights_edge_strike";
                else if (roll < 0.86F) spell = "hazennstuff:shadow_reaver";
                else spell = "irons_spellbooks:slow";
                if (castSpell(helper, spell, target, lvl)) return true;
                return castSpell(helper, roll < 0.5F ? "irons_spellbooks:slow" : "irons_spellbooks:shadow_slash", target, lvl);
            }
            return false;
        }

        // Astral: arcane control and missiles.
        if (helper.getHealth() < helper.getMaxHealth() * 0.4F) {
            if (castSpell(helper, "irons_spellbooks:shield", helper, 2)) return true;
        }
        if (target != null) {
            String spell = helper.getRandom().nextFloat() < 0.25F
                    ? "irons_spellbooks:slow" : "irons_spellbooks:magic_missile";
            return castSpell(helper, spell, target, 2);
        }
        return false;
    }

    /** Casts one spell by registry id at a target. Returns false on any failure, never throws. */
    public static boolean castSpell(HelperEntity helper, String spellId, LivingEntity target, int level) {
        if (helper == null || target == null || !target.isAlive() || !HelperIronsCompat.isAvailable()) return false;
        ResourceLocation id = ResourceLocation.tryParse(normalizeId(spellId));
        if (id == null) return false;
        try {
            AbstractSpell spell = SpellRegistry.REGISTRY.get(id);
            if (spell == null || spell == SpellRegistry.none()) return false;
            helper.lookAt(EntityAnchorArgument.Anchor.EYES, target.getEyePosition());
            helper.getLookControl().setLookAt(target, 45.0F, 45.0F);
            MagicData data = magicData(helper);
            int castLevel = Math.max(1, Math.min(8, level));
            if (target == helper || target == helper.getOwner()) {
                // Self or ally spells (heal, shield, haste) resolve on whoever is passed as the caster.
                spell.onCast(helper.level(), castLevel, target, CastSource.MOB, data);
            } else {
                // Hostile targets: she is the caster, so her spell-power attributes apply; the target rides in the cast data.
                data.setAdditionalCastData(new io.redspace.ironsspellbooks.capabilities.magic.TargetEntityCastData(target));
                spell.onCast(helper.level(), castLevel, helper, CastSource.MOB, data);
            }
            int style = resolveCastStyle(spellId);
            int school = com.blighteddreams.helper.client.SpellSchool.fromString(spellId).id;
            if (school == 0 && spell.getSchoolType() != null && spell.getSchoolType().getId() != null) {
                school = com.blighteddreams.helper.client.SpellSchool.fromString(spell.getSchoolType().getId().toString()).id;
            }
            int castTicks = 18;
            try {
                castTicks = Math.max(6, Math.min(80, spell.getCastTime(level)));
            } catch (Throwable ignored) {}
            helper.triggerCastAnim(style, castTicks, helper.getRandom().nextInt(3), school);
            if (helper.level() instanceof ServerLevel sl) {
                Vec3 eye = helper.getEyePosition();
                sl.sendParticles(ParticleTypes.ENCHANT, eye.x, eye.y - 0.15, eye.z, 10, 0.25, 0.25, 0.25, 0.05);
                sl.playSound(null, helper.blockPosition(), SoundEvents.EVOKER_CAST_SPELL, SoundSource.PLAYERS, 0.55F, 1.15F);
            }
            return true;
        } catch (Throwable t) {
            return vanillaFallback(helper, spellId, target, level);
        }
    }

    public static int resolveCastStyle(String spellId) {
        String s = spellId != null ? spellId.toLowerCase(Locale.ROOT) : "";
        if (s.contains("shield") || s.contains("ward") || s.contains("protect") || s.contains("fortify") || s.contains("barrier")) {
            return com.blighteddreams.helper.client.HelperCasts.STYLE_SHIELD;
        }
        if (s.contains("heal") || s.contains("blessing") || s.contains("divine") || s.contains("radiance") || s.contains("angel") || s.contains("holy") || s.contains("regen")) {
            return com.blighteddreams.helper.client.HelperCasts.STYLE_OVERHEAD;
        }
        if (s.contains("slam") || s.contains("quake") || s.contains("earth") || s.contains("stomp") || s.contains("ground")) {
            return com.blighteddreams.helper.client.HelperCasts.STYLE_SLAM;
        }
        if (s.contains("beam") || s.contains("ray") || s.contains("channel") || s.contains("torrent") || s.contains("stream")) {
            return com.blighteddreams.helper.client.HelperCasts.STYLE_CHANNEL;
        }
        if (s.contains("missile") || s.contains("bolt") || s.contains("arrow") || s.contains("dart") || s.contains("smite") || s.contains("slow")) {
            return com.blighteddreams.helper.client.HelperCasts.STYLE_POINT;
        }
        return com.blighteddreams.helper.client.HelperCasts.STYLE_CHARGE;
    }

    private static boolean vanillaFallback(HelperEntity helper, String spellId, LivingEntity target, int level) {
        int school = com.blighteddreams.helper.client.SpellSchool.fromString(spellId).id;
        helper.triggerCastAnim(resolveCastStyle(spellId), 18, helper.getRandom().nextInt(3), school);
        String id = spellId == null ? "" : spellId.toLowerCase(Locale.ROOT);
        if (id.contains("heal") || id.contains("regeneration")) {
            target.heal(4.0F + level * 2.0F);
            target.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 100, Math.min(2, level - 1)));
            return true;
        }
        if (id.contains("shield") || id.contains("fortify")) {
            target.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 200, 1));
            target.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 200, Math.min(2, level - 1)));
            return true;
        }
        if (id.contains("haste")) {
            target.addEffect(new MobEffectInstance(MobEffects.DIG_SPEED, 300, 2));
            target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 300, 1));
            return true;
        }
        if (id.contains("slow")) {
            target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 120, 2));
            return true;
        }
        if (id.contains("teleport") || id.contains("recall")) {
            return false; // handled by the agent, not a damage fallback
        }
        if (target.isAlive() && helper.level() instanceof ServerLevel sl) {
            target.hurt(helper.damageSources().indirectMagic(helper, helper), 3.0F + level * 2.0F);
            sl.sendParticles(ParticleTypes.ELECTRIC_SPARK, target.getX(), target.getY(0.6), target.getZ(),
                    12, 0.3, 0.4, 0.3, 0.1);
            return true;
        }
        return false;
    }

    private static MagicData magicData(HelperEntity helper) {
        try {
            MagicData data = MagicData.getPlayerMagicData(helper);
            if (data != null) return data;
        } catch (Throwable ignored) {
        }
        return new MagicData();
    }

    public static String normalizeId(String spell) {
        if (spell == null || spell.isBlank()) return "irons_spellbooks:magic_missile";
        String s = spell.trim().toLowerCase(Locale.ROOT).replace(' ', '_');
        if (s.contains(":")) return s;
        return switch (s) {
            case "fire", "firebolt" -> "irons_spellbooks:firebolt";
            case "fireball" -> "irons_spellbooks:fireball";
            case "heal", "healing", "cure" -> "irons_spellbooks:heal";
            case "greater_heal", "greaterheal" -> "irons_spellbooks:greater_heal";
            case "smite", "guiding_bolt" -> "irons_spellbooks:guiding_bolt";
            case "lightning", "lightning_bolt" -> "irons_spellbooks:lightning_bolt";
            case "chain_lightning" -> "irons_spellbooks:chain_lightning";
            case "magic_missile", "missile" -> "irons_spellbooks:magic_missile";
            case "shield" -> "irons_spellbooks:shield";
            case "haste" -> "irons_spellbooks:haste";
            case "slow" -> "irons_spellbooks:slow";
            case "teleport", "warp" -> "irons_spellbooks:teleport";
            case "black_hole", "blackhole" -> "irons_spellbooks:black_hole";
            case "ice", "icicle", "frost" -> "irons_spellbooks:icicle";
            case "fire_breath" -> "irons_spellbooks:fire_breath";
            case "blood_slash" -> "irons_spellbooks:blood_slash";
            case "summon_vex", "vex" -> "irons_spellbooks:summon_vex";
            default -> "irons_spellbooks:" + s;
        };
    }

    @Nullable
    public static LivingEntity findTarget(HelperEntity helper, double radius) {
        if (!(helper.level() instanceof ServerLevel level)) return null;
        List<Mob> mobs = level.getEntitiesOfClass(Mob.class,
                new AABB(helper.blockPosition()).inflate(radius),
                e -> e.isAlive() && e instanceof Enemy && helper.canAttack(e));
        return mobs.stream().min(Comparator.comparingDouble(helper::distanceToSqr)).orElse(null);
    }

    public static class SpellTask implements EmilyTask {
        private final String spell;
        private int delay = 12;
        private boolean done;
        private boolean failed;

        public SpellTask(String spell) {
            this.spell = spell == null ? "" : spell.trim();
        }

        @Override
        public String name() {
            return "spell";
        }

        @Override
        public String label() {
            return "casting " + (spell.isBlank() ? "a spell" : spell);
        }

        @Override
        public void start(HelperEntity helper, EmilyAgent agent) {
            if (!HelperIronsCompat.isAvailable()) {
                agent.say("I don't have any magic to call on in this world.", "worried");
                failed = true;
                return;
            }
            agent.say("Channeling " + (spell.isBlank() ? "magic" : spell) + ".", "battle");
        }

        @Override
        public Status tick(HelperEntity helper, EmilyAgent agent) {
            if (failed) return Status.FAILED;
            if (done) return Status.DONE;
            if (--delay > 0) return Status.RUNNING;
            done = true;
            String id = normalizeId(spell);
            boolean healing = id.contains("heal") || id.contains("regeneration") || id.contains("shield")
                    || id.contains("haste") || id.contains("fortify");
            LivingEntity target;
            if (healing) {
                var owner = helper.getOwner();
                target = owner != null && owner.getHealth() < owner.getMaxHealth() * 0.95F ? owner : helper;
            } else {
                target = findTarget(helper, 24.0);
                if (target == null) target = helper;
            }
            int level = Math.max(1, Math.min(8, 1 + helper.getLevel() / 5));
            if (id.contains("fire") && helper.getVariant() == HelperVariant.DEMON) level = helper.getFireSpellLevel();
            if ((id.contains("heal") || id.contains("guiding")) && helper.getVariant() == HelperVariant.ANGEL) {
                level = helper.getHolySpellLevel();
            }
            boolean ok = castSpell(helper, id, target, level);
            if (ok) {
                agent.say("Spell away.", "smug");
                return Status.DONE;
            }
            agent.say("The spell fizzled. I need to practice that one.", "worried");
            return Status.FAILED;
        }

        @Override
        public boolean roaming() {
            return false;
        }

        @Override
        public boolean quietOnDone() {
            return true;
        }
    }
}
