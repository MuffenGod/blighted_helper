package com.blighteddreams.helper;

import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;

/**
 * Client-side particles that go with her animations: sparks along the blade path at the moment a swing lands, embers
 * for axes, a dust ring for slams, and dust kicked up when she sprints. Called from her client tick; uses only common
 * level methods so the class is safe on a dedicated server (where it is never called).
 */
public final class HelperFx {
    private HelperFx() {}

    static void clientTick(HelperEntity e) {
        HelperVariantFx.tick(e);
        Level level = e.level();
        float yaw = e.yBodyRot * Mth.DEG_TO_RAD;
        double fx = -Mth.sin(yaw), fz = Mth.cos(yaw); // forward
        double rx = -Mth.cos(yaw), rz = -Mth.sin(yaw); // her right

        if (e.isSprinting() && e.onGround() && e.tickCount % 3 == 0) {
            level.addParticle(ParticleTypes.POOF, e.getX() - fx * 0.3, e.getY() + 0.05, e.getZ() - fz * 0.3, -fx * 0.02, 0.02, -fz * 0.02);
        }

        // ---- Melee weapon swing impact flare ----
        int since = e.tickCount - e.animSwingStartTick;
        int ticks = e.animSwingKind.swingTicks > 0 ? e.animSwingKind.swingTicks : 10;
        int land = Math.max(1, Math.round(ticks * 0.35F));
        if (since == land && e.aim() == HelperEntity.AIM_NONE) {
            WeaponKind k = e.animSwingKind;
            ParticleOptions spark = switch (k) {
                case AXE -> ParticleTypes.SMALL_FLAME;
                case DAGGER, FIST -> ParticleTypes.CRIT;
                case DUAL -> ParticleTypes.CRIT;
                case STAFF -> ParticleTypes.WITCH;
                case SPEAR -> ParticleTypes.END_ROD;
                case HEAVY -> ParticleTypes.ENCHANTED_HIT;
                default -> ParticleTypes.ENCHANTED_HIT;
            };
            double cy = e.getY() + 1.15;
            int count = k == WeaponKind.DUAL ? 14 : 9;
            for (int i = 0; i < count; i++) {
                double a = (i / (double) (count - 1) - 0.5) * Math.PI * 0.95; // fan in front
                double d = 1.3 + e.getRandom().nextDouble() * 0.35;
                double px = e.getX() + (fx * Math.cos(a) + rx * Math.sin(a)) * d;
                double pz = e.getZ() + (fz * Math.cos(a) + rz * Math.sin(a)) * d;
                ParticleOptions pType = (k == WeaponKind.DUAL && i % 2 == 1) ? ParticleTypes.ENCHANTED_HIT : spark;
                level.addParticle(pType, px, cy + (e.getRandom().nextDouble() - 0.5) * 0.45, pz, fx * 0.05, 0.02, fz * 0.05);
            }
            com.blighteddreams.helper.client.HelperSwings.Swing swing = com.blighteddreams.helper.client.HelperSwings.get(k, e.animSwingVariant);
            if (swing.groundShock() && k != WeaponKind.STAFF) {
                for (int i = 0; i < 18; i++) {
                    double a = i / 18.0 * Math.PI * 2.0;
                    level.addParticle(ParticleTypes.CLOUD, e.getX() + fx * 1.1 + Math.cos(a) * 0.7, e.getY() + 0.1,
                            e.getZ() + fz * 1.1 + Math.sin(a) * 0.7, Math.cos(a) * 0.14, 0.01, Math.sin(a) * 0.14);
                }
            }
        }

        // ---- Spellcasting visual flare ----
        if (e.animCastStyle != com.blighteddreams.helper.client.HelperCasts.STYLE_NONE) {
            int castSince = e.tickCount - e.animCastStartTick;
            int totalTicks = Math.max(6, e.animCastTicks);
            int releaseTick = Math.round(totalTicks * 0.65F);
            if (castSince >= 0 && castSince <= totalTicks) {
                com.blighteddreams.helper.client.SpellSchool school = com.blighteddreams.helper.client.SpellSchool.byId(e.animCastSchool);
                if (school == com.blighteddreams.helper.client.SpellSchool.NONE) {
                    school = com.blighteddreams.helper.client.SpellSchool.byId(e.defaultSchoolFor(e.animCastStyle));
                }

                ParticleOptions primaryMote = switch (school) {
                    case FIRE -> ParticleTypes.FLAME;
                    case HOLY -> ParticleTypes.END_ROD;
                    case ICE -> ParticleTypes.SNOWFLAKE;
                    case LIGHTNING -> ParticleTypes.ELECTRIC_SPARK;
                    case ENDER -> ParticleTypes.PORTAL;
                    case BLOOD -> ParticleTypes.CRIMSON_SPORE;
                    case EVOCATION -> ParticleTypes.ENCHANTED_HIT;
                    case NATURE -> ParticleTypes.SPORE_BLOSSOM_AIR;
                    case ELDRITCH -> ParticleTypes.SOUL_FIRE_FLAME;
                    case COSMIC -> ParticleTypes.END_ROD;
                    case HYDRO -> ParticleTypes.SPLASH;
                    default -> ParticleTypes.WITCH;
                };

                ParticleOptions secondaryMote = switch (school) {
                    case FIRE -> ParticleTypes.LAVA;
                    case HOLY -> ParticleTypes.GLOW;
                    case ICE -> ParticleTypes.ITEM_SNOWBALL;
                    case LIGHTNING -> ParticleTypes.FIREWORK;
                    case ENDER -> ParticleTypes.REVERSE_PORTAL;
                    case BLOOD -> ParticleTypes.DAMAGE_INDICATOR;
                    case EVOCATION -> ParticleTypes.HAPPY_VILLAGER;
                    case NATURE -> ParticleTypes.COMPOSTER;
                    case ELDRITCH -> ParticleTypes.SOUL;
                    case COSMIC -> ParticleTypes.FIREWORK;
                    case HYDRO -> ParticleTypes.BUBBLE_POP;
                    default -> ParticleTypes.ENCHANT;
                };

                // Style-specific gather particles:
                if (castSince < releaseTick) {
                    if (e.animCastStyle == com.blighteddreams.helper.client.HelperCasts.STYLE_OVERHEAD) {
                        // Ground circle ascending motes
                        for (int i = 0; i < 3; i++) {
                            double angle = e.getRandom().nextDouble() * Math.PI * 2.0;
                            double dist = 0.3 + e.getRandom().nextDouble() * 0.95;
                            double px = e.getX() + Math.cos(angle) * dist;
                            double py = e.getY() + 0.1 + e.getRandom().nextDouble() * 0.3;
                            double pz = e.getZ() + Math.sin(angle) * dist;
                            double vy = 0.08 + e.getRandom().nextDouble() * 0.07;
                            level.addParticle(primaryMote, px, py, pz, 0.0, vy, 0.0);
                            if (e.getRandom().nextBoolean()) {
                                level.addParticle(secondaryMote, px, py + 0.4, pz, 0.0, vy * 0.7, 0.0);
                            }
                        }
                    } else if (e.animCastStyle == com.blighteddreams.helper.client.HelperCasts.STYLE_CHARGE) {
                        // Concentric inward swirling plasma vortex
                        double cx = e.getX() + fx * 0.45;
                        double cy = e.getY() + 1.25;
                        double cz = e.getZ() + fz * 0.45;
                        for (int i = 0; i < 3; i++) {
                            double angle = (e.tickCount * 0.5) + (i * Math.PI * 2.0 / 3.0) + (e.getRandom().nextDouble() * 0.4);
                            double dist = 0.5 + e.getRandom().nextDouble() * 0.6;
                            double px = cx + Math.cos(angle) * dist;
                            double py = cy + (e.getRandom().nextDouble() - 0.5) * 0.4;
                            double pz = cz + Math.sin(angle) * dist;
                            double vx = (cx - px) * 0.16;
                            double vy = (cy - py) * 0.16;
                            double vz = (cz - pz) * 0.16;
                            level.addParticle(primaryMote, px, py, pz, vx, vy, vz);
                            if (e.getRandom().nextInt(3) == 0) {
                                level.addParticle(secondaryMote, px, py, pz, vx * 0.5, vy * 0.5, vz * 0.5);
                            }
                        }
                    } else if (castSince % 2 == 0) {
                        double angle = (e.tickCount * 0.45) + (e.getRandom().nextDouble() * Math.PI * 2);
                        double rDist = 0.85 + e.getRandom().nextDouble() * 0.5;
                        double mx = e.getX() + Math.cos(angle) * rDist;
                        double mz = e.getZ() + Math.sin(angle) * rDist;
                        double my = e.getY() + 1.1 + (e.getRandom().nextDouble() - 0.5) * 0.4;
                        double vx = (e.getX() - mx) * 0.08;
                        double vy = ((e.getY() + 1.25) - my) * 0.08;
                        double vz = (e.getZ() - mz) * 0.08;
                        level.addParticle(primaryMote, mx, my, mz, vx, vy, vz);
                    }
                }

                // Release moment: explosive discharge
                if (castSince == releaseTick) {
                    if (e.animCastStyle == com.blighteddreams.helper.client.HelperCasts.STYLE_CHARGE) {
                        double cx = e.getX() + fx * 0.5;
                        double cy = e.getY() + 1.25;
                        double cz = e.getZ() + fz * 0.5;
                        for (int i = 0; i < 28; i++) {
                            double spreadX = (e.getRandom().nextDouble() - 0.5) * 0.25;
                            double spreadY = (e.getRandom().nextDouble() - 0.5) * 0.25;
                            double spreadZ = (e.getRandom().nextDouble() - 0.5) * 0.25;
                            double spd = 0.45 + e.getRandom().nextDouble() * 0.35;
                            level.addParticle(primaryMote, cx, cy, cz, fx * spd + spreadX, spreadY, fz * spd + spreadZ);
                            if (i % 3 == 0) {
                                level.addParticle(secondaryMote, cx, cy, cz, fx * (spd * 0.7) + spreadX, spreadY * 1.5, fz * (spd * 0.7) + spreadZ);
                            }
                        }
                    } else if (e.animCastStyle == com.blighteddreams.helper.client.HelperCasts.STYLE_OVERHEAD) {
                        for (int i = 0; i < 28; i++) {
                            double a = i / 28.0 * Math.PI * 2.0;
                            double spd = 0.18 + e.getRandom().nextDouble() * 0.12;
                            level.addParticle(primaryMote, e.getX(), e.getY() + 2.1, e.getZ(), Math.cos(a) * spd, 0.02, Math.sin(a) * spd);
                            level.addParticle(secondaryMote, e.getX() + Math.cos(a) * 0.8, e.getY() + 1.5, e.getZ() + Math.sin(a) * 0.8, Math.cos(a) * 0.08, 0.06, Math.sin(a) * 0.08);
                        }
                    } else if (e.animCastStyle == com.blighteddreams.helper.client.HelperCasts.STYLE_SLAM) {
                        for (int i = 0; i < 24; i++) {
                            double a = i / 24.0 * Math.PI * 2.0;
                            level.addParticle(ParticleTypes.CLOUD, e.getX() + fx * 0.8, e.getY() + 0.1, e.getZ() + fz * 0.8,
                                    Math.cos(a) * 0.20, 0.01, Math.sin(a) * 0.20);
                            level.addParticle(primaryMote, e.getX() + fx * 0.8, e.getY() + 0.2, e.getZ() + fz * 0.8,
                                    Math.cos(a) * 0.16, 0.04, Math.sin(a) * 0.16);
                        }
                    } else {
                        double cy = e.getY() + 1.25;
                        for (int i = 0; i < 18; i++) {
                            double a = i / 18.0 * Math.PI * 2.0;
                            double spd = 0.15 + e.getRandom().nextDouble() * 0.08;
                            level.addParticle(primaryMote, e.getX() + fx * 0.4, cy, e.getZ() + fz * 0.4,
                                    Math.cos(a) * spd + fx * 0.1, (e.getRandom().nextDouble() - 0.5) * 0.06, Math.sin(a) * spd + fz * 0.1);
                        }
                    }
                }
            }
        }
    }
}
