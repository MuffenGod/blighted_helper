package com.blighteddreams.helper.client;

import com.blighteddreams.helper.HelperEntity;
import net.minecraft.util.Mth;

/**
 * Emily's spellcasting animations:
 *  - 6 distinct casting styles: POINT, CHARGE, OVERHEAD, SLAM, CHANNEL, SHIELD
 *  - 2 to 3 sub-variants per style for dynamic, natural combat variety
 *  - 3 phases per cast: GATHER (scaled to actual spell castTicks), RELEASE (moment spell fires), RECOVER (settle)
 *  - Upper-body layering so Emily can cast spells while walking or running without foot sliding
 *
 * Pose arrays (12 floats):
 *  0: torso yaw (radians, relative)
 *  1: torso pitch (radians, relative)
 *  2: right arm x (pitch)
 *  3: right arm y (yaw)
 *  4: right arm z (roll)
 *  5: right arm lower (forearm pitch)
 *  6: left arm x
 *  7: left arm y
 *  8: left arm z
 *  9: left arm lower
 *  10: lunge (pixels)
 *  11: crouch (pixels)
 */
public final class HelperCasts {
    private HelperCasts() {}

    public static final int STYLE_NONE = 0;
    public static final int STYLE_POINT = 1;
    public static final int STYLE_CHARGE = 2;
    public static final int STYLE_OVERHEAD = 3;
    public static final int STYLE_SLAM = 4;
    public static final int STYLE_CHANNEL = 5;
    public static final int STYLE_SHIELD = 6;

    public record CastPose(float[] gather, float[] release, float[] recover, float wingFlare, boolean groundRing) {}

    private static float[] k(float... v) {
        return v;
    }

    // ---------------------------------------------------------------------------------- 1. POINT (Fast projectile / smite)
    private static final CastPose[] POINT = {
            // Variant 0: Right hand forward thrust point, left arm balanced back
            new CastPose(
                    k(0.25F, -0.05F, -0.6F, 0.2F, 0.1F, -1.2F, 0.3F, -0.15F, -0.2F, -0.3F, 0.0F, 0.4F),
                    k(-0.2F, 0.05F, -1.55F, -0.05F, 0.0F, -0.05F, 0.5F, -0.2F, -0.3F, -0.2F, 2.5F, 0.6F),
                    k(-0.15F, 0.05F, -1.45F, 0.0F, 0.0F, -0.1F, 0.4F, -0.2F, -0.25F, -0.2F, 1.8F, 0.4F),
                    0.25F, false
            ),
            // Variant 1: Left off-hand snap point (keeps weapon in right hand lowered)
            new CastPose(
                    k(-0.2F, -0.05F, -0.4F, 0.2F, 0.15F, -0.5F, -0.7F, -0.15F, -0.1F, -1.3F, 0.0F, 0.4F),
                    k(0.25F, 0.05F, -0.3F, 0.2F, 0.15F, -0.5F, -1.55F, 0.05F, 0.0F, -0.05F, 2.2F, 0.6F),
                    k(0.2F, 0.05F, -0.35F, 0.2F, 0.15F, -0.5F, -1.45F, 0.0F, 0.0F, -0.1F, 1.6F, 0.4F),
                    0.25F, false
            ),
            // Variant 2: Two-handed focused guiding point
            new CastPose(
                    k(0.0F, -0.08F, -0.8F, -0.15F, 0.1F, -1.1F, -0.8F, 0.15F, -0.1F, -1.1F, 0.0F, 0.5F),
                    k(0.0F, 0.08F, -1.5F, -0.05F, 0.0F, -0.1F, -1.5F, 0.05F, 0.0F, -0.1F, 2.0F, 0.8F),
                    k(0.0F, 0.06F, -1.4F, -0.05F, 0.0F, -0.15F, -1.4F, 0.05F, 0.0F, -0.15F, 1.4F, 0.5F),
                    0.35F, false
            )
    };

