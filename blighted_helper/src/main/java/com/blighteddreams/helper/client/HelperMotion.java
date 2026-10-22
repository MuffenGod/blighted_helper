package com.blighteddreams.helper.client;

/**
 * The motion layer of the four Gaia-model helpers: data-driven animation tracks (keyframes + oscillators) for idles, walks, runs and flourishes,
 * group "spread" channels (wing fans), and reactive spring dynamics for hair, cloth, tails, feathers and the halo.
 *
 * Pure math on pose arrays, no Minecraft classes. Everything between the BEGIN-JS and END-JS markers is written in a restricted Java subset and is
 * transpiled to JavaScript by design/entitykit/anim/java2js.js, so the model studio runs exactly the code the game runs.
 *
 * A pose is a float[] with STRIDE (8) floats per part: x, y, z, xRot, yRot, zRot, visibility (0/1) and a uniform scale multiplier.
 * Channels are addressed as part * STRIDE + axis; channels at or above chBase are "extra" channels (fx.*, spread.*).
 */
public final class HelperMotion {
    private HelperMotion() {}

    // ---- helpers the JS prelude re-implements (Java-only constructs live outside the transpiled region)
    private static float sin(float x) { return (float) Math.sin(x); }
    private static float cos(float x) { return (float) Math.cos(x); }
    private static float fexp(float x) { return (float) Math.exp(x); }
    private static int ifloor(float x) { int i = (int) x; return x < (float) i ? i - 1 : i; }
    private static void fill(float[] a, int n, float v) { for (int i = 0; i < n; i++) a[i] = v; }

    //@BEGIN-JS
    static final float PI = 3.14159265F;
    static final float TAU = 6.2831853F;
    static final int STRIDE = 8;
    static final int AX = 0, AY = 1, AZ = 2, ARX = 3, ARY = 4, ARZ = 5, AVIS = 6, ASC = 7;
    // standard part indices (the rest of the pose array is the variant's extra parts)
    static final int ROOT = 0, BODYBOTTOM = 1, BODYMIDDLE = 2, BODYTOP = 3, BODYTOPF = 4, NECK = 5, HEAD = 6, HAIR1 = 7, HAIR2 = 8;
    static final int RARM = 9, RARML = 10, LARM = 11, LARML = 12, RWING = 13, LWING = 14, SKIRT1 = 15, SKIRT2 = 16, SKIRT3 = 17;
    static final int TAIL1 = 18, TAIL2 = 19, TAIL3 = 20, RLEG = 21, RLEGL = 22, LLEG = 23, LLEGL = 24, NSTD = 25;

    /** one animated channel: keyframes [t, v, ease] triples and oscillators [amp, period, phase, enveloped] quads */
    static final class Track {
        public int ch;
        public int mode;          // 0 = add to the pose, 1 = blend toward the value
        public float[] keys;
        public int nkeys;
        public float[] osc;
        public int nosc;
        public float wIn;
        public float wOut;
        public float loop;        // > 0: the keys repeat every loop ticks
        public int tb;            // time base of the oscillators: 0 = ticks, 1 = walk phase (cycles per step cycle)
    }

    static final class Clip {
        public float dur;
        public Track[] tracks;
        public int ntracks;
    }

    static final class VariantMotion {
        public Clip idle;
        public Clip walk;
        public Clip run;
        public Clip[] flourish;
        public int nflourish;
    }

    /** what she is doing this frame */
    static final class In {
        public float age;         // ticks
        public float dt;          // ticks since the last frame
        public float limbSwing;
        public float amt;         // 0..1 walking amount
        public float run;         // 0..1
        public float air;
        public float idle;        // 0..1 how still she is
        public boolean calm;      // no combat, no aim pose, not downed
        public int fl;            // flourish index or -1
        public float flT;         // flourish progress 0..1
        public float vf;          // local forward velocity, blocks per tick
        public float vl;          // local leftward velocity
        public int busy;          // arms holding a weapon (1 right, 2 left): the idle and walk clips leave them alone
    }

