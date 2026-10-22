// ANGEL motion — serene and radiant. She hovers a breath above the floor, hands in prayer, halo turning, wings stirring; she glides rather than walks.
// flourishes: 0 blessing · 1 prayer bow · 2 gaze to the heavens · 3 wing wrap · 4 ascension · 5 curtsy · 6 cross clasp · 7 graceful spin
const { E, osc, oscN, wosc, cst, key, set, loop, hold, sampled, pulses, armR, armL, armKeys, shaped, flourish, clip } = require('./dsl');

const PI2 = Math.PI * 2;
const bump = (a, b, c, d, v, e = E.sine) => [[0, 0, 0], [a, 0, 0], [b, v, e], [c, v, 0], [d, 0, e], [1, 0, 0]];
const WL = 'x_awl', WR = 'x_awr';
const wingOpen = (ks) => [key(WL + 'a0.yRot', ks), key(WR + 'a0.yRot', ks.map(([t, v, e]) => [t, -v, e]))];                                        // + = sweep out from the back / forward
const wingLift = (ks) => [key(WL + 'a0.zRot', ks.map(([t, v, e]) => [t, -v, e])), key(WR + 'a0.zRot', ks)];                                         // + = raise
const wingElbow = (ks) => [key(WL + 'a1.zRot', ks.map(([t, v, e]) => [t, -v, e])), key(WR + 'a1.zRot', ks)];
const wingFan = (ks) => { const r = ks.map(([t, v, e]) => [t, v - 1, e]); return [key('spread.' + WL + 'fan', r), key('spread.' + WR + 'fan', r)]; };   // absolute spread values (1 = as built)
const flap = (amp, period, env = 0, ph = 0) => [osc(WL + 'a0.zRot', -amp, period, ph, env), osc(WR + 'a0.zRot', amp, period, ph, env), osc(WL + 'a1.zRot', -amp * 0.7, period, ph + 0.7, env), osc(WR + 'a1.zRot', amp * 0.7, period, ph + 0.7, env)];
const halo = (spin, grow, bob = 0) => [key('x_halo.yRot', [[0, 0, 1], [1, spin, 1]]), key('x_halo.sc', [[0, 0, 1], [0.2, grow, 1], [0.8, grow, 1], [1, 0, 1]]), bob ? osc('x_halo.y', bob, 20, 0, 1) : null];
const PRAYER_R = [0.1, -0.55, 0, -1.45], PRAYER_L = [0.1, 0.55, 0, -1.45];   // hands clasped at the belly

// ---------------------------------------------------------------- idle: hovering, hands together, halo turning
const idle = clip([
  osc('fx.glow', 0.12, 90),
  armR(PRAYER_R), armL(PRAYER_L),
  cst('head.xRot', 0.1), osc('head.yRot', 0.12, 190, 0.5), osc('head.zRot', 0.03, 140),
  osc('bodytop.yRot', 0.07, 175), cst('bodytop.xRot', 0.03),
  cst('root.y', -0.5), osc('root.y', -0.3, 66),
  loop('x_halo.yRot', [[0, 0, 0], [1, PI2, 0]], 260), osc('x_halo.y', 0.4, 64), osc('x_halo.xRot', 0.03, 90),
  osc('spread.' + WL + 'fan', 0.05, 100), osc('spread.' + WR + 'fan', 0.05, 100, 0.5), ...flap(0.04, 100),
]);

// ---------------------------------------------------------------- walk: she glides (the base bounce is cancelled) with fluttering wings and a drifting halo
const walk = clip([
  wosc('head.zRot', 0.03, 1, 1),
  ...flap(0.06, 2).map((t) => ({ ...t, tb: 1 })), wosc('x_halo.zRot', 0.05, 1),
]);
const run = clip([
  ...[WL, WR].map((w, i) => cst(w + 'a0.yRot', (i ? -1 : 1) * -0.35)), ...wingFan([[0, 0.8, 1], [1, 0.8, 1]]), cst('x_halo.xRot', -0.18), cst('bodytop.xRot', 0.06), ...flap(0.1, 1).map((t) => ({ ...t, tb: 1 })),
]);

// ---------------------------------------------------------------- flourishes
const F = [];