    // ---------------------------------------------------------------------------------- 2. CHARGE (Fireball / Energy blast)
    private static final CastPose[] CHARGE = {
            // Variant 0: Dual palm chest gather into explosive twin thrust
            new CastPose(
                    k(0.0F, -0.12F, -0.9F, 0.45F, 0.25F, -1.4F, -0.9F, -0.45F, -0.25F, -1.4F, -0.5F, 1.0F),
                    k(0.0F, 0.15F, -1.45F, 0.08F, 0.0F, -0.05F, -1.45F, -0.08F, 0.0F, -0.05F, 3.2F, 1.2F),
                    k(0.0F, 0.1F, -1.35F, 0.12F, 0.05F, -0.15F, -1.35F, -0.12F, -0.05F, -0.15F, 2.4F, 0.8F),
                    0.6F, false
            ),
            // Variant 1: One-handed overhead comet gather and forward hurl
            new CastPose(
                    k(0.4F, -0.18F, -2.4F, 0.3F, 0.2F, -0.8F, -0.5F, -0.2F, -0.1F, -0.6F, 0.0F, 0.8F),
                    k(-0.35F, 0.18F, -1.3F, -0.2F, 0.0F, -0.1F, 0.3F, 0.1F, -0.2F, -0.3F, 3.0F, 1.1F),
                    k(-0.4F, 0.15F, -1.2F, -0.25F, 0.0F, -0.15F, 0.3F, 0.1F, -0.2F, -0.3F, 2.2F, 0.7F),
                    0.55F, false
            ),
            // Variant 2: Low gathering cup rising forward
            new CastPose(
                    k(0.0F, 0.2F, -0.3F, 0.15F, 0.1F, -1.3F, -0.3F, -0.15F, -0.1F, -1.3F, 0.0F, 1.8F),
                    k(0.0F, -0.15F, -1.55F, 0.1F, 0.0F, -0.1F, -1.55F, -0.1F, 0.0F, -0.1F, 2.6F, 0.4F),
                    k(0.0F, -0.1F, -1.4F, 0.15F, 0.05F, -0.2F, -1.4F, -0.15F, -0.05F, -0.2F, 1.8F, 0.2F),
                    0.5F, false
            )
    };

    // ---------------------------------------------------------------------------------- 3. OVERHEAD (Holy Radiance / Heal)
    private static final CastPose[] OVERHEAD = {
            // Variant 0: Majestic V-raise with open wings and lifted chin
            new CastPose(
                    k(0.0F, -0.1F, -1.2F, 0.1F, 0.05F, -0.6F, -1.2F, -0.1F, -0.05F, -0.6F, 0.0F, 0.6F),
                    k(0.0F, -0.28F, -2.65F, 0.45F, 0.35F, -0.2F, -2.65F, -0.45F, -0.35F, -0.2F, 0.0F, -0.5F),
                    k(0.0F, -0.2F, -2.4F, 0.55F, 0.4F, -0.25F, -2.4F, -0.55F, -0.4F, -0.25F, 0.0F, -0.2F),
                    0.9F, false
            ),
            // Variant 1: Right hand skyward invoking light, left hand pointing toward beneficiary
            new CastPose(
                    k(-0.15F, -0.05F, -1.6F, 0.1F, 0.1F, -0.7F, -0.8F, 0.1F, -0.1F, -0.8F, 0.0F, 0.5F),
                    k(-0.25F, -0.22F, -2.7F, 0.25F, 0.2F, -0.15F, -1.4F, -0.1F, -0.1F, -0.2F, 1.5F, 0.2F),
                    k(-0.2F, -0.18F, -2.5F, 0.3F, 0.25F, -0.2F, -1.3F, -0.1F, -0.1F, -0.25F, 1.0F, 0.2F),
                    0.85F, false
            ),
            // Variant 2: Reverent hands to heart, radiating outward
            new CastPose(
                    k(0.0F, 0.08F, -0.9F, 0.5F, 0.2F, -1.7F, -0.9F, -0.5F, -0.2F, -1.7F, 0.0F, 0.9F),
                    k(0.0F, -0.2F, -2.1F, 0.6F, 0.45F, -0.3F, -2.1F, -0.6F, -0.45F, -0.3F, 0.0F, 0.1F),
                    k(0.0F, -0.15F, -1.8F, 0.65F, 0.4F, -0.4F, -1.8F, -0.65F, -0.4F, -0.4F, 0.0F, 0.3F),
                    0.8F, false
            )
    };

