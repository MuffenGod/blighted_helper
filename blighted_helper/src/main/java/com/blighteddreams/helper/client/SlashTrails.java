package com.blighteddreams.helper.client;

import com.blighteddreams.helper.HelperEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;

/**
 * Glowing slash trails for Emily's swings: a crescent that sweeps after the blade and fades through the
 * follow-through, a streak for thrusts and jabs, a ring for spinning attacks, and a ground shockwave for slams.
 * Drawn additively (the lightning render type), so they glow at night and stay subtle in daylight.
 * Frame: origin at her feet, -z forward, +x her right, +y up.
 */
public final class SlashTrails {
    private static final int SEGMENTS = 22;

    private SlashTrails() {}

    public static void render(HelperEntity e, float partial, PoseStack ps, MultiBufferSource buf) {
        if (e.isDowned()) return;

        boolean hasCast = e.animCastStyle != HelperCasts.STYLE_NONE;
        float pSwing = (e.tickCount + partial - e.animSwingStartTick) / HelperSwings.ticks(e.animSwingKind);
        boolean hasSwing = e.aim() == HelperEntity.AIM_NONE && pSwing >= HelperSwings.A && pSwing < 1.0F;

        if (!hasCast && !hasSwing) return;

        ps.pushPose();
        ps.mulPose(Axis.YP.rotationDegrees(180.0F - Mth.rotLerp(partial, e.yBodyRotO, e.yBodyRot)));
        VertexConsumer vc = buf.getBuffer(RenderType.lightning());
        PoseStack.Pose pose = ps.last();

        if (hasCast) {
            renderCastVisuals(pose, vc, e, partial);
        }

        if (hasSwing) {
            HelperSwings.Swing s = HelperSwings.get(e.animSwingKind, e.animSwingVariant);
            HelperSwings.Trail t = s.trail();
            int rgb = e.animSwingKind.color;
            float r = ((rgb >> 16) & 255) / 255F, g = ((rgb >> 8) & 255) / 255F, b = (rgb & 255) / 255F;
            float alpha = HelperSwings.trailAlpha(pSwing);
            float head = HelperSwings.strike(pSwing);

            renderTrail(pose, vc, t, pSwing, head, alpha, r, g, b);

            if (s.offhandTrail() != null) {
                float pOff = HelperSwings.clamp01(pSwing - s.leftDelay());
                if (pOff >= HelperSwings.A && pOff < 1) {
                    float headOff = HelperSwings.strike(pOff);
                    float alphaOff = HelperSwings.trailAlpha(pOff);
                    // Complementary accented tint for offhand blade
                    float rOff = b * 0.8F + 0.2F * r;
                    float gOff = g * 0.85F + 0.15F * b;
                    float bOff = r * 0.85F + 0.15F * b;
                    renderTrail(pose, vc, s.offhandTrail(), pOff, headOff, alphaOff, rOff, gOff, bOff);
                }
            }

            if (s.groundShock() && pSwing >= HelperSwings.B) {
                float q = HelperSwings.clamp01((pSwing - HelperSwings.B) / (1 - HelperSwings.B));
                float radius = 0.5F + 2.6F * HelperSwings.easeOutQuad(q);
                ring(pose, vc, 0.06F, radius, 0.35F, (1 - q) * 0.9F, r, g, b);
                ring(pose, vc, 0.08F, radius * 0.6F, 0.15F, (1 - q) * 0.6F, 1, 1, 1);
            }
        }

        ps.popPose();
    }

    private static void renderTrail(PoseStack.Pose pose, VertexConsumer vc, HelperSwings.Trail t, float p, float head, float alpha, float r, float g, float b) {
        if (t == null) return;
        switch (t.type()) {
            case ARC -> arc(pose, vc, t, t.from(), t.to(), head, alpha, r, g, b);
            case THRUST -> thrust(pose, vc, t, head, alpha, r, g, b);
            case ARC_RING -> {
                float spin = HelperSwings.easeInOutSine(HelperSwings.clamp01((p - HelperSwings.A) / (HelperSwings.C - HelperSwings.A)));
                float fade = 1 - HelperSwings.clamp01((p - HelperSwings.C) / (1 - HelperSwings.C));
                float from = t.from() != 0 || t.to() != 0 ? t.from() : -90.0F;
                float to = t.from() != 0 || t.to() != 0 ? t.to() : 270.0F;
                arc(pose, vc, t, from, to, spin, fade, r, g, b);
            }
            case RING -> ring(pose, vc, t.height(), t.inner() + (t.outer() - t.inner()) * head, 0.25F, alpha, r, g, b);
            default -> { }
        }
    }

