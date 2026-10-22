package com.blighteddreams.helper.client;

import com.blighteddreams.helper.WeaponKind;

/**
 * Emily's melee swings, made for her model (not Better Combat's). Every weapon kind has a three-hit combo; each hit is
 * three key poses (wind-up, strike, follow-through) that the model blends from and back to her current pose, plus the
 * shape of the slash trail drawn by {@link SlashTrails}.
 *
 * Pose values: torso yaw and pitch (added), right arm x/y/z and forearm, left arm x/y/z and forearm (absolute, radians),
 * lunge and crouch (pixels).
 */
public final class HelperSwings {
    private HelperSwings() {}

    /** Phase edges, as a fraction of the swing: wind-up ends at A, the strike lands by B, follow-through ends at C. */
    public static final float A = 0.2F, B = 0.42F, C = 0.7F;

    public enum TrailType { NONE, ARC, THRUST, RING, ARC_RING }

    /**
     * @param roll   tilt of the arc plane around her forward axis, degrees (0 flat, 90 vertical, + lifts her right side)
     * @param from   arc start angle, degrees (0 = straight ahead, + = to her right)
     * @param to     arc end angle
     * @param inner  inner radius, blocks
     * @param outer  outer radius, blocks
     * @param height arc centre height, blocks
     * @param side   sideways offset of the centre (+ = her right), blocks
     */
    public record Trail(TrailType type, float roll, float from, float to, float inner, float outer, float height, float side) {
        static Trail arc(float roll, float from, float to, float inner, float outer, float height) {
            return new Trail(TrailType.ARC, roll, from, to, inner, outer, height, 0.0F);
        }

        static Trail thrust(float height, float side, float length) {
            return new Trail(TrailType.THRUST, 0, 0, 0, 0.25F, length, height, side);
        }

        static Trail ring(float height, float radius) {
            return new Trail(TrailType.RING, 0, 0, 0, radius - 0.35F, radius, height, 0.0F);
        }
    }

    public record Swing(float[] windup, float[] strike, float[] follow, float spin, Trail trail, Trail offhandTrail, boolean groundShock, float leftDelay) {
        public Swing(float[] windup, float[] strike, float[] follow, float spin, Trail trail, boolean groundShock) {
            this(windup, strike, follow, spin, trail, null, groundShock, 0.0F);
        }
    }

    private static float[] k(float... v) {
        return v;
    }

    private static Swing s(float[] w, float[] st, float[] f, Trail t) {
        return new Swing(w, st, f, 0.0F, t, null, false, 0.0F);
    }

    private static Swing shock(float[] w, float[] st, float[] f, Trail t) {
        return new Swing(w, st, f, 0.0F, t, null, true, 0.0F);
    }

    private static Swing shock(float[] w, float[] st, float[] f, Trail t, Trail offTrail) {
        return new Swing(w, st, f, 0.0F, t, offTrail, true, 0.0F);
    }

    private static Swing shock(float[] w, float[] st, float[] f, Trail t, Trail offTrail, float leftDelay) {
        return new Swing(w, st, f, 0.0F, t, offTrail, true, leftDelay);
    }

    private static Swing s(float[] w, float[] st, float[] f, Trail t, Trail offTrail) {
        return new Swing(w, st, f, 0.0F, t, offTrail, false, 0.0F);
    }

    private static Swing s(float[] w, float[] st, float[] f, Trail t, Trail offTrail, float leftDelay) {
        return new Swing(w, st, f, 0.0F, t, offTrail, false, leftDelay);
    }

    private static final float TAU = (float) (Math.PI * 2.0);

