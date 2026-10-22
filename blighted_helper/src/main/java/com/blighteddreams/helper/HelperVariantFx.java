package com.blighteddreams.helper;

import com.blighteddreams.helper.client.HelperMotionData;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import org.joml.Vector3f;

import java.util.Locale;

/**
 * Particles that go with the premium motion of the four Gaia-model helpers: the event pulses authored into each flourish (confetti, bells, rings,
 * rays, feathers, flames...) and a light ambient shimmer for each variant. Client side only; uses common level methods so it is safe on a server
 * (where it is never called).
 */
public final class HelperVariantFx {
    private HelperVariantFx() {}

    private static DustParticleOptions dust(float r, float g, float b, float size) {
        return new DustParticleOptions(new Vector3f(r, g, b), size);
    }

    private static final float[][] CONFETTI = {{0.62F, 0.35F, 1.0F}, {1.0F, 0.85F, 0.3F}, {1.0F, 1.0F, 1.0F}, {1.0F, 0.25F, 0.35F}, {0.4F, 0.2F, 0.7F}};

    static void tick(HelperEntity e) {
        HelperVariant v = e.getVariant();
        if (!v.usesXlModel()) return;
        String vn = v.name().toLowerCase(Locale.ROOT);
        if (e.animIdleVariant >= 0 && !e.inCombat()) {
            float dur = Math.max(1.0F, e.flourishTicks());
            float prev = (e.tickCount - 1 - e.animIdleStartTick) / dur, now = (e.tickCount - e.animIdleStartTick) / dur;
            for (HelperMotionData.Event ev : HelperMotionData.events(vn, e.animIdleVariant)) {
                if (ev.t() > prev && ev.t() <= now) emit(e, v, ev.name());
            }
        }
        ambient(e, v);
    }

    // ------------------------------------------------------------------------------------------------ positions
    private static double fx(HelperEntity e) { return -Mth.sin(e.yBodyRot * Mth.DEG_TO_RAD); }
    private static double fz(HelperEntity e) { return Mth.cos(e.yBodyRot * Mth.DEG_TO_RAD); }

    /** point relative to her feet: forward, to her left, up */
    private static double[] at(HelperEntity e, double fwd, double left, double up) {
        double yaw = e.yBodyRot * Mth.DEG_TO_RAD;
        double fxx = -Math.sin(yaw), fzz = Math.cos(yaw);
        double lx = Math.cos(yaw), lz = Math.sin(yaw);
        return new double[]{e.getX() + fxx * fwd + lx * left, e.getY() + up, e.getZ() + fzz * fwd + lz * left};
    }

