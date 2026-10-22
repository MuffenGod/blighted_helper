package com.blighteddreams.helper.client;

/**
 * The body layer of the Gaia-model helpers: locomotion, weapon stances and carries, attack swings, foot IK, head stabilisation and spring
 * secondary motion of the standard parts. Pure math on {@link HelperMotion}-style pose arrays (STRIDE floats per part, the 25 standard parts
 * first, in {@link HelperMotionRig#STD_NAMES} order), so it runs the same in the game and in the model studio.
 *
 * Everything between the BEGIN-JS and END-JS markers is written in the restricted Java subset that design/entitykit/anim/java2js.js
 * transpiles to JavaScript (anim/body_core.js): no lambdas, no switch, no names that are JS keywords, one declaration style per line.
 *
 * Techniques (the usual ones in shipped third-person games, scaled to a blocky rig):
 * - a phase-warped gait: each foot spends a duty fraction of the cycle planted (moving back linearly) and the rest swinging forward on an
 *   eased arc; two-bone leg IK turns foot targets into hip and knee angles, so knees bend by themselves when the pelvis drops;
 * - pelvis bob (inverted pendulum when walking, spring-mass when running), lateral sway over the stance foot, swing-side hip drop, pelvis
 *   twist with counter-rotating shoulders, forward lean from speed and acceleration, banking into turns, a landing squash;
 * - per-weapon fighting stances (staggered feet, crouch, bladed hips, breathing bounce, a figure-eight guard sway, a periodic re-grip) and
 *   relaxed carries with idle breakers;
 * - attacks as a kinetic chain: the hips lead, the chest follows, then the arm and a whipping forearm; anticipation dip, stepping lunge with
 *   a planted back foot, an overshooting follow-through and an impact shake;
 * - head stabilisation (the gaze holds while the torso twists) and damped springs that make the tail, hair, skirt and wings lag.
 */
public final class HelperBody {
    private HelperBody() {}

    // ---- helpers the JS prelude re-implements
    private static float sin(float x) { return (float) Math.sin(x); }
    private static float cos(float x) { return (float) Math.cos(x); }
    private static float sqrt(float x) { return (float) Math.sqrt(x); }
    private static float atan2(float y, float x) { return (float) Math.atan2(y, x); }
    private static float acos(float x) { return (float) Math.acos(x); }
    private static float fexp(float x) { return (float) Math.exp(x); }
    private static int ifloor(float x) { int i = (int) x; return x < (float) i ? i - 1 : i; }

    //@BEGIN-JS
    static final float PI = 3.14159265F;
    static final float TAU = 6.2831853F;
    static final int STRIDE = 8;
    static final int AX = 0, AY = 1, AZ = 2, ARX = 3, ARY = 4, ARZ = 5;
    static final int ROOT = 0, BODYBOTTOM = 1, BODYMIDDLE = 2, BODYTOP = 3, BODYTOPF = 4, NECK = 5, HEAD = 6, HAIR1 = 7, HAIR2 = 8;
    static final int RARM = 9, RARML = 10, LARM = 11, LARML = 12, RWING = 13, LWING = 14, SKIRT1 = 15, SKIRT2 = 16, SKIRT3 = 17;
    static final int TAIL1 = 18, TAIL2 = 19, TAIL3 = 20, RLEG = 21, RLEGL = 22, LLEG = 23, LLEGL = 24, NSTD = 25;
    // WeaponKind ordinals
    static final int K_FIST = 0, K_SWORD = 1, K_DAGGER = 2, K_AXE = 3, K_HEAVY = 4, K_SPEAR = 5, K_STAFF = 6, K_BOW = 7, K_CROSSBOW = 8, K_DUAL = 9;
    // HelperVariant ordinals (personality of the gait)
    static final int V_GAIA = 0, V_DEMON = 1, V_ANGEL = 2, V_ASTRAL = 3, V_JESTER = 4;
    // leg geometry in model pixels: the hips sit 13 above the ground, thigh 6, shin and foot 7
    static final float HIP_Y = 13.0F, HIP_X = 2.0F, THIGH = 6.0F, SHIN = 7.0F;
    // swing phase edges (HelperSwings.A / B / C): wind-up ends, the strike lands, the follow-through ends
    static final float SA = 0.2F, SB = 0.42F, SC = 0.7F;

    // state slots (per entity float[S_LEN])
    static final int S_INIT = 0, S_YAW = 1, S_YAWRATE = 2, S_VF = 3, S_ACC = 4, S_AIR = 5, S_LANDT = 6, S_LANDK = 7, S_VYAIR = 8, S_PELVY = 9, S_ROOTY = 10;
    static final int S_FEET = 12;      // foot targets: right x, y, z then left x, y, z (ground frame: y < 0 is up, z > 0 is behind)
    static final int S_SPR = 20;       // damped springs, (position, velocity) pairs
    static final int S_LEN = 40;

    /** fighting stance per weapon kind: lead foot (1 = left forward, -1 = right forward), half stagger, half width, crouch, pelvis yaw, chest yaw, bounce, bounce period (ticks), guard sway */
    static final float[] STANCE = {
            1.0F, 1.7F, 2.5F, 1.25F, 0.30F, -0.10F, 0.55F, 14.0F, 0.06F,     // fist: orthodox boxer, light on the feet
            -1.0F, 1.6F, 2.4F, 1.1F, -0.28F, 0.12F, 0.25F, 34.0F, 0.07F,     // sword: sword side leads, hips bladed
            -1.0F, 1.3F, 2.6F, 1.6F, -0.22F, 0.08F, 0.45F, 16.0F, 0.08F,     // dagger: low and springy
            1.0F, 1.5F, 2.7F, 1.2F, 0.22F, -0.08F, 0.25F, 30.0F, 0.05F,      // axe
            1.0F, 1.9F, 3.0F, 1.5F, 0.26F, -0.06F, 0.30F, 40.0F, 0.035F,     // heavy: wide, low, heavy breathing
            1.0F, 2.2F, 2.3F, 1.3F, 0.34F, -0.14F, 0.20F, 36.0F, 0.03F,      // spear: long stance, butt at the right hip
            1.0F, 1.5F, 2.5F, 0.9F, 0.20F, -0.06F, 0.20F, 38.0F, 0.05F,      // staff
            1.0F, 1.3F, 2.6F, 0.6F, 0.25F, -0.10F, 0.15F, 40.0F, 0.02F,      // bow
            1.0F, 1.3F, 2.4F, 0.7F, 0.20F, -0.08F, 0.15F, 40.0F, 0.02F,      // crossbow
            -1.0F, 1.1F, 2.9F, 1.5F, -0.15F, 0.05F, 0.45F, 18.0F, 0.07F };   // dual: wide, low, bouncing

