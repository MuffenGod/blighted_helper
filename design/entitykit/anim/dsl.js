// Tiny authoring DSL for helper motion data (see HelperMotion.java for the semantics).
// Channels are "part.axis": axes x y z xRot yRot zRot vis sc; plus "fx.*", "spread.<group>" and "ev.*" extra channels.
// Rotation sign cheat sheet (the rig is y-down, front = -z, her left = +x):
//   arms hang down: xRot < 0 swings forward/up, xRot > 0 back; right arm zRot > 0 = out, left arm zRot < 0 = out; forearm xRot < 0 bends the elbow
//   legs: xRot < 0 swings forward; lower leg xRot > 0 bends the knee; head xRot > 0 looks down, zRot tilts; body xRot > 0 leans forward
//   root.y < 0 lifts her up; root.yRot spins her about the vertical.
const E = { lin: 0, sine: 1, out: 2, in: 3, inout: 4, back: 5, elastic: 6, step: 7 };

// continuous oscillator (period in ticks). env=1 fades it with the flourish window
const osc = (c, amp, period, phase = 0, env = 0, extra = {}) => ({ c, m: 'add', o: [[amp, period, phase, env]], ...extra });
const oscN = (c, list, extra = {}) => ({ c, m: 'add', o: list.map(([a, p, ph = 0, env = 0]) => [a, p, ph, env]), ...extra });
// walk-synced oscillator: p cycles per step cycle
const wosc = (c, amp, p, phase = 0, extra = {}) => ({ c, m: 'add', o: [[amp, p, phase, 0]], tb: 1, ...extra });
// constant offset
const cst = (c, v, extra = {}) => ({ c, m: 'add', k: [[0, v, 0]], ...extra });
// keyframes in flourish progress 0..1: [[t, v, ease], ...]; m = 'add' | 'set'
const key = (c, ks, m = 'add', extra = {}) => ({ c, m, k: ks.map(([t, v, e = E.sine]) => [t, v, e]), ...extra });
// "set" track: blend toward the keyed value (arms, poses). in/out = fade window
const set = (c, ks, extra = {}) => key(c, ks, 'set', extra);
// a looping key track (period in ticks)
const loop = (c, ks, ticks, m = 'add', extra = {}) => ({ c, m, k: ks.map(([t, v, e = E.sine]) => [t, v, e]), loop: ticks, ...extra });
// hold a pose: blend an arm/leg/etc to constant values for the whole window
const hold = (c, v, extra = {}) => set(c, [[0, v, 0], [1, v, 0]], extra);

// sample a function of progress into keyframes (for nonlinear paths: flips, orbits)
const sampled = (c, fn, n = 24, m = 'add', extra = {}) => ({ c, m, k: Array.from({ length: n + 1 }, (_, i) => [i / n, fn(i / n), 0]), ...extra });
// event pulses (particles, sounds): value 1 at the given times
const pulses = (c, times, w = 0.012) => ({ c, m: 'add', k: [[0, 0, 0], ...times.flatMap((t) => [[Math.max(0, t - w), 0, 0], [t, 1, 0], [t + w, 0, 0]]), [1.0001, 0, 0]] });
// absolute arm poses: [shoulder x, y, z, forearm x]
const armR = (v, extra = {}) => [hold('rightarm.xRot', v[0], extra), hold('rightarm.yRot', v[1], extra), hold('rightarm.zRot', v[2], extra), hold('rightarmlower.xRot', v[3], extra)];
const armL = (v, extra = {}) => [hold('leftarm.xRot', v[0], extra), hold('leftarm.yRot', v[1], extra), hold('leftarm.zRot', v[2], extra), hold('leftarmlower.xRot', v[3], extra)];
// keyed arm: ks = { x:[[t,v,e]..], y, z, l } for the right ('rightarm') or left arm
const armKeys = (side, ks, extra = {}) => [ks.x && set(side + 'arm.xRot', ks.x, extra), ks.y && set(side + 'arm.yRot', ks.y, extra), ks.z && set(side + 'arm.zRot', ks.z, extra), ks.l && set(side + 'armlower.xRot', ks.l, extra)];
// the same shape applied to several channels with a scale each: shape(t) in 0..1
const shaped = (list, keys, m = 'add') => list.map(([c, k]) => key(c, keys.map(([t, v, e = 1]) => [t, v * k, e]), m));

const flourish = (name, dur, tracks) => ({ name, dur, tracks: tracks.flat().filter(Boolean) });
const clip = (tracks) => ({ tracks: tracks.flat().filter(Boolean) });

module.exports = { E, osc, oscN, wosc, cst, key, set, loop, hold, sampled, pulses, armR, armL, armKeys, shaped, flourish, clip };