    /** the rig binding: which pose slots spring, sway and spread */
    static final class Bind {
        public int nparts;
        public int chBase;
        public int nxch;
        public float[] xdef;
        public int nsim;
        public int[] simPart;
        public float[] simPhi;
        public boolean[] simRing;
        public float[] simPar;    // per sim part: period (ticks), damping ratio, gain
        public float[] simSway;   // per sim part: ax, ay, az, speed, phase
        public float[] simV;      // per sim part: coupling of vertical acceleration into x and z rotation
        public float[] simG;      // per sim part: counter-rotation against the body's pitch / roll (hanging parts want to stay vertical)
        public int[] simDepth;
        public int ngrp;
        public int[] grpPart;
        public int[] grpXch;
        public float[] grpAng;    // the part's in-plane fan angle from straight down (radians)
    }

    static float clamp01(float x) {
        return x < 0.0F ? 0.0F : (x > 1.0F ? 1.0F : x);
    }

    static float sstep(float a, float b, float x) {
        float t = clamp01((x - a) / (b - a));
        return t * t * (3.0F - 2.0F * t);
    }

    static float wrap(float a) {
        float r = a;
        if (r > PI) r -= TAU;
        if (r < -PI) r += TAU;
        return r;
    }

    static float ease(int e, float x) {
        if (e == 0) return x;
        if (e == 1) return 0.5F - 0.5F * cos(PI * x);
        if (e == 2) {
            float u = 1.0F - x;
            return 1.0F - u * u * u;
        }
        if (e == 3) return x * x * x;
        if (e == 4) {
            if (x < 0.5F) return 4.0F * x * x * x;
            float u = -2.0F * x + 2.0F;
            return 1.0F - u * u * u / 2.0F;
        }
        if (e == 5) {
            float c1 = 1.70158F;
            float c3 = c1 + 1.0F;
            float u = x - 1.0F;
            return 1.0F + c3 * u * u * u + c1 * u * u;
        }
        if (e == 6) {
            if (x <= 0.0F) return 0.0F;
            if (x >= 1.0F) return 1.0F;
            return fexp(-6.931F * x) * sin((x * 10.0F - 0.75F) * 2.0944F) + 1.0F;
        }
        if (e == 7) return x < 1.0F ? 0.0F : 1.0F;
        return x;
    }

    static float keyValue(float[] k, int n, float t) {
        if (n <= 0) return 0.0F;
        if (t <= k[0]) return k[1];
        for (int i = 1; i < n; i++) {
            float t1 = k[i * 3];
            if (t <= t1) {
                float t0 = k[i * 3 - 3];
                float v0 = k[i * 3 - 2];
                float v1 = k[i * 3 + 1];
                float u = t1 > t0 ? (t - t0) / (t1 - t0) : 1.0F;
                return v0 + (v1 - v0) * ease(ifloor(k[i * 3 + 2] + 0.5F), u);
            }
        }
        return k[n * 3 - 2];
    }

    static float trackValue(Track tr, float t, float age, float walkPh, float env) {
        float v = 0.0F;
        if (tr.nkeys > 0) {
            float tt = t;
            if (tr.loop > 0.0F) tt = (age % tr.loop) / tr.loop;
            v = keyValue(tr.keys, tr.nkeys, tt);
        }
        for (int i = 0; i < tr.nosc; i++) {
            float a = tr.osc[i * 4];
            float p = tr.osc[i * 4 + 1];
            float ph = tr.osc[i * 4 + 2];
            float em = tr.osc[i * 4 + 3];
            float arg = tr.tb == 1 ? walkPh * p + ph : TAU * age / p + ph;
            v += a * sin(arg) * (em > 0.5F ? env : 1.0F);
        }
        return v;
    }

    /** k scales the track (idle / walk amount); timed tracks fade in and out over wIn / wOut of the clip */
    static void applyTrack(Track tr, float[] pose, float[] xch, int chBase, float t, float age, float walkPh, boolean timed, float k) {
        float env = 1.0F;
        if (timed) env = sstep(0.0F, tr.wIn, t) * (1.0F - sstep(1.0F - tr.wOut, 1.0F, t));
        float v = trackValue(tr, t, age, walkPh, env);
        if (tr.ch >= chBase) {
            int xi = tr.ch - chBase;
            if (tr.mode == 1) xch[xi] = xch[xi] + (v - xch[xi]) * env * k;
            else xch[xi] += v * k;
            return;
        }
        if (tr.mode == 1) pose[tr.ch] = pose[tr.ch] + (v - pose[tr.ch]) * env * k;
        else pose[tr.ch] += v * k;
    }