    private static final Swing[] SWORD = {
            // 1. fast horizontal cut, right to left (heavy kinetic blade impact)
            shock(k(0.7F, -0.05F, -1.35F, 1.2F, 0.2F, -0.3F, 0.2F, 0.1F, -0.2F, -0.3F, 0, 0.5F),
                  k(-0.7F, 0.1F, -1.45F, -0.9F, 0.0F, -0.05F, -0.1F, 0.3F, -0.3F, -0.4F, 2.5F, 1.0F),
                  k(-0.85F, 0.12F, -1.1F, -1.3F, -0.2F, -0.2F, -0.2F, 0.4F, -0.3F, -0.3F, 2.0F, 1.0F),
                  Trail.arc(-8, 110, -120, 0.75F, 1.6F, 1.2F)),
            // 2. lunging piercing thrust
            s(k(0.4F, 0.0F, -0.7F, 0.1F, 0.1F, -1.4F, 0.2F, -0.1F, -0.2F, -0.3F, -0.5F, 0.8F),
              k(-0.1F, 0.1F, -1.55F, -0.05F, 0.0F, -0.05F, 0.3F, 0.1F, -0.3F, -0.2F, 3.5F, 1.2F),
              k(-0.1F, 0.1F, -1.5F, 0.0F, 0.0F, -0.1F, 0.3F, 0.1F, -0.3F, -0.2F, 3.0F, 1.2F),
              Trail.thrust(1.2F, 0.2F, 2.0F)),
            // 3. rising backhand slash, low left to high right
            s(k(-0.6F, 0.15F, -0.35F, -0.9F, -0.3F, -0.4F, 0.2F, 0.1F, -0.2F, -0.3F, 0.5F, 1.5F),
              k(0.55F, -0.15F, -2.5F, 0.7F, 0.35F, -0.1F, -0.2F, -0.3F, -0.4F, -0.3F, 2.0F, 0),
              k(0.7F, -0.2F, -2.8F, 0.9F, 0.45F, -0.2F, -0.2F, -0.4F, -0.4F, -0.3F, 1.5F, 0),
              Trail.arc(40, -110, 105, 0.75F, 1.6F, 1.15F)),
            // 4. falling helm-splitter (disciplined two-handed overhead executioner cut with ground shockwave)
            shock(k(0.1F, -0.28F, -2.85F, 0.15F, 0.05F, -0.6F, -2.6F, -0.15F, -0.05F, -0.6F, 0.0F, -0.2F),
                  k(-0.05F, 0.35F, -0.85F, 0.05F, 0.0F, -0.05F, -0.85F, -0.05F, 0.0F, -0.05F, 2.8F, 1.4F),
                  k(-0.05F, 0.35F, -0.75F, 0.05F, 0.0F, -0.1F, -0.75F, -0.05F, 0.0F, -0.1F, 2.5F, 1.5F),
                  Trail.arc(90, 110, -75, 0.75F, 1.75F, 1.25F)),
            // 5. iaido quick-draw crescent dash (low coil into blinding horizontal sweep with shockwave)
            shock(k(-0.65F, 0.15F, -0.45F, -0.7F, -0.2F, -1.4F, -0.5F, 0.4F, 0.0F, -0.6F, -0.5F, 2.8F),
                  k(0.7F, -0.1F, -1.35F, 0.95F, 0.25F, -0.05F, 0.2F, -0.3F, -0.2F, -0.4F, 4.2F, 1.2F),
                  k(0.85F, -0.12F, -1.15F, 1.15F, 0.35F, -0.15F, 0.2F, -0.3F, -0.2F, -0.4F, 3.8F, 1.2F),
                  Trail.arc(-12, -115, 125, 0.8F, 1.9F, 1.1F)),
            // 6. rising dragon riposte (low scoop into soaring upward launcher with impact flare)
            shock(k(0.4F, 0.2F, 0.2F, 0.2F, 0.1F, -1.4F, -0.3F, -0.2F, 0.0F, -0.6F, 0.0F, 1.8F),
                  k(-0.35F, -0.2F, -2.55F, 0.1F, 0.15F, -0.1F, 0.4F, 0.2F, -0.2F, -0.3F, 2.4F, -0.4F),
                  k(-0.4F, -0.2F, -2.75F, 0.15F, 0.2F, -0.15F, 0.4F, 0.2F, -0.2F, -0.3F, 2.0F, -0.2F),
                  Trail.arc(55, -95, 95, 0.75F, 1.85F, 1.2F)),
    };