    /** combat guard per weapon kind: right arm x, y, z, forearm, left arm x, y, z, forearm (absolute radians) */
    static final float[] GUARD = {
            -0.55F, -0.3F, 0.05F, -1.9F, -0.55F, 0.3F, -0.05F, -1.9F,
            -0.7F, 0.22F, 0.15F, -0.75F, -0.6F, -0.3F, -0.2F, -0.8F,
            -0.5F, -0.2F, 0.05F, -1.3F, -0.9F, 0.3F, 0.0F, -1.2F,
            -0.6F, 0.3F, 0.1F, -1.2F, -0.7F, 0.5F, 0.0F, -0.8F,
            -0.4F, -0.2F, 0.0F, -0.9F, -0.55F, 0.6F, 0.0F, -0.7F,
            -0.28F, -0.16F, 0.08F, -0.36F, -0.78F, 0.42F, -0.1F, -0.52F,
            -0.85F, -0.32F, 0.16F, -0.75F, -0.32F, 0.46F, -0.14F, -1.25F,
            -0.5F, 0.0F, 0.1F, -0.2F, 0.4F, 0.0F, -0.3F, -0.8F,
            -0.6F, -0.45F, 0.0F, -0.35F, -0.9F, 0.55F, 0.0F, -0.6F,
            -0.92F, -0.18F, 0.14F, -1.15F, -0.26F, 0.36F, -0.24F, -1.45F };

    /** relaxed carry per weapon kind: right arm x, y, z, forearm, left arm x, y, z, forearm, busy arms (1 right, 2 left, 3 both) */
    static final float[] CARRY = {
            0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F,
            -0.25F, 0.0F, 0.2F, -0.35F, 0.0F, 0.0F, 0.0F, 0.0F, 1.0F,
            -0.1F, 0.0F, 0.22F, -0.75F, 0.0F, 0.0F, 0.0F, 0.0F, 1.0F,
            0.1F, 0.0F, 0.2F, -0.25F, 0.0F, 0.0F, 0.0F, 0.0F, 1.0F,
            -1.15F, 0.25F, 0.1F, -1.95F, 0.0F, 0.0F, 0.0F, 0.0F, 1.0F,
            -0.24F, 0.08F, 0.14F, -0.32F, 0.12F, -0.08F, -0.1F, -0.22F, 3.0F,
            -0.72F, -0.22F, 0.14F, -0.85F, -0.32F, 0.38F, -0.12F, -0.65F, 3.0F,
            0.25F, 0.0F, 0.28F, -0.05F, 0.0F, 0.0F, 0.0F, 0.0F, 1.0F,
            -0.6F, -0.45F, 0.0F, -0.35F, -0.9F, 0.55F, 0.0F, -0.6F, 3.0F,
            -0.38F, 0.16F, 0.22F, -0.65F, 0.18F, -0.28F, -0.22F, -0.92F, 3.0F };

    /** what she is doing this frame */
    static final class In {
        public float age;
        public float dt;
        public float limbSwing;
        public float amt;
        public float run;
        public float air;
        public float vy;
        public float vf;
        public float vl;
        public float yaw;
        public boolean calm;
        public boolean combat;
        public boolean carry;
        public int kind;
        public int aim;
        public int style;
        public boolean riding;
        public boolean flourish;
    }

    /** one attack: key poses (torso yaw, torso pitch, right arm x y z forearm, left arm x y z forearm, lunge, crouch), spin, left-arm delay */
    static final class Swing {
        public float[] windup;
        public float[] strike;
        public float[] follow;
        public float spin;
        public float leftDelay;
        public int kind;
    }

    // ------------------------------------------------------------------------------------------------ small math
    static float clamp01(float x) {
        return x < 0.0F ? 0.0F : (x > 1.0F ? 1.0F : x);
    }

    static float clamp(float x, float a, float b) {
        return x < a ? a : (x > b ? b : x);
    }

    static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    static float sstep(float a, float b, float x) {
        float t = clamp01((x - a) / (b - a));
        return t * t * (3.0F - 2.0F * t);
    }

    static float frac(float x) {
        return x - (float) ifloor(x);
    }

    static float wrap(float a) {
        float r = a;
        while (r > PI) r -= TAU;
        while (r < -PI) r += TAU;
        return r;
    }

    static float bump(float x) {
        return x <= 0.0F || x >= 1.0F ? 0.0F : sin(PI * x);
    }

    static float inOutSine(float x) {
        return 0.5F - 0.5F * cos(PI * x);
    }

    static float outCubic(float x) {
        float u = 1.0F - x;
        return 1.0F - u * u * u;
    }

    static float outQuart(float x) {
        float u = 1.0F - x;
        return 1.0F - u * u * u * u;
    }

    static float outBack(float x) {
        float c1 = 1.4F;
        float c3 = c1 + 1.0F;
        float u = x - 1.0F;
        return 1.0F + c3 * u * u * u + c1 * u * u;
    }

    static void add(float[] p, int part, int axis, float v) {
        p[part * STRIDE + axis] += v;
    }

    static void put(float[] p, int part, int axis, float v) {
        p[part * STRIDE + axis] = v;
    }