// 0 — blessing: a step forward, right hand raised, left over her heart; the halo flares and golden light drifts out
F.push(flourish('bless', 88, [
  armKeys('right', { x: [[0, 0.09], [0.25, -1.75, E.out], [0.8, -1.75], [1, 0.09]], y: [[0, 0], [0.25, -0.2], [0.8, -0.2], [1, 0]], z: [[0, 0.17], [0.25, 0.35], [0.8, 0.35], [1, 0.17]], l: [[0, 0], [0.25, -0.6], [0.8, -0.6], [1, 0]] }),
  armKeys('left', { x: [[0, 0.09], [0.25, -0.95], [0.8, -0.95], [1, 0.09]], y: [[0, 0], [0.25, 0.5], [0.8, 0.5], [1, 0]], z: [[0, -0.17], [0.25, 0.0], [0.8, 0.0], [1, -0.17]], l: [[0, 0], [0.25, -1.35], [0.8, -1.35], [1, 0]] }),
  key('leftleg.xRot', bump(0.15, 0.3, 0.8, 0.95, -0.3)), key('leftleglower.xRot', bump(0.15, 0.3, 0.8, 0.95, 0.2)), key('rightleg.xRot', bump(0.15, 0.3, 0.8, 0.95, 0.18)), key('rightleglower.xRot', bump(0.15, 0.3, 0.8, 0.95, 0.3)),
  key('bodybottom.yRot', bump(0.15, 0.3, 0.8, 0.95, 0.1)), key('bodytop.yRot', bump(0.15, 0.3, 0.8, 0.95, -0.1)), key('head.xRot', [[0, 0, 1], [0.25, -0.15, 1], [0.8, -0.15, 1], [1, 0, 1]]), key('root.y', bump(0.15, 0.3, 0.8, 0.95, -0.6)),
  wingLift([[0, 0, 1], [0.3, 0.2, 1], [0.8, 0.2, 1], [1, 0, 1]]), wingFan([[0, 1, 1], [0.3, 1.22, 1], [0.8, 1.22, 1], [1, 1, 1]]), halo(PI2 * 2, 0.3, 0),
  key('fx.glow', [[0, 0, 1], [0.3, 1.2, 1], [0.8, 0.9, 1], [1, 0, 1]]), pulses('ev.rays', [0.3]), pulses('ev.sparkle', [0.32, 0.45, 0.58, 0.7]),
]));

// 1 — prayer bow: hips back, knees soft, the spine folds forward, veil and hair falling forward
F.push(flourish('prayerbow', 88, [
  armKeys('right', { x: [[0, 0.09], [0.2, 0.1], [0.85, 0.1], [1, 0.09]], y: [[0, 0], [0.2, -0.55], [0.85, -0.55], [1, 0]], z: [[0, 0.17], [0.2, 0.0], [0.85, 0.0], [1, 0.17]], l: [[0, 0], [0.2, -1.45], [0.85, -1.45], [1, 0]] }),
  armKeys('left', { x: [[0, 0.09], [0.2, 0.1], [0.85, 0.1], [1, 0.09]], y: [[0, 0], [0.2, 0.55], [0.85, 0.55], [1, 0]], z: [[0, -0.17], [0.2, 0.0], [0.85, 0.0], [1, -0.17]], l: [[0, 0], [0.2, -1.45], [0.85, -1.45], [1, 0]] }),
  key('bodytop.xRot', [[0, 0, 1], [0.15, -0.05, 1], [0.45, 0.62, E.out], [0.72, 0.62, 1], [0.92, 0, 1], [1, 0, 1]]), key('bodybottom.xRot', [[0, 0, 1], [0.45, -0.22, 1], [0.72, -0.22, 1], [0.92, 0, 1], [1, 0, 1]]),
  key('head.xRot', [[0, 0, 1], [0.45, 0.3, 1], [0.72, 0.3, 1], [0.92, 0, 1], [1, 0, 1]]), key('root.y', [[0, 0, 1], [0.45, 1.2, 1], [0.72, 1.2, 1], [0.92, 0, 1], [1, 0, 1]]),
  key('rightleg.xRot', bump(0.15, 0.45, 0.72, 0.92, -0.22)), key('leftleg.xRot', bump(0.15, 0.45, 0.72, 0.92, -0.22)), key('rightleglower.xRot', bump(0.15, 0.45, 0.72, 0.92, 0.42)), key('leftleglower.xRot', bump(0.15, 0.45, 0.72, 0.92, 0.42)),
  wingLift([[0, 0, 1], [0.45, -0.28, 1], [0.72, -0.28, 1], [1, 0, 1]]), wingFan([[0, 1, 1], [0.45, 0.8, 1], [0.72, 0.8, 1], [1, 1, 1]]), halo(PI2, 0.1, 0),
  key('fx.glow', [[0, 0, 1], [0.5, 0.7, 1], [1, 0, 1]]), pulses('ev.sparkle', [0.5, 0.62, 0.74]),
]));