    private static final Swing[] DAGGER = {
            // quick jab
            s(k(0.35F, 0, -1.0F, 0.1F, 0.1F, -1.5F, -0.8F, 0.3F, 0, -1.2F, 0, 1.0F),
              k(-0.25F, 0.1F, -1.65F, -0.15F, 0, 0.0F, -0.5F, 0.5F, 0, -1.4F, 3.0F, 1.0F),
              k(-0.2F, 0.1F, -1.55F, -0.1F, 0, -0.1F, -0.5F, 0.5F, 0, -1.3F, 2.5F, 1.0F),
              Trail.thrust(1.2F, 0.3F, 1.7F)),
            // cross-cut, left to right
            s(k(-0.5F, 0, -1.3F, -1.0F, -0.3F, -0.4F, -1.0F, 0.6F, 0, -1.0F, 0.5F, 1.2F),
              k(0.6F, 0.05F, -1.3F, 1.0F, 0.3F, -0.1F, -0.6F, -0.2F, 0, -0.8F, 2.0F, 1.2F),
              k(0.7F, 0.05F, -1.1F, 1.2F, 0.4F, -0.2F, -0.5F, -0.3F, 0, -0.7F, 1.5F, 1.2F),
              Trail.arc(15, -100, 100, 0.5F, 1.15F, 1.15F)),
            // spinning assassination plunge with ground impact
            new Swing(k(0.4F, 0, -1.2F, 1.0F, 0.3F, -0.3F, -0.4F, -0.6F, -0.4F, -0.4F, 0, 1.5F),
              k(-0.4F, 0, -1.4F, -0.6F, -0.2F, -0.1F, -0.3F, 0.7F, -0.6F, -0.2F, 1.5F, 1.5F),
              k(-0.5F, 0, -1.2F, -0.8F, -0.2F, -0.2F, -0.3F, 0.6F, -0.5F, -0.3F, 1.0F, 1.2F),
              TAU, new Trail(TrailType.ARC_RING, 0, 0, 0, 0.6F, 1.35F, 1.05F, 0), true),
    };

    private static final Swing[] AXE = {
            // diagonal chop, high right to low left (ground splitting shock)
            shock(k(0.6F, -0.25F, -2.7F, 0.6F, 0.3F, -0.9F, -2.2F, 0.9F, 0.2F, -0.9F, 0, -0.3F),
                  k(-0.5F, 0.3F, -0.7F, -0.5F, 0, -0.1F, -0.8F, 0.1F, 0, -0.2F, 2.5F, 1.8F),
                  k(-0.6F, 0.3F, -0.45F, -0.7F, 0, -0.1F, -0.55F, 0.0F, 0, -0.1F, 2.5F, 2.0F),
                  Trail.arc(45, 115, -85, 0.8F, 1.7F, 1.2F)),
            // wide sweep
            shock(k(0.8F, 0, -1.2F, 1.3F, 0.3F, -0.5F, -1.2F, 1.6F, 0, -0.5F, 0, 1.2F),
                  k(-0.8F, 0.1F, -1.3F, -0.8F, 0, -0.1F, -1.3F, -0.4F, 0, -0.2F, 2.0F, 1.4F),
                  k(-1.0F, 0.1F, -1.1F, -1.2F, -0.1F, -0.2F, -1.1F, -0.7F, 0, -0.2F, 1.5F, 1.4F),
                  Trail.arc(-5, 120, -130, 0.85F, 1.8F, 1.1F)),
            // two-handed overhead ground-shatter slam
            shock(k(0, -0.35F, -3.0F, 0.05F, 0, -0.9F, -3.0F, -0.05F, 0, -0.9F, 0, -0.6F),
                  k(0, 0.45F, -0.5F, 0.1F, 0, -0.1F, -0.5F, -0.1F, 0, -0.1F, 3.0F, 2.4F),
                  k(0, 0.45F, -0.3F, 0.1F, 0, -0.1F, -0.3F, -0.1F, 0, -0.1F, 3.0F, 2.6F),
                  Trail.arc(90, 100, -75, 0.8F, 1.7F, 1.25F)),
    };