    static float val(float[] p, int part, int axis) {
        return p[part * STRIDE + axis];
    }

    /** a damped spring in st[o] (position) / st[o + 1] (velocity): period in ticks, zeta = damping ratio */
    static float spring(float[] st, int o, float target, float period, float zeta, float dt) {
        float w = TAU / period;
        int steps = ifloor(dt / 0.5F) + 1;
        float h = dt / (float) steps;
        float x = st[o];
        float v = st[o + 1];
        for (int i = 0; i < steps; i++) {
            v += (w * w * (target - x) - 2.0F * zeta * w * v) * h;
            x += v * h;
        }
        st[o] = x;
        st[o + 1] = v;
        return x;
    }

    // ------------------------------------------------------------------------------------------------ leg IK
    /**
     * Two-bone IK for one leg. The foot target (fx, fy, fz) is in the ground frame of the root at rest (y < 0 lifts the foot, z > 0 is
     * behind her); (ox, oy, oz) is how far the root has moved from its rest pivot. Writes the hip pitch and roll and the knee bend.
     */
    static void legIK(float[] p, int hip, int knee, float hx, float ox, float oy, float oz, float fx, float fy, float fz) {
        float vx = fx - (hx + ox);
        float vy = HIP_Y + fy - oy;
        float vz = fz - oz;
        float ys = sqrt(vx * vx + vy * vy);
        float roll = atan2(-vx, vy);
        float d = sqrt(ys * ys + vz * vz);
        float maxd = (THIGH + SHIN) * 0.998F;
        if (d > maxd) d = maxd;
        if (d < 3.0F) d = 3.0F;
        float ck = (THIGH * THIGH + SHIN * SHIN - d * d) / (2.0F * THIGH * SHIN);
        float bend = PI - acos(clamp(ck, -1.0F, 1.0F));
        float cg = (THIGH * THIGH + d * d - SHIN * SHIN) / (2.0F * THIGH * d);
        float alpha = atan2(vz, ys);
        p[hip * STRIDE + ARX] = alpha - acos(clamp(cg, -1.0F, 1.0F));
        p[hip * STRIDE + ARZ] = roll;
        p[knee * STRIDE + ARX] = bend;
    }

    // ------------------------------------------------------------------------------------------------ gait
    /** foot position along the stride (+1 behind, -1 ahead) at cycle fraction u: swing (eased, back to front) then stance (linear, front to back) */
    static float footZ(float u, float duty) {
        float sw = 1.0F - duty;
        if (u < sw) {
            float t = u / sw;
            return 1.0F - 2.0F * (t * t * (3.0F - 2.0F * t));
        }
        return -1.0F + 2.0F * (u - sw) / duty;
    }

    /** foot lift (0..1) at cycle fraction u: high just after toe-off, lower as the foot reaches for the ground */
    static float footLift(float u, float duty) {
        float sw = 1.0F - duty;
        if (u >= sw) return 0.0F;
        float t = u / sw;
        return sin(PI * t) * (1.15F - 0.3F * t);
    }

    /** how much weight the foot carries (0..1, peaks mid-stance) */
    static float footLoad(float u, float duty) {
        float sw = 1.0F - duty;
        if (u < sw) return 0.0F;
        return sin(PI * (u - sw) / duty);
    }