    /** Point on the arc plane: angle in degrees (0 ahead, + right), tilted by the trail's roll, at radius rad. */
    private static float[] at(HelperSwings.Trail t, float angleDeg, float rad) {
        float a = angleDeg * Mth.DEG_TO_RAD;
        float roll = t.roll() * Mth.DEG_TO_RAD;
        float side = Mth.sin(a) * rad;
        return new float[]{t.side() + side * Mth.cos(roll), t.height() + side * Mth.sin(roll), -0.15F - Mth.cos(a) * rad};
    }

    /** A crescent from the tail of the sweep up to its head; brightest at the head, with a white-hot outer edge. */
    private static void arc(PoseStack.Pose pose, VertexConsumer vc, HelperSwings.Trail t, float from, float to, float head,
                            float alpha, float r, float g, float b) {
        if (alpha <= 0.01F || head <= 0.01F) return;
        float tail = Math.max(0.0F, head - 0.62F);
        for (int i = 0; i < SEGMENTS; i++) {
            float u0 = tail + (head - tail) * i / SEGMENTS;
            float u1 = tail + (head - tail) * (i + 1) / SEGMENTS;
            float w0 = (float) i / SEGMENTS, w1 = (float) (i + 1) / SEGMENTS;
            float a0 = alpha * w0 * w0 * w0, a1 = alpha * w1 * w1 * w1;
            float ang0 = Mth.lerp(u0, from, to), ang1 = Mth.lerp(u1, from, to);
            // the blade sweeps a wedge that is thin at the tail and full at the head
            float in0 = Mth.lerp(w0, t.outer() - 0.15F, t.inner()), in1 = Mth.lerp(w1, t.outer() - 0.15F, t.inner());
            quad(pose, vc, at(t, ang0, in0), at(t, ang1, in1), at(t, ang1, t.outer()), at(t, ang0, t.outer()),
                    r, g, b, a0 * 0.55F, a1 * 0.55F);
            float edge = t.outer() - 0.07F;
            quad(pose, vc, at(t, ang0, edge), at(t, ang1, edge), at(t, ang1, t.outer() + 0.02F), at(t, ang0, t.outer() + 0.02F),
                    1, 1, 1, a0 * 0.9F, a1 * 0.9F);
        }
    }

    /** A tapered streak straight ahead, as two crossed ribbons so it reads from every side. */
    private static void thrust(PoseStack.Pose pose, VertexConsumer vc, HelperSwings.Trail t, float head, float alpha,
                               float r, float g, float b) {
        if (alpha <= 0.01F) return;
        float z0 = -0.35F, z1 = -0.35F - t.outer() * head;
        float zt = Mth.lerp(0.55F, z0, z1);
        float x = t.side(), y = t.height(), w = 0.09F;
        quad(pose, vc, new float[]{x - w * 0.2F, y, zt}, new float[]{x, y, z1}, new float[]{x, y, z1}, new float[]{x + w * 0.2F, y, zt},
                1, 1, 1, alpha, alpha);
        quad(pose, vc, new float[]{x - w, y, z0}, new float[]{x, y, z1}, new float[]{x, y, z1}, new float[]{x + w, y, z0},
                r, g, b, 0.0F, alpha * 0.8F);
        quad(pose, vc, new float[]{x, y - w, z0}, new float[]{x, y, z1}, new float[]{x, y, z1}, new float[]{x, y + w, z0},
                r, g, b, 0.0F, alpha * 0.8F);
    }