    private static final Swing[] HEAVY = {
            // 1. titanic vertical two-handed downward cleave with earth-shattering ground shock
            new Swing(k(0.0F, -0.45F, -3.15F, 0.1F, 0.0F, -1.0F, -3.1F, -0.1F, 0.0F, -1.0F, 0.0F, -1.0F),
              k(0.0F, 0.5F, -0.45F, 0.05F, 0.0F, 0.0F, -0.45F, -0.05F, 0.0F, 0.0F, 3.5F, 3.2F),
              k(0.0F, 0.5F, -0.3F, 0.05F, 0.0F, 0.0F, -0.3F, -0.05F, 0.0F, 0.0F, 3.2F, 3.5F),
              0.0F, Trail.arc(90, 110, -75, 1.0F, 2.3F, 1.35F), true),
            // 2. 360 momentum greatsword hurricane spin
            new Swing(k(-0.9F, 0.1F, -0.7F, -0.8F, -0.2F, -0.2F, -0.7F, -0.4F, 0.0F, -0.2F, 0.5F, 1.5F),
              k(0.0F, 0.0F, -1.35F, 0.0F, 0.1F, -0.1F, -1.35F, 0.5F, 0.0F, -0.2F, 1.5F, 1.5F),
              k(0.2F, 0.0F, -1.2F, 0.3F, 0.1F, -0.2F, -1.2F, 0.6F, 0.0F, -0.2F, 1.0F, 1.2F),
              TAU, new Trail(TrailType.ARC_RING, 0, 0, 0, 0.9F, 2.1F, 1.1F, 0), false),
            // 3. heavy diagonal cleave & ground drag
            s(k(0.8F, -0.2F, -2.6F, 0.5F, 0.2F, -0.8F, -2.2F, 0.8F, 0.1F, -0.9F, -0.5F, 0.5F),
              k(-0.8F, 0.3F, -0.6F, -0.5F, -0.1F, -0.1F, -0.8F, -0.2F, 0.0F, -0.2F, 3.2F, 2.2F),
              k(-1.0F, 0.3F, -0.4F, -0.7F, -0.2F, -0.1F, -0.6F, -0.3F, 0.0F, -0.2F, 3.0F, 2.4F),
              Trail.arc(45, 120, -90, 0.9F, 2.05F, 1.15F)),
            // 4. titanic colossus uppercut (drags blade along ground, launches skyward)
            s(k(0.7F, 0.35F, 0.4F, 0.3F, 0.15F, -1.2F, 0.2F, -0.3F, 0.0F, -0.8F, -0.5F, 2.5F),
              k(-0.6F, -0.35F, -2.85F, 0.1F, 0.15F, -0.1F, -2.5F, -0.2F, 0.0F, -0.2F, 3.2F, -0.8F),
              k(-0.7F, -0.3F, -3.0F, 0.15F, 0.2F, -0.15F, -2.6F, -0.2F, 0.0F, -0.2F, 2.8F, -0.6F),
              Trail.arc(-85, -100, 80, 1.0F, 2.35F, 1.35F)),
            // 5. executioner's guillotine leap (airborne vault with crushing ground shockwave)
            new Swing(k(0.0F, -0.25F, -3.1F, 0.05F, 0.0F, -0.9F, -3.1F, -0.05F, 0.0F, -0.9F, 0.0F, -1.2F),
              k(0.0F, 0.48F, -0.5F, 0.05F, 0.0F, 0.0F, -0.5F, -0.05F, 0.0F, 0.0F, 3.6F, 4.2F),
              k(0.0F, 0.48F, -0.4F, 0.05F, 0.0F, 0.0F, -0.4F, -0.05F, 0.0F, 0.0F, 3.4F, 4.4F),
              0.0F, Trail.arc(90, 115, -70, 1.0F, 2.4F, 1.3F), true),
            // 6. wide perimeter cleave (full 270 horizontal momentum ring sweep)
            new Swing(k(0.85F, 0.0F, -1.1F, 1.25F, 0.2F, -0.4F, -1.1F, 1.25F, 0.0F, -0.4F, 0.0F, 1.5F),
              k(-0.8F, 0.1F, -1.35F, -0.7F, -0.1F, -0.1F, -1.35F, -0.3F, 0.0F, -0.1F, 2.5F, 1.8F),
              k(-1.0F, 0.1F, -1.2F, -1.0F, -0.2F, -0.15F, -1.2F, -0.5F, 0.0F, -0.15F, 2.0F, 1.8F),
              TAU, new Trail(TrailType.RING, 0, 0, 0, 1.1F, 2.35F, 1.05F, 0), false),
    };