    /**
     * Base locomotion for every frame: idle weight shift and breathing, walk and run cycles, airborne and landing, and the standard wings,
     * tail, hair and gown. Writes the foot targets that {@link #finish} solves.
     */
    static void locomotion(float[] p, float[] rest, In bi, float[] st) {
        float dt = bi.dt;
        if (st[S_INIT] < 0.5F || dt > 6.0F || dt < 0.0F) {
            for (int i = 0; i < S_LEN; i++) st[i] = 0.0F;
            st[S_INIT] = 1.0F;
            st[S_YAW] = bi.yaw;
            st[S_VF] = bi.vf;
            st[S_AIR] = bi.air;
            st[S_PELVY] = val(p, BODYBOTTOM, ARY);
            st[S_ROOTY] = val(p, ROOT, AY);
            dt = 0.0F;
        }
        float h = dt < 0.001F ? 0.001F : dt;
        float age = bi.age;
        // turn rate (rad/tick) and forward acceleration (blocks/tick^2), smoothed
        float yr = dt < 0.001F ? st[S_YAWRATE] : wrap(bi.yaw - st[S_YAW]) / h;
        st[S_YAW] = bi.yaw;
        st[S_YAWRATE] += (clamp(yr, -0.4F, 0.4F) - st[S_YAWRATE]) * clamp01(dt * 0.3F);
        float acc = dt < 0.001F ? st[S_ACC] : (bi.vf - st[S_VF]) / h;
        st[S_VF] = bi.vf;
        st[S_ACC] += (clamp(acc, -0.08F, 0.08F) - st[S_ACC]) * clamp01(dt * 0.25F);
        if (bi.air > 0.5F) st[S_VYAIR] = bi.vy;
        if (st[S_AIR] > 0.5F && bi.air < 0.5F) {
            st[S_LANDT] = age;
            st[S_LANDK] = clamp01(-st[S_VYAIR] * 2.2F);
        }
        st[S_AIR] = bi.air;

        float amt = bi.amt;
        float run = bi.run;
        float air = bi.air;
        float idle = 1.0F - clamp01(amt * 6.0F);
        float ph = bi.limbSwing * 0.6662F;

        // personality of the gait
        float hipK = 1.0F;
        float bobK = 1.0F;
        float armK = 1.0F;
        float cross = 0.0F;
        if (bi.style == V_DEMON) { hipK = 1.7F; cross = 0.8F; armK = 0.8F; }
        else if (bi.style == V_ANGEL) { hipK = 0.7F; bobK = 0.45F; armK = 0.55F; cross = 0.3F; }
        else if (bi.style == V_JESTER) { bobK = 1.6F; armK = 1.2F; }
        else if (bi.style == V_GAIA) { hipK = 1.1F; }

        // ---- gait cycle
        float duty = lerp(0.62F, 0.4F, run);
        float stride = lerp(6.2F, 8.6F, run) * amt;
        float lift = lerp(2.1F, 3.6F, run) * clamp01(amt * 1.6F);
        float uR = frac(ph / TAU);
        float uL = frac(ph / TAU + 0.5F);
        float zR = footZ(uR, duty);
        float zL = footZ(uL, duty);
        float lR = footLift(uR, duty);
        float lL = footLift(uL, duty);
        float wR = footLoad(uR, duty);
        float wL = footLoad(uL, duty);
        // running: the heel kicks up behind her early in the swing
        float kickR = run * bump(clamp01(uR / ((1.0F - duty) * 0.6F)));
        float kickL = run * bump(clamp01(uL / ((1.0F - duty) * 0.6F)));
        float footX = HIP_X - cross * 1.2F * amt;
        st[S_FEET] = -footX;
        st[S_FEET + 1] = -(lift * lR + 2.6F * kickR * amt);
        st[S_FEET + 2] = stride * zR + 1.6F * kickR * amt;
        st[S_FEET + 3] = footX;
        st[S_FEET + 4] = -(lift * lL + 2.6F * kickL * amt);
        st[S_FEET + 5] = stride * zL + 1.6F * kickL * amt;

        // pelvis height: lowest just after each contact when walking (inverted pendulum), lowest mid-stance when running (spring-mass)
        float phc = TAU * (1.0F - duty);
        float walkDown = 0.5F + 0.5F * cos(2.0F * (ph - phc - 0.35F));
        float runDown = 0.5F + 0.5F * cos(2.0F * (ph - phc - PI * duty));
        float bob = lerp(0.55F * walkDown, 1.25F * runDown, run) * amt * bobK;
        add(p, ROOT, AY, bob + 0.35F * amt + 0.55F * run);
        // weight over the stance foot: hips slide across, the swing-side hip drops, the shoulders counter-tilt
        float weight = wL - wR;   // > 0: weight on her left leg
        add(p, ROOT, AX, 0.42F * amt * hipK * (1.0F - 0.6F * run) * weight);
        add(p, BODYBOTTOM, ARZ, -0.07F * amt * hipK * (1.0F - 0.5F * run) * weight);
        add(p, BODYTOP, ARZ, 0.05F * amt * hipK * (1.0F - 0.5F * run) * weight);
        // pelvis twists with the forward foot, shoulders counter-rotate against it
        float twist = 0.5F * (zR - zL);
        add(p, BODYBOTTOM, ARY, 0.13F * amt * hipK * twist);
        add(p, BODYTOP, ARY, -0.22F * amt * twist);
        // lean into speed and acceleration, bank into turns
        float lean = 0.05F * amt + 0.24F * run + clamp(st[S_ACC] * 2.6F, -0.12F, 0.2F);
        add(p, BODYBOTTOM, ARX, lean);
        add(p, BODYTOP, ARX, 0.04F * run);
        float bank = clamp(-st[S_YAWRATE] * 2.2F * (0.35F + amt), -0.22F, 0.22F);
        add(p, BODYBOTTOM, ARZ, bank);
        add(p, BODYTOP, ARZ, bank * 0.4F);

        // arms swing against the legs, the elbow flexing a beat later on the forward swing
        float armAmp = lerp(0.55F, 0.9F, run) * amt * armK;
        float pha = phc - PI;
        float swR = -cos(ph - pha);
        add(p, RARM, ARX, armAmp * swR);
        add(p, LARM, ARX, -armAmp * swR);
        float flexR = Math.max(0.0F, cos(ph - pha - 0.45F));
        float flexL = Math.max(0.0F, -cos(ph - pha - 0.45F));
        float elbow = 0.12F * amt + 1.2F * run;
        add(p, RARML, ARX, -elbow - 0.3F * amt * (1.0F - run) * flexR);
        add(p, LARML, ARX, -elbow - 0.3F * amt * (1.0F - run) * flexL);
        add(p, RARM, ARZ, 0.04F * amt + 0.12F * run);
        add(p, LARM, ARZ, -0.04F * amt - 0.12F * run);

        // ---- standing: contrapposto (weight settles on one leg, then the other), breathing
        float wc = sin(TAU * age / 300.0F + 0.7F) * 0.8F + sin(TAU * age / 131.0F) * 0.2F;
        float ws = clamp(wc * 1.8F, -1.0F, 1.0F) * idle * (bi.combat || bi.aim != 0 ? 0.0F : 1.0F);   // > 0: weight on her left leg
        float br = sin(TAU * age / 86.0F);
        add(p, ROOT, AX, 0.4F * ws);
        add(p, ROOT, AY, (0.2F * Math.abs(ws) + 0.12F) * idle);
        add(p, BODYBOTTOM, ARZ, -0.05F * ws);
        add(p, BODYTOP, ARZ, 0.038F * ws);
        add(p, BODYBOTTOM, ARY, 0.025F * ws);
        add(p, BODYTOP, ARX, 0.014F * br);
        add(p, RARM, ARZ, 0.018F * br + 0.04F * cos(age * 0.09F) * idle);
        add(p, LARM, ARZ, -0.018F * br - 0.04F * cos(age * 0.09F) * idle);
        // the unweighted leg relaxes: knee forward, heel up
        float freeR = Math.max(0.0F, ws);
        float freeL = Math.max(0.0F, -ws);
        st[S_FEET] += 0.25F * freeR;
        st[S_FEET + 1] -= 0.35F * freeR;
        st[S_FEET + 2] -= 0.7F * freeR;
        st[S_FEET + 3] -= 0.25F * freeL;
        st[S_FEET + 4] -= 0.35F * freeL;
        st[S_FEET + 5] -= 0.7F * freeL;

        // ---- airborne: knees tuck on the way up, legs reach down on the way down, arms out for balance
        if (air > 0.5F) {
            float rise = clamp01(bi.vy * 5.0F);
            float fall = clamp01(-bi.vy * 3.0F);
            st[S_FEET] = -2.1F;
            st[S_FEET + 1] = -lerp(4.8F, 2.0F, fall);
            st[S_FEET + 2] = lerp(-2.2F, -0.8F, fall);
            st[S_FEET + 3] = 2.1F;
            st[S_FEET + 4] = -lerp(2.4F, 0.6F, fall);
            st[S_FEET + 5] = lerp(1.6F, 0.6F, fall);
            add(p, RARM, ARZ, 0.4F + 0.35F * fall);
            add(p, LARM, ARZ, -0.4F - 0.35F * fall);
            add(p, RARM, ARX, -0.3F - 0.3F * fall);
            add(p, LARM, ARX, -0.25F - 0.3F * fall);
            add(p, BODYBOTTOM, ARX, 0.12F * rise - 0.06F * fall);
            add(p, SKIRT1, ARX, -0.1F - 0.15F * fall);
            add(p, HAIR1, ARX, -0.15F + 0.2F * rise);
        }
        // ---- landing squash: the knees take the impact (IK), the torso folds and recovers
        float lt = age - st[S_LANDT];
        if (st[S_LANDK] > 0.01F && lt >= 0.0F && lt < 16.0F && air < 0.5F) {
            float c = st[S_LANDK] * (lt / 2.2F) * fexp(1.0F - lt / 2.2F);
            add(p, ROOT, AY, 2.8F * c);
            add(p, BODYBOTTOM, ARX, 0.24F * c);
            add(p, BODYTOP, ARX, 0.12F * c);
            add(p, RARM, ARZ, 0.3F * c);
            add(p, LARM, ARZ, -0.3F * c);
            add(p, RARM, ARX, -0.2F * c);
            add(p, LARM, ARX, -0.2F * c);
        }

        // ---- standard wings, tail, hair and gown (the variants' own parts are the motion layer's)
        float flap = cos(age * 0.25F) * (0.06F + amt * 0.2F);
        float wingY = 0.65F + flap - 0.2F * run + 0.55F * air;   // |yRot| grows as the flat wings spread out to the sides
        put(p, RWING, ARY, -wingY);
        put(p, LWING, ARY, wingY);
        put(p, RWING, ARZ, 0.3F * air);
        put(p, LWING, ARZ, -0.3F * air);
        add(p, RWING, ARX, 0.35F * run);
        add(p, LWING, ARX, 0.35F * run);
        float tw = age * 0.1F + bi.limbSwing * 0.5F;
        put(p, TAIL1, ARY, cos(tw) * (0.1F + amt * 0.2F));
        put(p, TAIL2, ARY, cos(tw - 0.6F) * (0.14F + amt * 0.25F));
        put(p, TAIL3, ARY, cos(tw - 1.2F) * (0.18F + amt * 0.3F));
        add(p, TAIL1, ARX, 0.45F * run);
        add(p, TAIL2, ARX, -0.25F * run);
        add(p, TAIL3, ARX, -0.2F * run);
        add(p, HAIR1, ARX, 0.06F * amt + 0.32F * run + sin(age * 0.06F) * 0.03F);
        add(p, HAIR2, ARX, 0.28F * run + cos(age * 0.06F) * 0.04F);
        add(p, SKIRT1, ARX, amt * 0.16F + 0.25F * run + sin(age * 0.06F) * 0.02F);
        add(p, SKIRT2, ARX, amt * 0.1F);
        add(p, SKIRT3, ARX, amt * 0.07F);
        add(p, SKIRT1, ARZ, -0.05F * amt * weight);
    }