    /** A flat ring at a height, fading toward its inner edge. */
    private static void ring(PoseStack.Pose pose, VertexConsumer vc, float y, float radius, float width, float alpha,
                             float r, float g, float b) {
        if (alpha <= 0.01F) return;
        int n = 32;
        float in = Math.max(0.0F, radius - width);
        for (int i = 0; i < n; i++) {
            float a0 = Mth.TWO_PI * i / n, a1 = Mth.TWO_PI * (i + 1) / n;
            float[] p0 = {Mth.sin(a0) * in, y, Mth.cos(a0) * in};
            float[] p1 = {Mth.sin(a1) * in, y, Mth.cos(a1) * in};
            float[] p2 = {Mth.sin(a1) * radius, y, Mth.cos(a1) * radius};
            float[] p3 = {Mth.sin(a0) * radius, y, Mth.cos(a0) * radius};
            vertex(pose, vc, p0, r, g, b, 0);
            vertex(pose, vc, p1, r, g, b, 0);
            vertex(pose, vc, p2, r, g, b, alpha);
            vertex(pose, vc, p3, r, g, b, alpha);
            vertex(pose, vc, p3, r, g, b, alpha);
            vertex(pose, vc, p2, r, g, b, alpha);
            vertex(pose, vc, p1, r, g, b, 0);
            vertex(pose, vc, p0, r, g, b, 0);
        }
    }

    /** Double-sided quad p0-p1-p2-p3; p0/p3 take alpha a0 and p1/p2 take a1. */
    private static void quad(PoseStack.Pose pose, VertexConsumer vc, float[] p0, float[] p1, float[] p2, float[] p3,
                             float r, float g, float b, float a0, float a1) {
        vertex(pose, vc, p0, r, g, b, a0);
        vertex(pose, vc, p1, r, g, b, a1);
        vertex(pose, vc, p2, r, g, b, a1);
        vertex(pose, vc, p3, r, g, b, a0);
        vertex(pose, vc, p3, r, g, b, a0);
        vertex(pose, vc, p2, r, g, b, a1);
        vertex(pose, vc, p1, r, g, b, a1);
        vertex(pose, vc, p0, r, g, b, a0);
    }

    private static void vertex(PoseStack.Pose pose, VertexConsumer vc, float[] p, float r, float g, float b, float a) {
        vc.addVertex(pose, p[0], p[1], p[2]).setColor(r, g, b, Math.max(0.0F, Math.min(1.0F, a)));
    }

    public static void renderCastRing(PoseStack ps, MultiBufferSource buf, float radius, float alpha, float r, float g, float b) {
        if (alpha <= 0.01F) return;
        VertexConsumer vc = buf.getBuffer(RenderType.lightning());
        PoseStack.Pose pose = ps.last();
        ring(pose, vc, 0.04F, radius * 0.55F, 0.08F, alpha * 0.7F, r, g, b);
        ring(pose, vc, 0.05F, radius, 0.14F, alpha * 0.85F, r, g, b);
        ring(pose, vc, 0.06F, radius * 0.25F, 0.05F, alpha * 0.9F, 1.0F, 1.0F, 1.0F);
    }

    private static void glowingDisc(PoseStack.Pose pose, VertexConsumer vc, float cx, float cy, float cz,
                                    float ux, float uy, float uz, float vx, float vy, float vz,
                                    float radius, float alpha, float r, float g, float b) {
        if (alpha <= 0.01F || radius <= 0.001F) return;
        int n = 20;
        float[] center = {cx, cy, cz};
        for (int i = 0; i < n; i++) {
            float a0 = Mth.TWO_PI * i / n, a1 = Mth.TWO_PI * (i + 1) / n;
            float c0 = Mth.cos(a0) * radius, s0 = Mth.sin(a0) * radius;
            float c1 = Mth.cos(a1) * radius, s1 = Mth.sin(a1) * radius;
            float[] p0 = {cx + ux * c0 + vx * s0, cy + uy * c0 + vy * s0, cz + uz * c0 + vz * s0};
            float[] p1 = {cx + ux * c1 + vx * s1, cy + uy * c1 + vy * s1, cz + uz * c1 + vz * s1};
            vertex(pose, vc, center, r, g, b, alpha);
            vertex(pose, vc, p0, r, g, b, 0.0F);
            vertex(pose, vc, p1, r, g, b, 0.0F);
            vertex(pose, vc, p1, r, g, b, 0.0F);

            vertex(pose, vc, center, r, g, b, alpha);
            vertex(pose, vc, p1, r, g, b, 0.0F);
            vertex(pose, vc, p0, r, g, b, 0.0F);
            vertex(pose, vc, p0, r, g, b, 0.0F);
        }
    }