    static boolean busyCh(int ch, int busy) {
        if ((busy & 1) != 0 && ch >= RARM * STRIDE && ch < (RARML + 1) * STRIDE) return true;
        return (busy & 2) != 0 && ch >= LARM * STRIDE && ch < (LARML + 1) * STRIDE;
    }

    static void applyClip(Clip c, float[] pose, float[] xch, int chBase, float t, float age, float walkPh, boolean timed, float k, int busy) {
        if (c == null || k <= 0.001F) return;
        for (int i = 0; i < c.ntracks; i++) {
            if (busy != 0 && busyCh(c.tracks[i].ch, busy)) continue;
            applyTrack(c.tracks[i], pose, xch, chBase, t, age, walkPh, timed, k);
        }
    }

    /** reactive dynamics: damped springs per chain part, driven by how the body moves, plus the ambient sway */
    static void springs(float[] pose, float[] rest, Bind b, In st, float[] sim) {
        float dt = st.dt < 0.001F ? 0.001F : st.dt;
        boolean reset = sim[3] < 0.5F || dt > 4.0F;
        float rootYaw = pose[ROOT * STRIDE + ARY];
        float rootY = pose[ROOT * STRIDE + AY];
        float dyaw = 0.0F;
        float dyv = 0.0F;
        float dvf = 0.0F;
        float accY = 0.0F;
        float dpr = 0.0F;
        float rootX = pose[ROOT * STRIDE + ARX];
        if (!reset) {
            dpr = wrap(rootX - sim[5]) / dt;
            dyaw = wrap(rootYaw - sim[0]) / dt;
            dyv = -(rootY - sim[1]) / dt;
            dvf = (st.vf - sim[2]) / dt;
            accY = (dyv - sim[4]) / dt;
        } else {
            for (int i = 8; i < 8 + b.nsim * 4; i++) sim[i] = 0.0F;
        }
        sim[0] = rootYaw;
        sim[1] = rootY;
        sim[2] = st.vf;
        sim[3] = 1.0F;
        sim[4] = dyv;
        sim[5] = rootX;
        accY = accY > 0.6F ? 0.6F : (accY < -0.6F ? -0.6F : accY);
        // body-frame drive: moving forward / turning drags hanging parts back and sideways
        float bx = 2.6F * st.vf + 4.0F * dvf - 3.0F * dpr;
        float bz = 2.6F * st.vl - 3.2F * dyaw;
        float spin = dyaw < 0.0F ? -dyaw : dyaw;
        float flare = 3.4F * spin + 0.5F * (dyv < 0.0F ? -dyv : 0.0F);
        bx = bx > 0.9F ? 0.9F : (bx < -0.9F ? -0.9F : bx);
        bz = bz > 0.9F ? 0.9F : (bz < -0.9F ? -0.9F : bz);
        flare = flare > 0.7F ? 0.7F : flare;
        // how far the torso, neck and head are bent / tilted away from rest this frame
        float pitch = 0.0F;
        float roll = 0.0F;
        for (int q = BODYBOTTOM; q <= HEAD; q++) {
            if (q == BODYTOPF) continue;
            pitch += pose[q * STRIDE + ARX] - rest[q * STRIDE + ARX];
            roll += pose[q * STRIDE + ARZ] - rest[q * STRIDE + ARZ];
        }
        int steps = ifloor(dt / 0.6F) + 1;
        float h = dt / (float) steps;
        for (int j = 0; j < b.nsim; j++) {
            int part = b.simPart[j];
            float period = b.simPar[j * 3];
            float zeta = b.simPar[j * 3 + 1];
            float gain = b.simPar[j * 3 + 2];
            period = period * (1.0F + 0.07F * (float) b.simDepth[j]);
            float w = TAU / period;
            float w2 = w * w;
            float tx = bx;
            float tz = bz;
            if (b.simRing[j]) {
                float c = cos(b.simPhi[j]);
                float s = sin(b.simPhi[j]);
                tx = bx * c + bz * s - flare;
                tz = -bx * s + bz * c;
            } else {
                tx = bx + flare * 0.6F;
            }
            tx = tx * gain + b.simV[j * 2] * accY - b.simG[j] * pitch;
            tz = tz * gain + b.simV[j * 2 + 1] * accY - b.simG[j] * roll;
            int o = 8 + j * 4;
            float thx = sim[o];
            float thz = sim[o + 1];
            float vx = sim[o + 2];
            float vz = sim[o + 3];
            for (int s2 = 0; s2 < steps; s2++) {
                vx += (w2 * (tx - thx) - 2.0F * zeta * w * vx) * h;
                vz += (w2 * (tz - thz) - 2.0F * zeta * w * vz) * h;
                thx += vx * h;
                thz += vz * h;
            }
            thx = thx > 1.3F ? 1.3F : (thx < -1.3F ? -1.3F : thx);
            thz = thz > 1.3F ? 1.3F : (thz < -1.3F ? -1.3F : thz);
            sim[o] = thx;
            sim[o + 1] = thz;
            sim[o + 2] = vx;
            sim[o + 3] = vz;
            // ambient sway, a little stronger while she walks
            float ax = b.simSway[j * 5];
            float ay = b.simSway[j * 5 + 1];
            float az = b.simSway[j * 5 + 2];
            float sp = b.simSway[j * 5 + 3];
            float ph = st.age * sp + b.simSway[j * 5 + 4];
            float amp = 1.0F + 1.6F * st.amt;
            pose[part * STRIDE + ARX] += thx + sin(ph) * ax * amp;
            pose[part * STRIDE + ARY] += sin(ph + 1.3F) * ay * amp;
            pose[part * STRIDE + ARZ] += thz + sin(ph + 2.1F) * az * amp;
        }
    }