    private static final Swing[] SPEAR = {
            // 1. vanguard dragon lunge: deep stance forward impale with point-impact shockwave
            shock(k(0.55F, 0.0F, -0.65F, -0.22F, 0.12F, -1.45F, -1.25F, 0.45F, -0.15F, -0.45F, -0.5F, 1.2F),
                  k(-0.25F, 0.12F, -1.55F, 0.05F, 0.0F, -0.05F, -1.45F, 0.25F, -0.05F, -0.15F, 4.8F, 1.4F),
                  k(-0.20F, 0.12F, -1.50F, 0.05F, 0.0F, -0.10F, -1.40F, 0.25F, -0.05F, -0.15F, 4.2F, 1.4F),
                  Trail.thrust(1.35F, 0.15F, 3.2F)),
            // 2. twin viper piston thrusts: high-speed linear fencing double-pierce
            s(k(0.25F, 0.0F, -0.85F, 0.05F, 0.0F, -1.55F, -1.15F, 0.35F, 0.0F, -0.5F, -0.3F, 0.8F),
              k(-0.15F, 0.08F, -1.60F, -0.02F, 0.0F, -0.05F, -1.50F, 0.20F, 0.0F, -0.05F, 4.4F, 1.0F),
              k(-0.12F, 0.08F, -1.55F, 0.0F, 0.0F, -0.08F, -1.45F, 0.20F, 0.0F, -0.08F, 3.8F, 1.0F),
              Trail.thrust(1.30F, 0.12F, 2.9F)),
            // 3. haft deflect & rising skewer: high parry with pole shaft into upward chin impale
            shock(k(-0.35F, -0.12F, -1.75F, 0.35F, 0.15F, -0.85F, -0.75F, -0.35F, -0.15F, -0.95F, 0.0F, 0.6F),
                  k(0.20F, 0.15F, -1.45F, -0.05F, 0.0F, -0.15F, -1.35F, 0.15F, 0.0F, -0.25F, 3.8F, 0.4F),
                  k(0.25F, 0.15F, -1.40F, -0.05F, 0.0F, -0.20F, -1.30F, 0.15F, 0.0F, -0.25F, 3.2F, 0.4F),
                  Trail.thrust(1.30F, -0.10F, 2.8F)),
            // 4. valkyrie soaring vault & ground impale: aerial leap into plunging point plant with massive ground shock
            new Swing(k(0.0F, -0.25F, -2.65F, 0.12F, 0.0F, -0.95F, -2.45F, -0.12F, 0.0F, -0.95F, 0.0F, -0.5F),
              k(0.0F, 0.42F, -0.75F, 0.05F, 0.0F, -0.15F, -0.80F, 0.20F, 0.0F, -0.20F, 3.2F, 3.5F),
              k(0.0F, 0.42F, -0.70F, 0.05F, 0.0F, -0.10F, -0.75F, 0.20F, 0.0F, -0.15F, 3.2F, 3.8F),
              0.0F, Trail.thrust(1.25F, 0.10F, 3.0F), null, true, 0.0F),
            // 5. halberd cleaving polearm sweep: wide two-handed leverage horizontal cut with kinetic shock
            shock(k(0.75F, 0.05F, -1.15F, 1.15F, 0.25F, -0.55F, -1.15F, 1.45F, 0.0F, -0.45F, 0.0F, 1.8F),
                  k(-0.75F, 0.12F, -1.25F, -0.75F, 0.0F, -0.15F, -1.25F, -0.35F, 0.0F, -0.25F, 2.8F, 1.6F),
                  k(-0.90F, 0.12F, -1.10F, -1.05F, 0.0F, -0.20F, -1.15F, -0.50F, 0.0F, -0.25F, 2.2F, 1.6F),
                  Trail.arc(15, 125, -115, 1.10F, 2.45F, 1.05F)),
            // 6. 360 serpent whirlwind & final lunge: full body perimeter trip into sudden forward impale
            new Swing(k(0.55F, 0.15F, -0.75F, 0.75F, 0.15F, -1.15F, -0.85F, 0.85F, 0.0F, -0.65F, 0.0F, 2.2F),
              k(-0.35F, 0.10F, -1.35F, -0.15F, 0.0F, -0.15F, -1.35F, 0.15F, 0.0F, -0.15F, 3.6F, 1.8F),
              k(-0.45F, 0.10F, -1.30F, -0.20F, 0.0F, -0.20F, -1.30F, 0.15F, 0.0F, -0.15F, 3.0F, 1.8F),
              TAU, new Trail(TrailType.ARC_RING, 0, 0, 0, 1.10F, 2.50F, 0.75F, 0), null, true, 0.0F),
    };

    private static final Swing[] FIST = {
            // right jab
            s(k(0.25F, 0, -0.6F, 0.1F, 0, -1.9F, -0.6F, -0.3F, 0, -1.9F, 0, 1.0F),
              k(-0.35F, 0.05F, -1.6F, -0.15F, 0, -0.05F, -0.6F, -0.3F, 0, -1.9F, 2.0F, 1.0F),
              k(-0.3F, 0.05F, -1.5F, -0.1F, 0, -0.1F, -0.6F, -0.3F, 0, -1.9F, 1.8F, 1.0F),
              Trail.thrust(1.3F, 0.25F, 1.3F)),
            // left cross
            s(k(-0.25F, 0, -0.6F, 0.3F, 0, -1.9F, -0.6F, -0.1F, 0, -1.9F, 0, 1.0F),
              k(0.4F, 0.05F, -0.6F, 0.3F, 0, -1.9F, -1.6F, 0.15F, 0, -0.05F, 2.2F, 1.0F),
              k(0.35F, 0.05F, -0.6F, 0.3F, 0, -1.9F, -1.5F, 0.1F, 0, -0.1F, 2.0F, 1.0F),
              Trail.thrust(1.3F, -0.25F, 1.3F)),
            // rising dragon uppercut with ground tremoring kinetic shock
            shock(k(0.3F, 0.2F, 0.1F, 0.1F, 0.1F, -1.6F, -0.6F, -0.3F, 0, -1.9F, 0, 2.2F),
                  k(-0.2F, -0.25F, -2.2F, -0.1F, 0, -1.0F, -0.6F, -0.3F, 0, -1.9F, 1.5F, -0.3F),
                  k(-0.2F, -0.25F, -2.4F, -0.1F, 0, -1.1F, -0.6F, -0.3F, 0, -1.9F, 1.2F, -0.3F),
                  Trail.arc(90, -60, 80, 0.4F, 1.0F, 1.1F)),
    };