// 2 — gaze to the heavens: arms open, chin up, wings rising and fanning, the halo spinning faster
F.push(flourish('gaze', 96, [
  armKeys('right', { x: [[0, 0.09], [0.28, -0.5, E.out], [0.8, -0.5], [1, 0.09]], z: [[0, 0.17], [0.28, 0.75], [0.8, 0.75], [1, 0.17]], l: [[0, 0], [0.28, -0.3], [0.8, -0.3], [1, 0]] }),
  armKeys('left', { x: [[0, 0.09], [0.28, -0.5, E.out], [0.8, -0.5], [1, 0.09]], z: [[0, -0.17], [0.28, -0.75], [0.8, -0.75], [1, -0.17]], l: [[0, 0], [0.28, -0.3], [0.8, -0.3], [1, 0]] }),
  key('head.xRot', [[0, 0, 1], [0.28, -0.5, 1], [0.8, -0.5, 1], [1, 0, 1]]), key('bodytop.xRot', [[0, 0, 1], [0.28, -0.24, 1], [0.8, -0.24, 1], [1, 0, 1]]), key('bodybottom.xRot', [[0, 0, 1], [0.28, 0.1, 1], [0.8, 0.1, 1], [1, 0, 1]]),
  key('root.y', [[0, 0, 1], [0.28, -1.8, E.out], [0.8, -1.8, 1], [1, 0, 1]]), key('rightleg.xRot', [[0, 0, 1], [0.28, 0.1, 1], [0.8, 0.1, 1], [1, 0, 1]]), key('leftleg.xRot', [[0, 0, 1], [0.28, 0.1, 1], [0.8, 0.1, 1], [1, 0, 1]]),
  wingOpen([[0, 0, 1], [0.3, 0.45, E.out], [0.8, 0.45, 1], [1, 0, 1]]), wingLift([[0, 0, 1], [0.3, 0.25, 1], [0.8, 0.25, 1], [1, 0, 1]]), wingFan([[0, 1, 1], [0.3, 1.4, E.out], [0.8, 1.4, 1], [1, 1, 1]]), flap(0.05, 18, 1),
  halo(PI2 * 3, 0.35, 0.4), key('fx.glow', [[0, 0, 1], [0.35, 1.4, 1], [0.8, 1.1, 1], [1, 0, 1]]), pulses('ev.rays', [0.35]), pulses('ev.feather', [0.4, 0.5, 0.6, 0.7]),
]));

// 3 — wing wrap: the wings sweep forward and fold around her like a cocoon, then open again in a shower of feathers
F.push(flourish('wrap', 100, [
  wingOpen([[0, 0, 1], [0.35, 1.45, E.out], [0.6, 1.45, 1], [0.85, 0, E.out], [1, 0, 1]]), wingLift([[0, 0, 1], [0.35, -0.2, 1], [0.6, -0.2, 1], [0.85, 0, 1], [1, 0, 1]]),
  wingFan([[0, 1, 1], [0.35, 0.6, 1], [0.6, 0.6, 1], [0.8, 1.45, E.back], [0.9, 1.2, 1], [1, 1, 1]]),
  armKeys('right', { x: [[0, 0.09], [0.3, -0.8], [0.62, -0.8], [0.85, 0.09], [1, 0.09]], y: [[0, 0], [0.3, -0.5], [0.62, -0.5], [0.85, 0], [1, 0]], z: [[0, 0.17], [0.3, 0.0], [0.62, 0.0], [0.85, 0.17], [1, 0.17]], l: [[0, 0], [0.3, -1.4], [0.62, -1.4], [0.85, 0], [1, 0]] }),
  armKeys('left', { x: [[0, 0.09], [0.3, -0.8], [0.62, -0.8], [0.85, 0.09], [1, 0.09]], y: [[0, 0], [0.3, 0.5], [0.62, 0.5], [0.85, 0], [1, 0]], z: [[0, -0.17], [0.3, 0.0], [0.62, 0.0], [0.85, -0.17], [1, -0.17]], l: [[0, 0], [0.3, -1.4], [0.62, -1.4], [0.85, 0], [1, 0]] }),
  key('head.xRot', [[0, 0, 1], [0.35, 0.28, 1], [0.6, 0.28, 1], [0.85, -0.25, 1], [1, 0, 1]]), key('bodytop.xRot', [[0, 0, 1], [0.35, 0.1, 1], [0.6, 0.1, 1], [0.85, -0.18, 1], [1, 0, 1]]),
  key('fx.glow', [[0, 0, 1], [0.35, 0.5, 1], [0.6, 0.6, 1], [0.82, 1.5, 1], [1, 0, 1]]), pulses('ev.feather', [0.82, 0.86, 0.9, 0.94]), pulses('ev.rays', [0.82]), halo(PI2, 0.2, 0),
]));