    /** the full motion pass for one frame; pose already holds the base locomotion / combat pose */
    static void run(float[] pose, float[] rest, float[] xch, VariantMotion vm, In st, Bind b, float[] sim) {
        int chBase = b.chBase;
        for (int i = 0; i < b.nxch; i++) xch[i] = b.xdef[i];
        float walkPh = st.limbSwing * 0.6662F;
        // calm blends in and out (about four ticks) so leaving or entering combat never snaps the idle layer
        float cs = sim[6];
        float cr = st.dt * 0.3F;
        if (cr > 1.0F) cr = 1.0F;
        cs = cs + ((st.calm ? 1.0F : 0.0F) - cs) * cr;
        sim[6] = cs;
        if (vm != null) {
            float idleK = cs * sstep(0.5F, 0.9F, st.idle);
            applyClip(vm.idle, pose, xch, chBase, 0.0F, st.age, walkPh, false, idleK, st.busy);
            float walkK = st.amt * (1.0F - st.run * 0.6F);
            applyClip(vm.walk, pose, xch, chBase, 0.0F, st.age, walkPh, false, walkK, st.busy);
            applyClip(vm.run, pose, xch, chBase, 0.0F, st.age, walkPh, false, st.run, st.busy);
            if (st.fl >= 0 && st.fl < vm.nflourish) applyClip(vm.flourish[st.fl], pose, xch, chBase, st.flT, st.age, walkPh, true, cs, 0);
        }
        // fan groups: each part scales its in-plane fan angle by the group's spread channel (1 = as built, < 1 folds toward straight down)
        for (int g = 0; g < b.ngrp; g++) {
            int pi = b.grpPart[g] * STRIDE + ARZ;
            float sgn = rest[pi] < 0.0F ? -1.0F : 1.0F;
            pose[pi] += sgn * b.grpAng[g] * (xch[b.grpXch[g]] - 1.0F);
        }
        springs(pose, rest, b, st, sim);
    }
    //@END-JS

    // ---------------------------------------------------------------------------------------------------- Java-side convenience
    public static Track newTrack() {
        Track t = new Track();
        t.wIn = 0.15F;
        t.wOut = 0.15F;
        return t;
    }

    /** extra-channel defaults: spread.* and fx.glow rest at 1, everything else at 0 */
    public static float defaultFor(String name) {
        return name.startsWith("spread.") || name.equals("fx.glow") ? 1.0F : 0.0F;
    }

    static void zero(float[] a) {
        fill(a, a.length, 0.0F);
    }
}