    // ------------------------------------------------------------------------------------------------ flourish events
    private static void emit(HelperEntity e, HelperVariant v, String name) {
        Level level = e.level();
        RandomSource r = e.getRandom();
        double h = e.getBbHeight();
        switch (name) {
            case "ev.confetti" -> {
                for (int i = 0; i < 36; i++) {
                    float[] c = CONFETTI[r.nextInt(CONFETTI.length)];
                    double[] p = at(e, 0.1, 0.0, h * 0.75);
                    double a = r.nextDouble() * Math.PI * 2.0, sp = 0.08 + r.nextDouble() * 0.22;
                    level.addParticle(dust(c[0], c[1], c[2], 1.0F + r.nextFloat() * 0.6F), p[0], p[1], p[2], Math.cos(a) * sp, 0.12 + r.nextDouble() * 0.2, Math.sin(a) * sp);
                }
                for (int i = 0; i < 8; i++) level.addParticle(ParticleTypes.END_ROD, e.getX(), e.getY() + h * 0.7, e.getZ(), (r.nextDouble() - 0.5) * 0.25, 0.14, (r.nextDouble() - 0.5) * 0.25);
            }
            case "ev.bells" -> {
                for (int i = 0; i < 8; i++) {
                    double[] p = at(e, (r.nextDouble() - 0.5) * 0.6, (r.nextDouble() - 0.5) * 1.6, h * (0.55 + r.nextDouble() * 0.5));
                    level.addParticle(dust(1.0F, 0.85F, 0.3F, 0.8F), p[0], p[1], p[2], 0.0, 0.03, 0.0);
                    if (i % 3 == 0) level.addParticle(ParticleTypes.NOTE, p[0], p[1] + 0.2, p[2], r.nextDouble(), 0.0, 0.0);
                }
            }
            case "ev.note" -> {
                double[] p = at(e, 0.0, (r.nextDouble() - 0.5) * 0.6, h + 0.1);
                level.addParticle(ParticleTypes.NOTE, p[0], p[1], p[2], r.nextDouble(), 0.0, 0.0);
            }
            case "ev.heart" -> {
                for (int i = 0; i < 3; i++) {
                    double[] p = at(e, 0.3, (r.nextDouble() - 0.5) * 0.5, h * 0.95);
                    level.addParticle(ParticleTypes.HEART, p[0], p[1], p[2], 0.0, 0.05, 0.0);
                }
            }
            case "ev.embers" -> {
                for (int i = 0; i < 14; i++) {
                    double[] p = at(e, -0.15 - r.nextDouble() * 0.3, (r.nextDouble() - 0.5) * 1.8, h * (0.4 + r.nextDouble() * 0.6));
                    ParticleOptions o = switch (v) {
                        case DEMON -> i % 3 == 0 ? ParticleTypes.DRAGON_BREATH : ParticleTypes.REVERSE_PORTAL;
                        case GAIA -> i % 3 == 0 ? ParticleTypes.LAVA : ParticleTypes.SMALL_FLAME;
                        default -> ParticleTypes.END_ROD;
                    };
                    level.addParticle(o, p[0], p[1], p[2], (r.nextDouble() - 0.5) * 0.06, 0.05 + r.nextDouble() * 0.06, (r.nextDouble() - 0.5) * 0.06);
                }
            }
            case "ev.ring" -> {
                float[] c = switch (v) {
                    case DEMON -> new float[]{0.88F, 0.25F, 1.0F};
                    case GAIA -> new float[]{1.0F, 0.62F, 0.16F};
                    case ANGEL -> new float[]{1.0F, 0.92F, 0.55F};
                    default -> new float[]{0.7F, 0.4F, 1.0F};
                };
                for (int i = 0; i < 36; i++) {
                    double a = i / 36.0 * Math.PI * 2.0;
                    level.addParticle(dust(c[0], c[1], c[2], 1.3F), e.getX(), e.getY() + 0.1, e.getZ(), Math.cos(a) * 0.22, 0.02, Math.sin(a) * 0.22);
                    if (i % 4 == 0) {
                        ParticleOptions spark = switch (v) {
                            case GAIA -> ParticleTypes.FLAME;
                            case DEMON -> ParticleTypes.REVERSE_PORTAL;
                            case ANGEL -> ParticleTypes.END_ROD;
                            default -> ParticleTypes.NOTE;
                        };
                        level.addParticle(spark, e.getX(), e.getY() + 0.1, e.getZ(), Math.cos(a) * 0.26, 0.04, Math.sin(a) * 0.26);
                    }
                }
            }
            case "ev.rays" -> {
                for (int i = 0; i < 14; i++) {
                    double a = r.nextDouble() * Math.PI * 2.0, d = r.nextDouble() * 0.45;
                    level.addParticle(ParticleTypes.END_ROD, e.getX() + Math.cos(a) * d, e.getY() + 0.1 + r.nextDouble() * h, e.getZ() + Math.sin(a) * d, 0.0, 0.12 + r.nextDouble() * 0.16, 0.0);
                }
                for (int i = 0; i < 6; i++) level.addParticle(dust(1.0F, 0.92F, 0.5F, 0.9F), e.getX() + (r.nextDouble() - 0.5) * 0.9, e.getY() + h * 0.5, e.getZ() + (r.nextDouble() - 0.5) * 0.9, 0.0, 0.2, 0.0);
            }
            case "ev.sparkle" -> {
                for (int i = 0; i < 6; i++) {
                    double[] p = at(e, (r.nextDouble() - 0.5) * 1.0, (r.nextDouble() - 0.5) * 1.4, h * (0.3 + r.nextDouble() * 0.8));
                    level.addParticle(i % 2 == 0 ? ParticleTypes.END_ROD : ParticleTypes.GLOW, p[0], p[1], p[2], 0.0, 0.02, 0.0);
                }
            }
            case "ev.feather" -> {
                for (int i = 0; i < 4; i++) {
                    double[] p = at(e, -0.3 - r.nextDouble() * 0.3, (r.nextDouble() - 0.5) * 2.2, h * (0.7 + r.nextDouble() * 0.5));
                    level.addParticle(dust(i % 2 == 0 ? 1.0F : 1.0F, i % 2 == 0 ? 1.0F : 0.9F, i % 2 == 0 ? 0.95F : 0.55F, 1.0F), p[0], p[1], p[2], (r.nextDouble() - 0.5) * 0.04, -0.02, (r.nextDouble() - 0.5) * 0.04);
                }
            }
            case "ev.flame" -> {
                double[] p = at(e, 0.35, 0.0, h * 0.86);
                double fxx = fx(e), fzz = fz(e);
                for (int i = 0; i < 4; i++) {
                    double sp = 0.28 + r.nextDouble() * 0.22;
                    level.addParticle(i == 0 ? ParticleTypes.FLAME : ParticleTypes.SMALL_FLAME, p[0], p[1], p[2], fxx * sp + (r.nextDouble() - 0.5) * 0.08, 0.02 + r.nextDouble() * 0.04, fzz * sp + (r.nextDouble() - 0.5) * 0.08);
                }
                level.addParticle(ParticleTypes.SMOKE, p[0], p[1], p[2], fxx * 0.15, 0.05, fzz * 0.15);
            }
            default -> { }
        }
    }