    // ------------------------------------------------------------------------------------------------ holding a weapon
    /** combat stance or relaxed carry (only when no aim pose owns the arms); aim poses only set the feet */
    static void stance(float[] p, float[] rest, In bi, float[] st) {
        if (bi.aim != 0) {
            // archer's side-on stance: left foot forward, feet wide
            float still = 1.0F - clamp01(bi.amt * 2.0F);
            blendFeet(st, -2.7F, -0.2F, 1.4F, 2.6F, 0.0F, -1.3F, still);
            add(p, ROOT, AY, 0.6F * still);
            return;
        }
        if (bi.combat) combat(p, rest, bi, st);
        else if (bi.carry) carry(p, rest, bi, st);
    }

    static void blendFeet(float[] st, float rx, float ry, float rz, float lx, float ly, float lz, float w) {
        st[S_FEET] = lerp(st[S_FEET], rx, w);
        st[S_FEET + 1] = lerp(st[S_FEET + 1], ry, w);
        st[S_FEET + 2] = lerp(st[S_FEET + 2], rz, w);
        st[S_FEET + 3] = lerp(st[S_FEET + 3], lx, w);
        st[S_FEET + 4] = lerp(st[S_FEET + 4], ly, w);
        st[S_FEET + 5] = lerp(st[S_FEET + 5], lz, w);
    }