// 4 — ascension: she rises, arms wide, wings beating slowly, halo blazing
F.push(flourish('ascend', 124, [
  key('root.y', [[0, 0, 1], [0.2, -6.0, E.out], [0.8, -6.0, 1], [1, 0, E.in]]), osc('root.y', -0.45, 40, 0, 1),
  armKeys('right', { x: [[0, 0.09], [0.22, -0.35], [0.8, -0.35], [1, 0.09]], z: [[0, 0.17], [0.22, 0.95], [0.8, 0.95], [1, 0.17]], l: [[0, 0], [0.22, -0.2], [0.8, -0.2], [1, 0]] }),
  armKeys('left', { x: [[0, 0.09], [0.22, -0.35], [0.8, -0.35], [1, 0.09]], z: [[0, -0.17], [0.22, -0.95], [0.8, -0.95], [1, -0.17]], l: [[0, 0], [0.22, -0.2], [0.8, -0.2], [1, 0]] }),
  key('head.xRot', [[0, 0, 1], [0.22, -0.4, 1], [0.8, -0.4, 1], [1, 0, 1]]), key('bodytop.xRot', [[0, 0, 1], [0.22, -0.15, 1], [0.8, -0.15, 1], [1, 0, 1]]),
  key('rightleg.xRot', [[0, 0, 1], [0.2, 0.18, 1], [0.8, 0.18, 1], [1, 0, 1]]), key('leftleg.xRot', [[0, 0, 1], [0.2, 0.08, 1], [0.8, 0.08, 1], [1, 0, 1]]), key('rightleglower.xRot', [[0, 0, 1], [0.2, 0.3, 1], [0.8, 0.3, 1], [1, 0, 1]]), key('leftleglower.xRot', [[0, 0, 1], [0.2, 0.2, 1], [0.8, 0.2, 1], [1, 0, 1]]),
  wingOpen([[0, 0, 1], [0.25, 0.55, 1], [0.8, 0.55, 1], [1, 0, 1]]), wingLift([[0, 0, 1], [0.25, 0.2, 1], [0.8, 0.2, 1], [1, 0, 1]]), wingFan([[0, 1, 1], [0.25, 1.5, 1], [0.8, 1.5, 1], [1, 1, 1]]), flap(0.2, 40, 1),
  halo(PI2 * 4, 0.55, 0.5), key('fx.glow', [[0, 0, 1], [0.25, 1.6, 1], [0.8, 1.6, 1], [1, 0, 1]]), pulses('ev.rays', [0.2, 0.5, 0.8]), pulses('ev.feather', [0.25, 0.32, 0.4, 0.48, 0.56, 0.64, 0.72]), pulses('ev.sparkle', [0.3, 0.45, 0.6, 0.75]),
]));

// 5 — curtsy: one foot back, knees bent, hands lifting the hem, head bowed
F.push(flourish('curtsy', 72, [
  key('root.y', [[0, 0, 1], [0.35, 1.8, 1], [0.7, 1.8, 1], [1, 0, 1]]), key('rightleg.xRot', bump(0.1, 0.35, 0.7, 0.95, 0.5)), key('rightleglower.xRot', bump(0.1, 0.35, 0.7, 0.95, 0.6)), key('leftleg.xRot', bump(0.1, 0.35, 0.7, 0.95, -0.35)), key('leftleglower.xRot', bump(0.1, 0.35, 0.7, 0.95, 0.7)),
  armKeys('right', { x: [[0, 0.09], [0.3, -0.25], [0.7, -0.25], [1, 0.09]], z: [[0, 0.17], [0.3, 0.55], [0.7, 0.55], [1, 0.17]], l: [[0, 0], [0.3, -0.4], [0.7, -0.4], [1, 0]] }),
  armKeys('left', { x: [[0, 0.09], [0.3, -0.25], [0.7, -0.25], [1, 0.09]], z: [[0, -0.17], [0.3, -0.55], [0.7, -0.55], [1, -0.17]], l: [[0, 0], [0.3, -0.4], [0.7, -0.4], [1, 0]] }),
  key('head.xRot', [[0, 0, 1], [0.35, 0.28, 1], [0.7, 0.28, 1], [1, 0, 1]]), key('bodytop.xRot', [[0, 0, 1], [0.35, 0.18, 1], [0.7, 0.18, 1], [1, 0, 1]]), wingLift([[0, 0, 1], [0.4, -0.15, 1], [0.7, -0.15, 1], [1, 0, 1]]),
  key('fx.glow', [[0, 0, 1], [0.4, 0.5, 1], [1, 0, 1]]), pulses('ev.sparkle', [0.4, 0.5]),
]));