    // ---------------------------------------------------------------------------------- 4. SLAM (Ground impact / AoE)
    private static final CastPose[] SLAM = {
            // Variant 0: High leap/windup into downward single palm earth slam
            new CastPose(
                    k(0.3F, -0.25F, -2.5F, 0.2F, 0.15F, -0.4F, -0.6F, -0.2F, -0.1F, -0.8F, 0.0F, -0.4F),
                    k(-0.1F, 0.4F, -0.85F, 0.05F, 0.0F, -0.25F, -0.5F, -0.2F, -0.1F, -0.5F, 2.6F, 3.2F),
                    k(-0.1F, 0.4F, -0.8F, 0.05F, 0.0F, -0.2F, -0.5F, -0.2F, -0.1F, -0.5F, 2.6F, 3.3F),
                    0.45F, true
            ),
            // Variant 1: Two-handed weapon / staff ground plant
            new CastPose(
                    k(0.0F, -0.35F, -2.8F, 0.1F, 0.0F, -1.1F, -2.8F, -0.1F, 0.0F, -1.1F, 0.0F, -0.5F),
                    k(0.0F, 0.38F, -0.8F, 0.04F, 0.0F, -0.2F, -0.8F, -0.04F, 0.0F, -0.2F, 2.8F, 3.0F),
                    k(0.0F, 0.38F, -0.75F, 0.04F, 0.0F, -0.18F, -0.75F, -0.04F, 0.0F, -0.18F, 2.8F, 3.1F),
                    0.55F, true
            )
    };

    // ---------------------------------------------------------------------------------- 5. CHANNEL (Continuous Ray / Beam)
    private static final CastPose[] CHANNEL = {
            // Variant 0: Two-handed focused energy beam with slight vibration
            new CastPose(
                    k(0.0F, -0.05F, -1.35F, 0.1F, 0.0F, -0.4F, -1.35F, -0.1F, 0.0F, -0.4F, 1.0F, 0.7F),
                    k(0.0F, 0.05F, -1.5F, 0.05F, 0.0F, -0.1F, -1.5F, -0.05F, 0.0F, -0.1F, 2.2F, 0.8F),
                    k(0.0F, 0.04F, -1.45F, 0.05F, 0.0F, -0.12F, -1.45F, -0.05F, 0.0F, -0.12F, 1.8F, 0.7F),
                    0.5F, false
            ),
            // Variant 1: Right hand beam with left hand bracing the right forearm
            new CastPose(
                    k(0.2F, -0.05F, -1.2F, 0.05F, 0.0F, -0.5F, -1.1F, 0.5F, 0.2F, -1.3F, 1.2F, 0.6F),
                    k(0.1F, 0.05F, -1.55F, 0.0F, 0.0F, -0.05F, -1.35F, 0.4F, 0.15F, -1.1F, 2.4F, 0.7F),
                    k(0.1F, 0.04F, -1.5F, 0.0F, 0.0F, -0.08F, -1.3F, 0.4F, 0.15F, -1.15F, 2.0F, 0.6F),
                    0.4F, false
            )
    };

    // ---------------------------------------------------------------------------------- 6. SHIELD (Protective Ward / Aegis)
    private static final CastPose[] SHIELD = {
            // Variant 0: Crossed forearms bursting into protective aegis
            new CastPose(
                    k(0.0F, 0.05F, -1.35F, -0.45F, 0.15F, -1.6F, -1.35F, 0.45F, -0.15F, -1.6F, 0.0F, 1.2F),
                    k(0.0F, -0.1F, -1.45F, 0.65F, 0.3F, -0.3F, -1.45F, -0.65F, -0.3F, -0.3F, 1.5F, 0.6F),
                    k(0.0F, -0.08F, -1.35F, 0.7F, 0.35F, -0.4F, -1.35F, -0.7F, -0.35F, -0.4F, 1.0F, 0.5F),
                    0.7F, true
            ),
            // Variant 1: Dual forward barrier palms
            new CastPose(
                    k(0.0F, -0.05F, -0.9F, 0.2F, 0.1F, -1.3F, -0.9F, -0.2F, -0.1F, -1.3F, -0.3F, 0.8F),
                    k(0.0F, 0.12F, -1.5F, 0.1F, 0.0F, -0.05F, -1.5F, -0.1F, 0.0F, -0.05F, 2.2F, 1.1F),
                    k(0.0F, 0.08F, -1.4F, 0.12F, 0.0F, -0.1F, -1.4F, -0.12F, 0.0F, -0.1F, 1.6F, 0.8F),
                    0.6F, true
            )
    };

    public static CastPose get(int style, int variant) {
        CastPose[] set = switch (style) {
            case STYLE_POINT -> POINT;
            case STYLE_CHARGE -> CHARGE;
            case STYLE_OVERHEAD -> OVERHEAD;
            case STYLE_SLAM -> SLAM;
            case STYLE_CHANNEL -> CHANNEL;
            case STYLE_SHIELD -> SHIELD;
            default -> POINT;
        };
        return set[Math.floorMod(variant, set.length)];
    }
}