    /** fighting stance: staggered feet, crouch, bladed hips, a breathing bounce, a figure-eight guard sway and a periodic re-grip */
    static void combat(float[] p, float[] rest, In bi, float[] st) {
        int k = bi.kind < 0 || bi.kind > 9 ? 1 : bi.kind;
        int o = k * 9;
        int g = k * 8;
        float age = bi.age;
        float still = 1.0F - clamp01(bi.amt * 2.0F);
        float lead = STANCE[o];
        float stag = STANCE[o + 1];
        float wid = STANCE[o + 2];
        float crouch = STANCE[o + 3];
        float bounceAmp = STANCE[o + 6];
        float bper = STANCE[o + 7];
        float sway = STANCE[o + 8];
        float bounce = 0.5F - 0.5F * cos(TAU * age / bper);
        float springy = k == K_FIST || k == K_DAGGER || k == K_DUAL ? 1.0F : 0.0F;
        // feet: the lead foot ahead, the back foot on its ball when she is light on her feet
        float rz = lead > 0.0F ? stag : -stag;
        float backY = -(0.35F + 0.35F * bounce) * springy;
        blendFeet(st, -wid, lead > 0.0F ? backY : 0.0F, rz, wid, lead > 0.0F ? 0.0F : backY, -rz, still);
        // crouch with a breathing bounce; the weight drifts slowly from foot to foot
        add(p, ROOT, AY, (crouch + bounceAmp * bounce) * still + crouch * 0.45F * (1.0F - still));
        float drift = sin(TAU * age / 97.0F + 0.4F);
        add(p, ROOT, AX, 0.3F * drift * still);
        add(p, BODYBOTTOM, ARZ, -0.04F * drift * still);
        add(p, BODYTOP, ARZ, 0.03F * drift * still);
        // hips bladed toward the lead foot, chest squared a little back toward the target, a slight hunch
        add(p, BODYBOTTOM, ARY, STANCE[o + 4] * (0.4F + 0.6F * still));
        add(p, BODYTOP, ARY, STANCE[o + 5] * (0.4F + 0.6F * still));
        add(p, BODYBOTTOM, ARX, 0.07F);
        add(p, BODYTOP, ARX, 0.1F + 0.025F * sin(TAU * age / bper));
        add(p, HEAD, ARX, 0.06F);
        // arms: the guard, a little of the walking swing, the weapon tip drawing a slow figure eight, and a re-grip every ~6 seconds
        float rsw = (val(p, RARM, ARX) - rest[RARM * STRIDE + ARX]) * 0.25F;
        float lsw = (val(p, LARM, ARX) - rest[LARM * STRIDE + ARX]) * 0.25F;
        float w8 = TAU * age / 46.0F;
        float sx = sin(w8) * sway;
        float sy = sin(2.0F * w8) * sway * 0.7F;
        float grip = bump(frac(age / 128.0F) * 6.0F);
        put(p, RARM, ARX, GUARD[g] + rsw + sx - 0.1F * grip);
        put(p, RARM, ARY, GUARD[g + 1] + sy);
        put(p, RARM, ARZ, GUARD[g + 2]);
        put(p, RARML, ARX, GUARD[g + 3] + sin(w8 - 0.7F) * sway * 0.8F - 0.14F * grip);
        put(p, LARM, ARX, GUARD[g + 4] + lsw - sx * 0.5F);
        put(p, LARM, ARY, GUARD[g + 5] - sy * 0.4F);
        put(p, LARM, ARZ, GUARD[g + 6]);
        put(p, LARML, ARX, GUARD[g + 7] + sin(w8 + 1.2F) * sway * 0.5F);
        add(p, BODYTOP, ARX, 0.035F * grip);
        add(p, ROOT, AY, 0.3F * grip * still);
        // alert wings, lashing tail
        float pulse = sin(age * 0.16F);
        put(p, RWING, ARY, -0.85F - 0.06F * pulse);
        put(p, LWING, ARY, 0.85F + 0.06F * pulse);
        add(p, RWING, ARZ, 0.2F + 0.04F * pulse);
        add(p, LWING, ARZ, -0.2F - 0.04F * pulse);
        float tr = age * 0.26F;
        add(p, TAIL1, ARX, 0.4F + sin(tr) * 0.05F);
        add(p, TAIL1, ARY, cos(tr) * 0.18F);
        add(p, TAIL2, ARY, cos(tr - 0.5F) * 0.26F);
        add(p, TAIL3, ARY, cos(tr - 1.0F) * 0.34F);
    }

    /** relaxed carry with an idle breaker every ~11 seconds while she stands still */
    static void carry(float[] p, float[] rest, In bi, float[] st) {
        int k = bi.kind < 0 || bi.kind > 9 ? 1 : bi.kind;
        int c = k * 9;
        int busy = (int) CARRY[c + 8];
        float rsw = (val(p, RARM, ARX) - rest[RARM * STRIDE + ARX]) * 0.3F;
        float lsw = (val(p, LARM, ARX) - rest[LARM * STRIDE + ARX]) * 0.3F;
        float idle = 1.0F - clamp01(bi.amt * 6.0F);
        if ((busy & 1) != 0) {
            put(p, RARM, ARX, CARRY[c] + rsw);
            put(p, RARM, ARY, CARRY[c + 1]);
            put(p, RARM, ARZ, CARRY[c + 2]);
            put(p, RARML, ARX, CARRY[c + 3] + rsw * 0.4F);
        }
        if ((busy & 2) != 0) {
            put(p, LARM, ARX, CARRY[c + 4] + lsw);
            put(p, LARM, ARY, CARRY[c + 5]);
            put(p, LARM, ARZ, CARRY[c + 6]);
            put(p, LARML, ARX, CARRY[c + 7] + lsw * 0.4F);
        }
        // a heavy blade on the shoulder bounces with her steps
        if (k == K_HEAVY) add(p, RARM, ARX, 0.06F * (val(p, ROOT, AY) - rest[ROOT * STRIDE + AY]));
        // idle breaker: a 44-tick weapon action every 230 ticks (not during flourishes, not while walking)
        float x = frac((bi.age + 37.0F) / 230.0F) * 230.0F / 44.0F;
        if (x >= 1.0F || bi.flourish || !bi.calm) return;
        float env = sstep(0.0F, 0.18F, x) * (1.0F - sstep(0.82F, 1.0F, x)) * idle;
        if (env <= 0.0F) return;
        float cyc = x * TAU * 2.0F;
        if (k == K_SWORD || k == K_DAGGER) {   // a wrist twirl, eyes on the blade
            blendArm(p, RARM, -1.05F + sin(cyc) * 0.15F, sin(cyc) * 0.5F, cos(cyc) * 0.3F + 0.1F, -0.6F, env);
            add(p, HEAD, ARX, 0.2F * env);
            add(p, HEAD, ARY, -0.2F * env);
        } else if (k == K_DUAL) {               // both blades spun and crossed
            blendArm(p, RARM, -1.05F + sin(cyc) * 0.18F, sin(cyc) * 0.45F, cos(cyc) * 0.25F, -0.6F, env);
            blendArm(p, LARM, -1.05F - sin(cyc) * 0.18F, -sin(cyc) * 0.45F, -cos(cyc) * 0.25F, -0.6F, env);
            add(p, HEAD, ARX, 0.15F * env);
        } else if (k == K_HEAVY) {              // re-seats the blade on her shoulder with a shrug
            float hop = bump(clamp01(x * 2.0F - 0.3F));
            add(p, RARM, ARX, -0.25F * hop * env);
            add(p, BODYTOP, ARZ, 0.06F * hop * env);
            add(p, ROOT, AY, 0.5F * hop * env);
            add(p, HEAD, ARZ, -0.08F * env);
        } else if (k == K_AXE) {                // tosses the axe up a little and catches it
            float toss = bump(clamp01(x * 1.6F - 0.2F));
            blendArm(p, RARM, -0.7F - 0.5F * toss, 0.1F, 0.2F, -1.0F + 0.4F * toss, env);
            add(p, HEAD, ARX, -0.12F * toss * env);
        } else if (k == K_SPEAR || k == K_STAFF) {   // taps the butt on the ground twice
            float tap = Math.abs(sin(x * PI * 2.0F));
            add(p, RARM, ARX, -0.18F * tap * env);
            add(p, LARM, ARX, -0.12F * tap * env);
            add(p, ROOT, AY, 0.35F * tap * env);
        } else if (k == K_BOW) {                // lifts the bow and checks the string
            blendArm(p, RARM, -1.4F, 0.1F, 0.0F, -1.1F, env);
            blendArm(p, LARM, -1.2F, 0.6F, 0.0F, -0.9F, env);
            add(p, HEAD, ARY, -0.3F * env);
            add(p, HEAD, ARX, 0.15F * env);
        } else if (k == K_CROSSBOW) {           // checks the bolt groove
            add(p, HEAD, ARX, 0.35F * env);
            add(p, RARM, ARX, -0.25F * env);
        }
    }