    private static void orientedRing(PoseStack.Pose pose, VertexConsumer vc, float cx, float cy, float cz,
                                     float ux, float uy, float uz, float vx, float vy, float vz,
                                     float radius, float width, float alpha, float r, float g, float b) {
        if (alpha <= 0.01F || radius <= 0.001F) return;
        int n = 24;
        float in = Math.max(0.0F, radius - width);
        for (int i = 0; i < n; i++) {
            float a0 = Mth.TWO_PI * i / n, a1 = Mth.TWO_PI * (i + 1) / n;
            float c0 = Mth.cos(a0), s0 = Mth.sin(a0);
            float c1 = Mth.cos(a1), s1 = Mth.sin(a1);
            float[] p0 = {cx + ux * (c0 * in) + vx * (s0 * in), cy + uy * (c0 * in) + vy * (s0 * in), cz + uz * (c0 * in) + vz * (s0 * in)};
            float[] p1 = {cx + ux * (c1 * in) + vx * (s1 * in), cy + uy * (c1 * in) + vy * (s1 * in), cz + uz * (c1 * in) + vz * (s1 * in)};
            float[] p2 = {cx + ux * (c1 * radius) + vx * (s1 * radius), cy + uy * (c1 * radius) + vy * (s1 * radius), cz + uz * (c1 * radius) + vz * (s1 * radius)};
            float[] p3 = {cx + ux * (c0 * radius) + vx * (s0 * radius), cy + uy * (c0 * radius) + vy * (s0 * radius), cz + uz * (c0 * radius) + vz * (s0 * radius)};
            quad(pose, vc, p0, p1, p2, p3, r, g, b, 0.0F, alpha);
        }
    }