    // ------------------------------------------------------------------------------------------------ ambient shimmer
    private static void ambient(HelperEntity e, HelperVariant v) {
        Level level = e.level();
        RandomSource r = e.getRandom();
        double h = e.getBbHeight();
        int t = e.tickCount;
        switch (v) {
            case DEMON -> {
                if (t % 8 == 0 && r.nextInt(3) == 0) {
                    double[] p = at(e, -0.25, (r.nextBoolean() ? 1 : -1) * (0.3 + r.nextDouble() * 0.6), h * (0.55 + r.nextDouble() * 0.4));
                    level.addParticle(ParticleTypes.REVERSE_PORTAL, p[0], p[1], p[2], 0.0, 0.03, 0.0);
                }
            }
            case ANGEL -> {
                if (t % 5 == 0 && r.nextInt(2) == 0) {
                    double[] p = at(e, -0.3, (r.nextDouble() - 0.5) * 1.8, h * (0.5 + r.nextDouble() * 0.5));
                    level.addParticle(dust(1.0F, 0.93F, 0.6F, 0.7F), p[0], p[1], p[2], 0.0, -0.015, 0.0);
                }
                if (t % 40 == 0) {
                    double[] p = at(e, 0.0, (r.nextDouble() - 0.5) * 0.9, h + 0.2);
                    level.addParticle(ParticleTypes.END_ROD, p[0], p[1], p[2], 0.0, 0.01, 0.0);
                }
            }
            case GAIA -> {
                if (t % 10 == 0 && r.nextInt(3) == 0) {
                    double[] p = at(e, -0.3, (r.nextBoolean() ? 1 : -1) * (0.3 + r.nextDouble() * 0.7), h * (0.5 + r.nextDouble() * 0.5));
                    level.addParticle(dust(1.0F, 0.5F, 0.12F, 0.7F), p[0], p[1], p[2], 0.0, 0.04, 0.0);
                }
            }
            case JESTER -> {
                if (t % 30 == 0 && r.nextInt(2) == 0) {
                    double[] p = at(e, 0.0, (r.nextBoolean() ? 1 : -1) * (0.5 + r.nextDouble() * 0.5), h + 0.05);
                    level.addParticle(dust(1.0F, 0.85F, 0.3F, 0.6F), p[0], p[1], p[2], 0.0, 0.02, 0.0);
                }
            }
            default -> { }
        }
    }
}