    private static final Swing[] STAFF = {
            // 1. arcane seismic slam: two-handed overhead plant with ground shockwave
            new Swing(k(0.0F, -0.38F, -2.85F, 0.08F, 0.0F, -1.15F, -2.85F, -0.08F, 0.0F, -1.15F, 0.0F, -0.4F),
              k(0.0F, 0.35F, -0.8F, 0.04F, 0.0F, -0.22F, -0.8F, -0.04F, 0.0F, -0.22F, 2.6F, 2.8F),
              k(0.0F, 0.35F, -0.75F, 0.04F, 0.0F, -0.18F, -0.75F, -0.04F, 0.0F, -0.18F, 2.6F, 2.9F),
              0.0F, new Trail(TrailType.RING, 0, 0, 0, 1.0F, 2.2F, 0.15F, 0.0F), true),
            // 2. lunar lunge & celestial thrust
            s(k(0.35F, 0.0F, -0.7F, 0.2F, 0.0F, -1.2F, -1.1F, 0.5F, 0.0F, -0.3F, -0.6F, 0.8F),
              k(-0.1F, 0.1F, -1.5F, -0.04F, 0.0F, -0.1F, -1.45F, 0.25F, 0.0F, -0.1F, 3.8F, 1.1F),
              k(-0.08F, 0.1F, -1.45F, 0.0F, 0.0F, -0.15F, -1.4F, 0.25F, 0.0F, -0.1F, 3.2F, 1.1F),
              Trail.thrust(1.25F, 0.1F, 2.4F)),
            // 3. acrobatic vault & 360 double-ended arcane cyclone
            new Swing(k(-0.4F, 0.0F, -1.2F, -0.6F, 0.0F, -0.4F, -0.4F, 0.4F, 0.0F, -0.8F, 0.5F, 1.2F),
              k(0.0F, 0.0F, -1.4F, 0.2F, 0.1F, -0.15F, -1.4F, 0.6F, 0.0F, -0.15F, 2.0F, 1.0F),
              k(0.2F, 0.0F, -1.3F, 0.3F, 0.1F, -0.2F, -1.3F, 0.6F, 0.0F, -0.2F, 1.5F, 1.0F),
              TAU, new Trail(TrailType.ARC_RING, 0, 0, 0, 0.9F, 2.1F, 1.2F, 0), false),
            // 4. arcane helix figure-8 sweep (flowing diagonal sweep with sweeping arcane ring arc)
            s(k(0.6F, -0.15F, -1.35F, 0.95F, 0.25F, -0.45F, -0.9F, 0.65F, 0.15F, -0.6F, 0.3F, 1.4F),
              k(-0.55F, 0.1F, -1.25F, -0.85F, -0.1F, -0.15F, -1.2F, -0.4F, 0.0F, -0.2F, 2.8F, 1.4F),
              k(-0.65F, 0.1F, -1.1F, -1.05F, -0.15F, -0.2F, -1.1F, -0.5F, 0.0F, -0.25F, 2.2F, 1.4F),
              Trail.arc(35, -110, 110, 0.95F, 2.25F, 1.25F)),
            // 5. celestial star-burst javelin thrust (deep stance explosive forward reach)
            s(k(0.4F, 0.15F, -0.85F, 0.25F, 0.0F, -1.4F, -1.2F, 0.55F, 0.0F, -0.35F, -0.4F, 0.9F),
              k(-0.12F, 0.08F, -1.6F, -0.02F, 0.0F, -0.05F, -1.55F, 0.2F, 0.0F, -0.05F, 4.2F, 1.2F),
              k(-0.1F, 0.08F, -1.55F, 0.0F, 0.0F, -0.1F, -1.5F, 0.2F, 0.0F, -0.1F, 3.6F, 1.2F),
              Trail.thrust(1.3F, 0.12F, 2.8F)),
            // 6. astral shockwave vault cleave (aerial plant into expanding shockwave impact)
            new Swing(k(0.0F, -0.42F, -2.7F, 0.12F, 0.0F, -0.95F, -2.55F, -0.12F, 0.0F, -0.95F, 0.0F, 0.2F),
              k(0.0F, 0.42F, -0.7F, 0.04F, 0.0F, -0.15F, -0.8F, 0.18F, 0.0F, -0.2F, 3.2F, 2.6F),
              k(0.0F, 0.42F, -0.65F, 0.04F, 0.0F, -0.1F, -0.75F, 0.18F, 0.0F, -0.15F, 3.0F, 2.8F),
              TAU, new Trail(TrailType.RING, 0, 0, 0, 1.05F, 2.35F, 0.25F, 0.0F), true),
    };