    static void blendArm(float[] p, int arm, float x, float y, float z, float l, float w) {
        put(p, arm, ARX, lerp(val(p, arm, ARX), x, w));
        put(p, arm, ARY, lerp(val(p, arm, ARY), y, w));
        put(p, arm, ARZ, lerp(val(p, arm, ARZ), z, w));
        put(p, arm + 1, ARX, lerp(val(p, arm + 1, ARX), l, w));
    }

    // ------------------------------------------------------------------------------------------------ attacks
    /** one channel of a swing at phase q: base -> wind-up (settling into the coil) -> strike (snap) -> follow-through (overshoot) -> base */
    static float key(Swing s, int i, float base, float q) {
        float x = clamp01(q);
        if (x < SA) return lerp(base, s.windup[i], outCubic(x / SA));
        if (x < SB) return lerp(s.windup[i], s.strike[i], outQuart((x - SA) / (SB - SA)));
        if (x < SC) return lerp(s.strike[i], s.follow[i], outBack((x - SB) / (SC - SB)));
        return lerp(s.follow[i], base, inOutSine((x - SC) / (1.0F - SC)));
    }

    /**
     * An attack at progress q (0..1) blended over the stance: the hips fire first, then the chest, the arm and a whipping forearm; the lead
     * foot steps into the lunge and back out, the back foot pushes off its toes; the impact shakes through the arm.
     */
    static void swing(float[] p, float[] rest, In bi, float[] st, Swing s, float q) {
        if (q < 0.0F || q >= 1.0F) return;
        float qp = q + 0.05F;
        float qc = q + 0.025F;
        float qf = q - 0.035F;
        float yawP = key(s, 0, 0.0F, qp);
        float yawC = key(s, 0, 0.0F, qc);
        float pitC = key(s, 1, 0.0F, qc);
        add(p, BODYBOTTOM, ARY, yawP * 0.45F);
        add(p, BODYMIDDLE, ARY, yawC * 0.15F);
        add(p, BODYTOP, ARY, yawC * 0.5F);
        add(p, BODYMIDDLE, ARX, pitC * 0.4F);
        add(p, BODYTOP, ARX, pitC * 0.6F);
        // arms from where the stance had them
        float r0 = val(p, RARM, ARX);
        float r1 = val(p, RARM, ARY);
        float r2 = val(p, RARM, ARZ);
        float r3 = val(p, RARML, ARX);
        float l0 = val(p, LARM, ARX);
        float l1 = val(p, LARM, ARY);
        float l2 = val(p, LARM, ARZ);
        float l3 = val(p, LARML, ARX);
        put(p, RARM, ARX, key(s, 2, r0, q));
        put(p, RARM, ARY, key(s, 3, r1, q));
        put(p, RARM, ARZ, key(s, 4, r2, q));
        put(p, RARML, ARX, key(s, 5, r3, qf));
        float ql = q - s.leftDelay;
        put(p, LARM, ARX, key(s, 6, l0, ql));
        put(p, LARM, ARY, key(s, 7, l1, ql));
        put(p, LARM, ARZ, key(s, 8, l2, ql));
        put(p, LARML, ARX, key(s, 9, l3, ql - 0.035F));
        // weight: an anticipation dip back, then the lunge and crouch (the pelvis leads)
        float lunge = key(s, 10, 0.0F, qp);
        float crouch = key(s, 11, 0.0F, qp);
        float antic = bump(clamp01(q / (SA + 0.06F)));
        add(p, ROOT, AZ, -lunge + 0.6F * antic);
        add(p, ROOT, AY, crouch * 0.85F + 0.35F * antic);
        // footwork
        int kk = s.kind < 0 || s.kind > 9 ? 1 : s.kind;
        boolean leftLead = STANCE[kk * 9] > 0.0F;
        int fo = leftLead ? S_FEET + 3 : S_FEET;
        int bo = leftLead ? S_FEET : S_FEET + 3;
        float big = clamp01((Math.max(s.strike[10], s.follow[10]) - 0.8F) / 1.6F);
        float reach = Math.max(0.0F, lunge);
        float stepIn = bump(clamp01((q - 0.04F) / (SB - 0.02F)));
        float stepOut = bump(clamp01((q - SC) / (1.0F - SC)));
        st[fo + 2] -= reach * 1.2F;
        st[fo + 1] -= (1.9F * stepIn + 1.1F * stepOut) * big;
        st[bo + 1] -= 0.55F * clamp01(reach / 3.0F);
        // the impact rings through the arm and chest for a moment
        float ip = (q - SB) / 0.16F;
        if (ip > 0.0F && ip < 1.0F) {
            float shake = sin(ip * 3.0F * PI) * (1.0F - ip) * 0.045F;
            add(p, BODYTOP, ARX, shake);
            add(p, RARM, ARX, shake * 1.5F);
            add(p, RARML, ARX, shake);
        }
        if (s.spin != 0.0F) add(p, ROOT, ARY, s.spin * inOutSine(clamp01((q - SA) / (SC - SA))));
        // the rest of the body follows through: wings flare, tail and hair lag behind the torso, the gown swirls
        float flare = sin(clamp01((q - SA) / (SC - SA)) * PI);
        put(p, RWING, ARY, lerp(val(p, RWING, ARY), -1.15F, flare * 0.6F));
        put(p, LWING, ARY, lerp(val(p, LWING, ARY), 1.15F, flare * 0.6F));
        add(p, RWING, ARZ, 0.3F * flare);
        add(p, LWING, ARZ, -0.3F * flare);
        add(p, TAIL1, ARY, -yawC * 0.6F);
        add(p, TAIL2, ARY, -yawC * 0.4F);
        add(p, HAIR1, ARY, -yawC * 0.3F);
        add(p, HAIR2, ARX, 0.2F * flare);
        add(p, SKIRT1, ARY, yawC * 0.2F);
        add(p, SKIRT1, ARX, 0.12F * flare);
    }