    private static void renderCastVisuals(PoseStack.Pose pose, VertexConsumer vc, HelperEntity e, float partial) {
        float castTime = e.tickCount + partial - e.animCastStartTick;
        float totalTicks = Math.max(6, e.animCastTicks);
        float p = castTime / totalTicks;
        if (p < 0.0F || p > 1.35F) return;

        float time = e.tickCount + partial;

        SpellSchool school = SpellSchool.byId(e.animCastSchool);
        if (school == SpellSchool.NONE) {
            school = SpellSchool.byId(e.defaultSchoolFor(e.animCastStyle));
        }

        float sr = school.r, sg = school.g, sb = school.b;
        float ar, ag, ab;
        switch (school) {
            case FIRE -> { ar = 1.0F; ag = 0.95F; ab = 0.75F; } // hot white-gold
            case HOLY -> { ar = 1.0F; ag = 0.98F; ab = 0.70F; } // diamond gold
            case ICE -> { ar = 0.90F; ag = 0.98F; ab = 1.0F; } // diamond frost white
            case LIGHTNING -> { ar = 0.85F; ag = 0.55F; ab = 1.0F; } // violet plasma
            case ENDER -> { ar = 0.95F; ag = 0.20F; ab = 0.85F; } // magenta rift
            case BLOOD -> { ar = 1.0F; ag = 0.22F; ab = 0.35F; } // bright ruby
            case EVOCATION -> { ar = 0.50F; ag = 1.0F; ab = 0.78F; } // prismatic cyan-lime
            case NATURE -> { ar = 0.85F; ag = 0.95F; ab = 0.30F; } // bloom gold-green
            case ELDRITCH -> { ar = 0.40F; ag = 0.95F; ab = 0.95F; } // ghostly cyan
            case COSMIC -> { ar = 0.95F; ag = 0.45F; ab = 0.98F; } // starlight magenta
            case HYDRO -> { ar = 0.50F; ag = 0.95F; ab = 1.0F; } // cresting wave white
            default -> { ar = 1.0F; ag = 1.0F; ab = 1.0F; }
        }

        switch (e.animCastStyle) {
            case HelperCasts.STYLE_CHARGE -> { // ---------------- Elemental Plasma Sphere / Meteor Launch
                float cz = -0.42F;
                float cy = 1.22F;
                float cx = 0.0F;

                if (p < 0.65F) {
                    float gather = p / 0.65F;
                    float pulse = school == SpellSchool.LIGHTNING
                            ? Mth.sin(time * 3.5F) * 0.05F * gather
                            : (school == SpellSchool.ENDER
                                    ? -Math.abs(Mth.sin(time * 1.5F)) * 0.04F * gather
                                    : Mth.sin(time * 0.85F) * 0.03F * gather);
                    float radius = 0.06F + 0.26F * HelperSwings.easeOutQuad(gather) + pulse;
                    float alpha = gather * 0.95F;

                    // Core tri-planar discs
                    glowingDisc(pose, vc, cx, cy, cz, 1, 0, 0, 0, 1, 0, radius, alpha * 0.85F, sr, sg, sb);
                    glowingDisc(pose, vc, cx, cy, cz, 1, 0, 0, 0, 0, 1, radius, alpha * 0.85F, sr, sg, sb);
                    glowingDisc(pose, vc, cx, cy, cz, 0, 1, 0, 0, 0, 1, radius, alpha * 0.85F, sr, sg, sb);
                    // High-intensity hot nucleus
                    glowingDisc(pose, vc, cx, cy, cz, 1, 0, 0, 0, 1, 0, radius * 0.55F, alpha, ar, ag, ab);
                    glowingDisc(pose, vc, cx, cy, cz, 1, 0, 0, 0, 0, 1, radius * 0.55F, alpha, ar, ag, ab);

                    // Tilted orbiting corona rings
                    float a1 = time * 0.22F;
                    float ca1 = Mth.cos(a1), sa1 = Mth.sin(a1);
                    float sq2 = 0.7071F;
                    orientedRing(pose, vc, cx, cy, cz, ca1, sa1 * sq2, sa1 * sq2, -sa1, ca1 * sq2, ca1 * sq2,
                            radius * 1.35F, 0.05F, alpha * 0.9F, ar, ag, ab);
                    float a2 = -time * 0.28F;
                    float ca2 = Mth.cos(a2), sa2 = Mth.sin(a2);
                    orientedRing(pose, vc, cx, cy, cz, ca2 * sq2, -ca2 * sq2, sa2, -sa2 * sq2, sa2 * sq2, ca2,
                            radius * 1.20F, 0.04F, alpha * 0.8F, sr, sg, sb);

                    // Cosmic school special: wide Saturn-like planetary ring with star nodes
                    if (school == SpellSchool.COSMIC) {
                        float a3 = time * 0.12F;
                        float ca3 = Mth.cos(a3), sa3 = Mth.sin(a3);
                        orientedRing(pose, vc, cx, cy, cz, ca3, sa3 * 0.4F, sa3 * 0.9F, -sa3, ca3 * 0.4F, ca3 * 0.9F,
                                radius * 1.75F, 0.08F, alpha * 0.95F, 0.95F, 0.80F, 1.0F);
                    } else if (school == SpellSchool.ICE) {
                        // 6-pointed frost spikes
                        for (int k = 0; k < 6; k++) {
                            float fa = time * 0.15F + k * (Mth.TWO_PI / 6.0F);
                            float fx0 = cx + Mth.cos(fa) * (radius * 1.05F);
                            float fy0 = cy + Mth.sin(fa) * (radius * 1.05F);
                            float fx1 = cx + Mth.cos(fa) * (radius * 1.45F);
                            float fy1 = cy + Mth.sin(fa) * (radius * 1.45F);
                            quad(pose, vc,
                                    new float[]{fx0 - 0.02F, fy0, cz},
                                    new float[]{fx1, fy1, cz},
                                    new float[]{fx1, fy1, cz},
                                    new float[]{fx0 + 0.02F, fy0, cz},
                                    ar, ag, ab, alpha * 0.9F, 0.0F);
                        }
                    }

                    // Inward spiral gathering arcs
                    for (int s = 0; s < 3; s++) {
                        float spiralAngle = time * 0.3F + s * (Mth.TWO_PI / 3.0F);
                        float sRadius = Mth.lerp(1.0F - gather, radius, 0.85F);
                        float sx = cx + Mth.cos(spiralAngle) * sRadius;
                        float sy = cy + Mth.sin(spiralAngle * 0.7F) * (sRadius * 0.4F);
                        float sz = cz + Mth.sin(spiralAngle) * (sRadius * 0.6F);
                        quad(pose, vc,
                                new float[]{sx - 0.02F, sy - 0.02F, sz},
                                new float[]{cx, cy, cz},
                                new float[]{cx, cy, cz},
                                new float[]{sx + 0.02F, sy + 0.02F, sz},
                                sr, sg, sb, 0.0F, alpha * 0.55F);
                    }
                } else {
                    // Phase 2: Forward projectile launch & comet streak
                    float rel = HelperSwings.clamp01((p - 0.65F) / 0.35F);
                    float launchDist = 3.6F * HelperSwings.easeOutExpo(rel);
                    float fz = cz - launchDist;
                    float fade = 1.0F - HelperSwings.clamp01((p - 0.75F) / 0.6F);
                    float fRadius = 0.30F * fade;

                    if (fRadius > 0.01F) {
                        glowingDisc(pose, vc, cx, cy, fz, 1, 0, 0, 0, 1, 0, fRadius, fade * 0.9F, sr, sg, sb);
                        glowingDisc(pose, vc, cx, cy, fz, 1, 0, 0, 0, 0, 1, fRadius, fade * 0.9F, sr, sg, sb);
                        glowingDisc(pose, vc, cx, cy, fz, 1, 0, 0, 0, 1, 0, fRadius * 0.5F, fade, ar, ag, ab);

                        // Comet streak
                        quad(pose, vc,
                                new float[]{cx - fRadius * 0.5F, cy, cz},
                                new float[]{cx, cy, fz},
                                new float[]{cx, cy, fz},
                                new float[]{cx + fRadius * 0.5F, cy, cz},
                                sr, sg, sb, 0.0F, fade * 0.85F);
                        quad(pose, vc,
                                new float[]{cx, cy - fRadius * 0.5F, cz},
                                new float[]{cx, cy, fz},
                                new float[]{cx, cy, fz},
                                new float[]{cx, cy + fRadius * 0.5F, cz},
                                ar, ag, ab, 0.0F, fade * 0.85F);
                    }

                    // Detonation ring at origin
                    float ringRad = 0.25F + 1.4F * HelperSwings.easeOutQuad(rel);
                    float ringA = (1.0F - rel) * 0.9F;
                    orientedRing(pose, vc, cx, cy, cz, 1, 0, 0, 0, 1, 0, ringRad, 0.12F, ringA, sr, sg, sb);
                }
            }

            case HelperCasts.STYLE_OVERHEAD -> { // ---------------- Magic Circle & Ascending Radiance
                float auraAlpha = p < 0.25F ? (p / 0.25F) : (p < 0.85F ? 1.0F : (1.0F - (p - 0.85F) / 0.5F));
                if (auraAlpha <= 0.01F) return;

                // 1. Ground Magic Circle with School-Specific Glyphs
                float groundRad = 1.45F;
                ring(pose, vc, 0.04F, groundRad, 0.16F, auraAlpha * 0.85F, sr, sg, sb);
                ring(pose, vc, 0.05F, 0.75F, 0.08F, auraAlpha * 0.95F, ar, ag, ab);

                // Rotating glyph spokes on the ground
                float rot = time * 0.04F;
                int spokeCount = school == SpellSchool.ICE ? 6 : (school == SpellSchool.COSMIC ? 12 : 8);
                for (int ray = 0; ray < spokeCount; ray++) {
                    float ra = rot + ray * (Mth.TWO_PI / (float) spokeCount);
                    float cosR = Mth.cos(ra), sinR = Mth.sin(ra);
                    float r0 = 0.75F, r1 = groundRad;
                    float rw = 0.035F;
                    float px = -sinR * rw, pz = cosR * rw;
                    quad(pose, vc,
                            new float[]{cosR * r0 - px, 0.045F, sinR * r0 - pz},
                            new float[]{cosR * r1 - px, 0.045F, sinR * r1 - pz},
                            new float[]{cosR * r1 + px, 0.045F, sinR * r1 + pz},
                            new float[]{cosR * r0 + px, 0.045F, sinR * r0 + pz},
                            ar, ag, ab, auraAlpha * 0.75F, auraAlpha * 0.75F);
                }

                // 2. Ascending Divine / Elemental Radiance Rings
                for (int i = 0; i < 3; i++) {
                    float ringPhase = ((time * 0.06F + i * 0.333F) % 1.0F);
                    float ringY = 0.08F + ringPhase * 2.35F;
                    float ringR = 0.75F + ringPhase * 0.45F;
                    float ringA = Mth.sin(ringPhase * (float) Math.PI) * auraAlpha * 0.85F;
                    ring(pose, vc, ringY, ringR, 0.12F, ringA, sr, sg, sb);
                }

                // 3. Floating Halo / Crown hovering above Emily's head
                float haloY = 2.18F + Mth.sin(time * 0.15F) * 0.03F;
                ring(pose, vc, haloY, 0.34F, 0.06F, auraAlpha * 0.95F, ar, ag, ab);
                ring(pose, vc, haloY, 0.30F, 0.03F, auraAlpha * 0.80F, sr, sg, sb);

                // 4. Luminous Vertical Radiance Walls
                for (int wall = 0; wall < 6; wall++) {
                    float wa = wall * (Mth.TWO_PI / 6.0F) + time * 0.02F;
                    float cosW = Mth.cos(wa) * 0.85F, sinW = Mth.sin(wa) * 0.85F;
                    float nextW = (wall + 1) * (Mth.TWO_PI / 6.0F) + time * 0.02F;
                    float nextCos = Mth.cos(nextW) * 0.85F, nextSin = Mth.sin(nextW) * 0.85F;
                    quad(pose, vc,
                            new float[]{cosW, 0.05F, sinW},
                            new float[]{cosW, 2.45F, sinW},
                            new float[]{nextCos, 2.45F, nextSin},
                            new float[]{nextCos, 0.05F, nextSin},
                            ar, ag, ab, auraAlpha * 0.25F, auraAlpha * 0.10F);
                }
            }

            case HelperCasts.STYLE_SHIELD -> { // ---------------- Prismatic Elemental Barrier Aegis
                float shieldAlpha = p < 0.2F ? (p / 0.2F) : (p < 0.8F ? 1.0F : (1.0F - (p - 0.8F) / 0.55F));
                if (shieldAlpha <= 0.01F) return;
                float sz = -0.65F, sy = 1.15F, sx = 0.0F;
                float barrierRad = 0.85F;
                orientedRing(pose, vc, sx, sy, sz, 1, 0, 0, 0, 1, 0, barrierRad, 0.14F, shieldAlpha * 0.85F, sr, sg, sb);
                orientedRing(pose, vc, sx, sy, sz, 1, 0, 0, 0, 1, 0, barrierRad * 0.65F, 0.08F, shieldAlpha * 0.75F, ar, ag, ab);
                glowingDisc(pose, vc, sx, sy, sz, 1, 0, 0, 0, 1, 0, barrierRad * 0.9F, shieldAlpha * 0.30F, sr, sg, sb);
            }

            case HelperCasts.STYLE_POINT, HelperCasts.STYLE_CHANNEL -> { // ---------------- Focused Elemental Needle / Ray
                float pointP = HelperSwings.clamp01(p / 0.75F);
                float pointFade = 1.0F - HelperSwings.clamp01((p - 0.6F) / 0.6F);
                if (pointFade <= 0.01F) return;
                float px = 0.20F, py = 1.25F, pz0 = -0.3F;
                float beamLen = 2.6F * HelperSwings.easeOutExpo(pointP);
                quad(pose, vc,
                        new float[]{px - 0.05F, py, pz0},
                        new float[]{px, py, pz0 - beamLen},
                        new float[]{px, py, pz0 - beamLen},
                        new float[]{px + 0.05F, py, pz0},
                        sr, sg, sb, 0.0F, pointFade * 0.9F);
                quad(pose, vc,
                        new float[]{px, py - 0.05F, pz0},
                        new float[]{px, py, pz0 - beamLen},
                        new float[]{px, py, pz0 - beamLen},
                        new float[]{px, py + 0.05F, pz0},
                        ar, ag, ab, 0.0F, pointFade);
            }

            case HelperCasts.STYLE_SLAM -> { // ---------------- Seismic Impact Rings
                float slamP = HelperSwings.clamp01((p - 0.45F) / 0.55F);
                float slamFade = 1.0F - slamP;
                if (slamFade <= 0.01F) return;
                float slamRad = 0.5F + 2.8F * HelperSwings.easeOutQuad(slamP);
                ring(pose, vc, 0.05F, slamRad, 0.32F, slamFade * 0.9F, sr, sg, sb);
                ring(pose, vc, 0.06F, slamRad * 0.65F, 0.15F, slamFade * 0.95F, ar, ag, ab);
            }

            default -> { }
        }
    }
}