    private static final Swing[] DUAL = {
            // 1. stylish twin scissor cross X-slash (both blades crossing simultaneously with ground shock)
            shock(k(0.0F, -0.08F, -1.15F, 1.15F, 0.35F, -0.6F, -1.15F, -1.15F, -0.35F, -0.6F, 0.0F, 0.6F),
                  k(0.0F, 0.12F, -1.45F, -0.75F, 0.12F, -0.18F, -1.45F, 0.75F, -0.12F, -0.18F, 2.8F, 1.2F),
                  k(0.0F, 0.12F, -1.3F, -0.9F, 0.0F, -0.22F, -1.3F, 0.9F, 0.0F, -0.22F, 2.2F, 1.2F),
                  Trail.arc(28, 125, -60, 0.75F, 1.8F, 1.15F),
                  Trail.arc(-28, -125, 60, 0.75F, 1.8F, 1.15F)),

            // 2. alternating twin-fang flurry (downward right slash into explosive left reverse thrust)
            s(k(0.55F, -0.12F, -1.6F, 0.85F, 0.2F, -0.3F, -0.5F, 0.25F, 0.0F, -1.4F, 0.5F, 1.0F),
              k(-0.45F, 0.12F, -1.35F, -0.75F, 0.0F, -0.1F, -1.6F, 0.12F, 0.0F, -0.05F, 3.2F, 1.0F),
              k(-0.5F, 0.12F, -1.15F, -0.95F, 0.0F, -0.1F, -1.55F, 0.12F, 0.0F, -0.1F, 2.6F, 1.0F),
              Trail.arc(-32, 110, -70, 0.7F, 1.7F, 1.25F),
              Trail.thrust(1.15F, -0.22F, 2.1F),
              0.08F),

            // 3. dual cyclone 360 dance of death finisher (full body spin with ground shockwave)
            new Swing(k(0.7F, 0.0F, -1.0F, 1.15F, 0.25F, -0.3F, -0.5F, -0.55F, -0.25F, -0.5F, 0.0F, 1.2F),
              k(-0.5F, 0.0F, -1.4F, 0.0F, 0.1F, -0.1F, -1.4F, 0.0F, -0.1F, -0.1F, 2.2F, 1.2F),
              k(-0.7F, 0.0F, -1.25F, -0.3F, 0.0F, -0.2F, -1.25F, 0.3F, 0.0F, -0.2F, 1.6F, 1.2F),
              TAU, new Trail(TrailType.ARC_RING, 0, -90, 270, 0.85F, 2.05F, 1.15F, 0),
              new Trail(TrailType.ARC_RING, 0, 90, 450, 0.8F, 1.95F, 1.10F, 0), true, 0.0F),

            // 4. staccato twin cross-flurry (rapid horizontal cross-cut with quick torso whip)
            s(k(-0.5F, 0.0F, -1.25F, -0.9F, -0.2F, -0.35F, -0.9F, 0.7F, 0.2F, -0.8F, 0.8F, 1.1F),
              k(0.6F, 0.08F, -1.35F, 0.95F, 0.2F, -0.15F, -1.45F, -0.85F, -0.15F, -0.15F, 3.0F, 1.1F),
              k(0.7F, 0.08F, -1.15F, 1.1F, 0.25F, -0.2F, -1.3F, -1.0F, -0.15F, -0.2F, 2.4F, 1.0F),
              Trail.arc(12, -100, 100, 0.65F, 1.65F, 1.2F),
              Trail.arc(-12, 100, -100, 0.65F, 1.65F, 1.1F),
              0.06F),

            // 5. twin rising crescents (deep crouch into explosive upward V-cleave)
            s(k(0.0F, 0.35F, 0.15F, 0.25F, 0.1F, -1.35F, 0.15F, -0.25F, -0.1F, -1.35F, -0.5F, 3.2F),
              k(0.0F, -0.25F, -2.45F, 0.45F, 0.35F, -0.15F, -2.45F, -0.45F, -0.35F, -0.15F, 2.8F, 0.0F),
              k(0.0F, -0.2F, -2.65F, 0.55F, 0.4F, -0.2F, -2.65F, -0.55F, -0.4F, -0.2F, 2.0F, 0.0F),
              Trail.arc(65, -80, 80, 0.7F, 1.75F, 1.3F),
              Trail.arc(-65, -80, 80, 0.7F, 1.75F, 1.3F)),

            // 6. twin comet plunge (aerial leap into dual downward impale with ground shockwave)
            new Swing(k(0.0F, -0.22F, -2.75F, 0.1F, 0.0F, -0.85F, -2.75F, -0.1F, 0.0F, -0.85F, 0.0F, -0.6F),
              k(0.0F, 0.38F, -0.75F, 0.05F, 0.0F, -0.15F, -0.75F, -0.05F, 0.0F, -0.15F, 2.6F, 3.6F),
              k(0.0F, 0.38F, -0.7F, 0.05F, 0.0F, -0.1F, -0.7F, -0.05F, 0.0F, -0.1F, 2.6F, 3.8F),
              0.0F, Trail.thrust(1.15F, 0.18F, 2.2F), Trail.thrust(1.15F, -0.18F, 2.2F), true, 0.0F),

            // 7. 360° butterfly tempest (guard-break whirlwind with wide expanding perimeter ring and ground shock)
            new Swing(k(0.65F, -0.05F, -1.1F, 0.95F, 0.2F, -0.4F, -0.6F, -0.5F, -0.2F, -0.6F, 0.0F, 1.4F),
              k(-0.6F, 0.05F, -1.45F, -0.1F, 0.08F, -0.1F, -1.35F, 0.1F, -0.08F, -0.1F, 2.4F, 1.2F),
              k(-0.75F, 0.05F, -1.3F, -0.2F, 0.0F, -0.15F, -1.25F, 0.2F, 0.0F, -0.15F, 1.8F, 1.2F),
              TAU, new Trail(TrailType.RING, 0, 0, 0, 0.9F, 2.25F, 1.15F, 0),
              new Trail(TrailType.RING, 0, 0, 0, 0.75F, 1.85F, 0.95F, 0), true, 0.0F),
    };