    // ------------------------------------------------------------------------------------------------ last pass
    /** solves the legs onto their foot targets, steadies the head and runs the springs of the tail, hair, gown and wings */
    static void finish(float[] p, float[] rest, In bi, float[] st) {
        float dt = bi.dt < 0.0F ? 0.0F : (bi.dt > 4.0F ? 4.0F : bi.dt);
        float h = dt < 0.001F ? 0.001F : dt;
        float ox = val(p, ROOT, AX) - rest[ROOT * STRIDE + AX];
        float oy = val(p, ROOT, AY) - rest[ROOT * STRIDE + AY];
        float oz = val(p, ROOT, AZ) - rest[ROOT * STRIDE + AZ];
        if (!bi.riding) {
            legIK(p, RLEG, RLEGL, -HIP_X, ox, oy, oz, st[S_FEET], st[S_FEET + 1], st[S_FEET + 2]);
            legIK(p, LLEG, LLEGL, HIP_X, ox, oy, oz, st[S_FEET + 3], st[S_FEET + 4], st[S_FEET + 5]);
        }
        // head stabilisation: the gaze holds while the spine twists and bends under it (aim poses aim the head themselves)
        if (bi.aim == 0) {
            float sy = 0.0F;
            float sx = 0.0F;
            float sz = 0.0F;
            for (int q = BODYBOTTOM; q <= NECK; q++) {
                if (q == BODYTOPF) continue;
                sy += val(p, q, ARY) - rest[q * STRIDE + ARY];
                sx += val(p, q, ARX) - rest[q * STRIDE + ARX];
                sz += val(p, q, ARZ) - rest[q * STRIDE + ARZ];
            }
            add(p, HEAD, ARY, -0.85F * sy);
            add(p, HEAD, ARX, -0.55F * sx);
            add(p, HEAD, ARZ, -0.6F * sz);
        }
        // springs driven by how the pelvis turns and the body rises and falls
        float pel = val(p, BODYBOTTOM, ARY);
        float ry = val(p, ROOT, AY);
        float dPel = dt < 0.001F ? 0.0F : wrap(pel - st[S_PELVY]) / h;
        float dRy = dt < 0.001F ? 0.0F : (ry - st[S_ROOTY]) / h;
        st[S_PELVY] = pel;
        st[S_ROOTY] = ry;
        float turn = clamp(dPel * 2.2F + st[S_YAWRATE] * 2.5F, -0.7F, 0.7F);
        float drop = clamp(dRy * 0.18F, -0.5F, 0.5F);
        float tA = spring(st, S_SPR, -turn, 11.0F, 0.35F, dt);
        float tB = spring(st, S_SPR + 2, tA, 15.0F, 0.3F, dt);
        add(p, TAIL1, ARY, tA * 0.45F);
        add(p, TAIL2, ARY, tA * 0.4F + tB * 0.4F);
        add(p, TAIL3, ARY, tB * 0.9F);
        float hz = spring(st, S_SPR + 4, drop - st[S_ACC] * 3.0F, 13.0F, 0.3F, dt);
        add(p, HAIR1, ARX, hz * 0.5F);
        add(p, HAIR2, ARX, hz * 0.7F);
        add(p, TAIL1, ARX, -hz * 0.4F);
        float sk = spring(st, S_SPR + 6, drop * 0.8F - st[S_ACC] * 2.0F, 10.0F, 0.32F, dt);
        add(p, SKIRT1, ARX, sk * 0.35F);
        add(p, SKIRT2, ARX, sk * 0.3F);
        add(p, SKIRT3, ARX, sk * 0.3F);
        float wg = spring(st, S_SPR + 8, drop, 12.0F, 0.28F, dt);
        add(p, RWING, ARZ, wg * 0.5F);
        add(p, LWING, ARZ, -wg * 0.5F);
    }
    //@END-JS
}