// 6 — cross clasp: both hands on the cross, head bowed, a slow sway; the cross glows
F.push(flourish('clasp', 88, [
  armKeys('right', { x: [[0, 0.09], [0.2, -0.45], [0.85, -0.45], [1, 0.09]], y: [[0, 0], [0.2, -0.5], [0.85, -0.5], [1, 0]], z: [[0, 0.17], [0.2, 0.0], [0.85, 0.0], [1, 0.17]], l: [[0, 0], [0.2, -1.5], [0.85, -1.5], [1, 0]] }),
  armKeys('left', { x: [[0, 0.09], [0.2, -0.45], [0.85, -0.45], [1, 0.09]], y: [[0, 0], [0.2, 0.5], [0.85, 0.5], [1, 0]], z: [[0, -0.17], [0.2, 0.0], [0.85, 0.0], [1, -0.17]], l: [[0, 0], [0.2, -1.5], [0.85, -1.5], [1, 0]] }),
  key('head.xRot', [[0, 0, 1], [0.2, 0.3, 1], [0.85, 0.3, 1], [1, 0, 1]]), osc('bodytop.zRot', 0.04, 70, 0, 1), key('bodytop.xRot', [[0, 0, 1], [0.2, 0.08, 1], [0.85, 0.08, 1], [1, 0, 1]]),
  key('x_cross.sc', [[0, 0, 1], [0.3, 0.5, 1], [0.6, 0.15, 1], [0.8, 0.5, 1], [1, 0, 1]]), wingLift([[0, 0, 1], [0.3, -0.12, 1], [0.85, -0.12, 1], [1, 0, 1]]),
  key('fx.glow', [[0, 0, 1], [0.3, 1.1, 1], [0.8, 1.1, 1], [1, 0, 1]]), pulses('ev.sparkle', [0.3, 0.5, 0.7]),
]));

// 7 — graceful spin: a slow turn with arms out and wings spread, feathers flying off
F.push(flourish('spin', 96, [
  key('root.yRot', [[0, 0, 1], [0.15, 0, 1], [0.85, PI2, 1], [1, PI2, 1]]), key('root.y', [[0, 0, 1], [0.2, -1.8, 1], [0.8, -1.8, 1], [1, 0, 1]]),
  armKeys('right', { x: [[0, 0.09], [0.22, -0.3], [0.8, -0.3], [1, 0.09]], z: [[0, 0.17], [0.22, 1.2], [0.8, 1.2], [1, 0.17]], l: [[0, 0], [0.22, -0.25], [0.8, -0.25], [1, 0]] }),
  armKeys('left', { x: [[0, 0.09], [0.22, -0.3], [0.8, -0.3], [1, 0.09]], z: [[0, -0.17], [0.22, -1.2], [0.8, -1.2], [1, -0.17]], l: [[0, 0], [0.22, -0.25], [0.8, -0.25], [1, 0]] }),
  key('head.xRot', [[0, 0, 1], [0.22, -0.2, 1], [0.8, -0.2, 1], [1, 0, 1]]), key('bodytop.zRot', [[0, 0, 1], [0.5, 0.08, 1], [1, 0, 1]]),
  wingOpen([[0, 0, 1], [0.25, 0.4, 1], [0.8, 0.4, 1], [1, 0, 1]]), wingFan([[0, 1, 1], [0.25, 1.35, 1], [0.8, 1.35, 1], [1, 1, 1]]), flap(0.06, 12, 1), halo(PI2 * 2, 0.3, 0),
  key('fx.glow', [[0, 0, 1], [0.25, 1.2, 1], [0.8, 1.2, 1], [1, 0, 1]]), pulses('ev.feather', [0.25, 0.32, 0.4, 0.47, 0.55, 0.62, 0.7, 0.77]), pulses('ev.sparkle', [0.3, 0.5, 0.7]),
]));

module.exports = { motion: { idle, walk, run, flourish: F } };