    public static int variants(WeaponKind kind) {
        return switch (kind) {
            case DAGGER -> DAGGER.length;
            case AXE -> AXE.length;
            case HEAVY -> HEAVY.length;
            case SPEAR -> SPEAR.length;
            case STAFF -> STAFF.length;
            case DUAL -> DUAL.length;
            case FIST -> FIST.length;
            default -> SWORD.length;
        };
    }

    public static Swing get(WeaponKind kind, int variant) {
        Swing[] set = switch (kind) {
            case DAGGER -> DAGGER;
            case AXE -> AXE;
            case HEAVY -> HEAVY;
            case SPEAR -> SPEAR;
            case STAFF -> STAFF;
            case DUAL -> DUAL;
            case FIST -> FIST;
            default -> SWORD;
        };
        return set[Math.floorMod(variant, set.length)];
    }

    /** Swing length in ticks: ranged kinds fall back to a sword-length swing. */
    public static int ticks(WeaponKind kind) {
        return kind.swingTicks > 0 ? kind.swingTicks : 10;
    }

    // ---- easing ----

    public static float clamp01(float v) {
        return v < 0 ? 0 : v > 1 ? 1 : v;
    }

    public static float easeOutQuad(float t) {
        return 1 - (1 - t) * (1 - t);
    }

    public static float easeOutExpo(float t) {
        return t >= 1 ? 1 : 1 - (float) Math.pow(2, -9 * t);
    }

    public static float easeInOutSine(float t) {
        return (float) (-(Math.cos(Math.PI * t) - 1) / 2);
    }

    /** How far the strike has travelled (0..1) at swing progress p. */
    public static float strike(float p) {
        return easeOutExpo(clamp01((p - A) / (B - A)));
    }

    /** Trail opacity at swing progress p: full while striking, fading through the follow-through. */
    public static float trailAlpha(float p) {
        if (p < A) return 0;
        if (p < B) return 1;
        return 1 - clamp01((p - B) / (C - B + 0.1F));
    }
}
