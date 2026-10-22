// DEMON motion — smug, sultry, dangerous. Weight on one hip, hand on hip, tail with a mind of its own, wings that breathe and snap open.
// flourishes: 0 wing flare · 1 tail flick · 2 hair flip · 3 beckon · 4 cat stretch · 5 dramatic reveal · 6 impatient foot-tap · 7 levitate
const { E, osc, oscN, wosc, cst, key, set, loop, hold, sampled, pulses, armR, armL, armKeys, shaped, flourish, clip } = require('./dsl');

const PI2 = Math.PI * 2;
const bump = (a, b, c, d, v, e = E.sine) => [[0, 0, 0], [a, 0, 0], [b, v, e], [c, v, 0], [d, 0, e], [1, 0, 0]];
// her own (2026-10-21) bat wings copy the standard Gaia wings; the tail is the standard three-segment tail
const L = require('./legacy_rig');
const wingOpen = (k, ks) => L.wingOpen(ks, k);
const { wingLift, wingFan, flap, tailWave, tailRaise, noGlow } = L;
const wingElbow = () => [];

// ---------------------------------------------------------------- idle: hand on hip, hip cocked, chin up, wings breathing, tail swishing
const idle = clip([
  cst('bodybottom.zRot', 0.1), osc('bodybottom.yRot', -0.05, 180),
  cst('bodytop.zRot', -0.07), osc('bodytop.yRot', 0.09, 180),
  cst('head.zRot', -0.09), osc('head.zRot', 0.03, 125, 1), cst('head.xRot', -0.07), osc('head.yRot', 0.1, 180),
  cst('rightleg.xRot', -0.12), cst('rightleglower.xRot', 0.16),
  armL([0.25, 0, -0.95, -1.05]),
  ...flap(0.03, 90), ...tailWave(0.12, 55),
]);

// ---------------------------------------------------------------- walk: a runway strut (the body layer crosses her feet and swings her hips); wings trail, tail swishes in step
const walk = clip([
  cst('head.xRot', -0.06), ...wingOpen(1, [[0, 0.1, 1], [1, 0.1, 1]]), ...flap(0.05, 2, 0).map((t) => ({ ...t, tb: 1 })), ...tailWave(0.18, 1, 0).map((t) => ({ ...t, tb: 1 })),
]);
const run = clip([
  ...wingOpen(1, [[0, -0.35, 1], [1, -0.35, 1]]), cst('tail1.xRot', 0.25), ...flap(0.08, 1, 0).map((t) => ({ ...t, tb: 1 })),
]);

// ---------------------------------------------------------------- flourishes
const F = [];

// 0 — wing flare: the wings snap open with a flapping rumble, head thrown back in a laugh, fists down
F.push(flourish('flare', 84, [
  wingOpen(1, [[0, 0, 1], [0.14, -0.08, 1], [0.3, 0.7, E.back], [0.78, 0.7, 1], [1, 0, 1]]), wingLift([[0, 0, 1], [0.3, -0.28, 1], [0.78, -0.28, 1], [1, 0, 1]]),
  wingFan([[0, 1, 1], [0.14, 0.9, 1], [0.3, 1.14, E.back], [0.78, 1.14, 1], [1, 1, 1]]), flap(0.14, 9, 1),
  key('head.xRot', [[0, 0, 1], [0.14, 0.1, 1], [0.3, -0.35, 1], [0.78, -0.35, 1], [1, 0, 1]]), osc('head.zRot', 0.06, 6, 0, 1), osc('bodytop.xRot', 0.04, 6, 0, 1),
  key('bodytop.xRot', [[0, 0, 1], [0.3, -0.22, 1], [0.78, -0.22, 1], [1, 0, 1]]),
  armKeys('right', { x: [[0, 0.09], [0.3, 0.1], [0.78, 0.1], [1, 0.09]], z: [[0, 0.17], [0.3, 0.45], [0.78, 0.45], [1, 0.17]], l: [[0, 0], [0.3, -0.2], [0.78, -0.2], [1, 0]] }),
  armKeys('left', { x: [[0, 0.09], [0.3, 0.1], [0.78, 0.1], [1, 0.09]], z: [[0, -0.17], [0.3, -0.45], [0.78, -0.45], [1, -0.17]], l: [[0, 0], [0.3, -0.2], [0.78, -0.2], [1, 0]] }),
  tailRaise([[0, 0, 1], [0.3, -0.5, 1], [0.78, -0.5, 1], [1, 0, 1]]), key('root.y', [[0, 0, 1], [0.14, 0.5, 1], [0.3, -0.8, 1], [0.78, -0.8, 1], [1, 0, 1]]),
  key('fx.glow', [[0, 0, 1], [0.3, 1.2, 1], [0.78, 0.9, 1], [1, 0, 1]]), pulses('ev.embers', [0.3, 0.45, 0.6]), pulses('ev.ring', [0.3]),
]));

// 1 — tail flick: she glances back over her shoulder as the tail whips through a loop
F.push(flourish('tailflick', 72, [
  key('head.yRot', [[0, 0, 1], [0.2, -1.05, E.out], [0.8, -1.05, 1], [1, 0, 1]]), key('bodytop.yRot', [[0, 0, 1], [0.2, -0.35, 1], [0.8, -0.35, 1], [1, 0, 1]]), key('head.zRot', [[0, 0, 1], [0.3, 0.15, 1], [0.8, 0.15, 1], [1, 0, 1]]),
  ...tailWave(0.75, 12, 1), tailRaise([[0, 0, 1], [0.35, -0.35, 1], [0.7, -0.35, 1], [1, 0, 1]]), key('bodybottom.yRot', [[0, 0, 1], [0.2, -0.2, 1], [0.8, -0.2, 1], [1, 0, 1]]),
  ...wingOpen(1, [[0, 0, 1], [0.25, 0.25, 1], [0.8, 0.25, 1], [1, 0, 1]]), pulses('ev.embers', [0.45, 0.6]),
]));

// 2 — hair flip: a hand sweeps through the hair, hip popped, head thrown back
F.push(flourish('hairflip', 72, [
  armKeys('right', { x: [[0, 0.09], [0.25, -2.3, E.out], [0.5, -2.0], [0.7, -1.2], [1, 0.09]], y: [[0, 0], [0.25, 0.2], [0.5, 0.5], [0.7, 0.1], [1, 0]], z: [[0, 0.17], [0.25, 0.7], [0.5, 1.1], [0.7, 0.5], [1, 0.17]], l: [[0, 0], [0.25, -1.9], [0.5, -0.9], [0.7, -0.4], [1, 0]] }),
  key('head.zRot', [[0, 0, 1], [0.25, 0.3, 1], [0.5, 0.45, E.out], [0.72, 0.1, 1], [1, 0, 1]]), key('head.xRot', [[0, 0, 1], [0.25, -0.2, 1], [0.55, -0.35, 1], [0.8, -0.1, 1], [1, 0, 1]]), key('head.yRot', [[0, 0, 1], [0.25, 0.4, 1], [0.5, 0.6, 1], [0.8, 0, 1], [1, 0, 1]]),
  key('bodybottom.zRot', bump(0.15, 0.3, 0.7, 0.9, 0.2)), key('bodytop.zRot', bump(0.15, 0.3, 0.7, 0.9, -0.12)),
  osc('x_ttl0.zRot', 0.3, 9, 0, 1), osc('x_ttr0.zRot', -0.3, 9, 1, 1), ...flap(0.05, 24, 1),
  key('fx.glow', [[0, 0, 1], [0.5, 0.7, 1], [1, 0, 1]]), pulses('ev.embers', [0.5]),
]));

// 3 — beckon: a crooked finger and a smirk, leaning in
F.push(flourish('beckon', 64, [
  armKeys('right', { x: [[0, 0.09], [0.2, -1.45, E.out], [0.85, -1.45], [1, 0.09]], y: [[0, 0], [0.2, -0.3], [0.85, -0.3], [1, 0]], z: [[0, 0.17], [0.2, 0.1], [0.85, 0.1], [1, 0.17]], l: [[0, 0], [0.2, -1.0], [0.85, -1.0], [1, 0]] }),
  osc('rightarmlower.xRot', 0.4, 14, 0, 1), key('bodytop.xRot', [[0, 0, 1], [0.2, 0.18, 1], [0.85, 0.18, 1], [1, 0, 1]]),
  key('head.zRot', [[0, 0, 1], [0.2, 0.22, 1], [0.85, 0.22, 1], [1, 0, 1]]), key('head.xRot', [[0, 0, 1], [0.2, 0.08, 1], [0.85, 0.08, 1], [1, 0, 1]]), key('head.yRot', [[0, 0, 1], [0.25, 0.25, 1], [0.85, 0.25, 1], [1, 0, 1]]),
  key('bodybottom.zRot', bump(0.15, 0.3, 0.8, 0.95, 0.1)), ...wingOpen(1, [[0, 0, 1], [0.3, 0.12, 1], [0.85, 0.12, 1], [1, 0, 1]]), ...tailWave(0.3, 20, 1),
  key('fx.glow', [[0, 0, 1], [0.3, 0.6, 1], [0.8, 0.6, 1], [1, 0, 1]]), pulses('ev.heart', [0.35, 0.6]),
]));

// 4 — cat stretch: arms up and back, spine arched, wings and tail fully extended with a little shiver
F.push(flourish('stretch', 92, [
  armKeys('right', { x: [[0, 0.09], [0.28, -2.95, E.out], [0.7, -2.95], [1, 0.09]], y: [[0, 0], [0.28, 0.2], [0.7, 0.2], [1, 0]], z: [[0, 0.17], [0.28, -0.25], [0.7, -0.25], [1, 0.17]], l: [[0, 0], [0.28, -0.25], [0.7, -0.25], [1, 0]] }),
  armKeys('left', { x: [[0, 0.09], [0.28, -2.95, E.out], [0.7, -2.95], [1, 0.09]], y: [[0, 0], [0.28, -0.2], [0.7, -0.2], [1, 0]], z: [[0, -0.17], [0.28, 0.25], [0.7, 0.25], [1, -0.17]], l: [[0, 0], [0.28, -0.25], [0.7, -0.25], [1, 0]] }),
  key('bodytop.xRot', [[0, 0, 1], [0.28, -0.3, 1], [0.7, -0.3, 1], [1, 0, 1]]), key('bodybottom.xRot', [[0, 0, 1], [0.28, 0.2, 1], [0.7, 0.2, 1], [1, 0, 1]]), key('head.xRot', [[0, 0, 1], [0.28, -0.4, 1], [0.7, -0.4, 1], [1, 0, 1]]),
  key('leftleg.xRot', [[0, 0, 1], [0.28, 0.15, 1], [0.7, 0.15, 1], [1, 0, 1]]), key('rightleg.xRot', [[0, 0, 1], [0.28, 0.1, 1], [0.7, 0.1, 1], [1, 0, 1]]),
  wingOpen(1, [[0, 0, 1], [0.3, 0.9, E.out], [0.72, 0.9, 1], [1, 0, 1]]), wingLift([[0, 0, 1], [0.3, -0.2, 1], [0.72, -0.2, 1], [1, 0, 1]]), wingFan([[0, 1, 1], [0.3, 1.15, E.out], [0.72, 1.15, 1], [1, 1, 1]]), flap(0.05, 5, 1),
  tailRaise([[0, 0, 1], [0.3, -0.7, 1], [0.72, -0.7, 1], [1, 0, 1]]), key('root.y', [[0, 0, 1], [0.28, -0.4, 1], [0.7, -0.4, 1], [1, 0, 1]]),
  key('fx.glow', [[0, 0, 1], [0.3, 0.8, 1], [0.72, 0.8, 1], [1, 0, 1]]), pulses('ev.embers', [0.35]),
]));

// 5 — dramatic reveal: wings thrown wide, chest out, fists down, a ring of violet fire
F.push(flourish('reveal', 74, [
  wingOpen(1, [[0, 0, 1], [0.12, -0.15, 1], [0.26, 0.95, E.back], [0.8, 0.95, 1], [1, 0, 1]]), wingLift([[0, 0, 1], [0.26, -0.3, 1], [0.8, -0.3, 1], [1, 0, 1]]), wingFan([[0, 1, 1], [0.26, 1.16, E.back], [0.8, 1.16, 1], [1, 1, 1]]), flap(0.06, 8, 1),
  key('head.xRot', [[0, 0, 1], [0.12, 0.15, 1], [0.26, -0.4, 1], [0.8, -0.4, 1], [1, 0, 1]]), key('bodytop.xRot', [[0, 0, 1], [0.26, -0.3, E.back], [0.8, -0.3, 1], [1, 0, 1]]), key('bodybottom.xRot', [[0, 0, 1], [0.26, 0.12, 1], [0.8, 0.12, 1], [1, 0, 1]]),
  armKeys('right', { x: [[0, 0.09], [0.26, 0.15], [0.8, 0.15], [1, 0.09]], z: [[0, 0.17], [0.26, 0.55], [0.8, 0.55], [1, 0.17]], l: [[0, 0], [0.26, -0.15], [0.8, -0.15], [1, 0]] }),
  armKeys('left', { x: [[0, 0.09], [0.26, 0.15], [0.8, 0.15], [1, 0.09]], z: [[0, -0.17], [0.26, -0.55], [0.8, -0.55], [1, -0.17]], l: [[0, 0], [0.26, -0.15], [0.8, -0.15], [1, 0]] }),
  key('rightleg.zRot', [[0, 0, 1], [0.26, 0.12, 1], [0.8, 0.12, 1], [1, 0, 1]]), key('leftleg.zRot', [[0, 0, 1], [0.26, -0.12, 1], [0.8, -0.12, 1], [1, 0, 1]]),
  key('root.y', [[0, 0, 1], [0.12, 0.7, 1], [0.26, -1.2, E.back], [0.8, -1.2, 1], [1, 0, 1]]), tailRaise([[0, 0, 1], [0.26, -0.7, 1], [0.8, -0.7, 1], [1, 0, 1]]),
  key('fx.glow', [[0, 0, 1], [0.26, 1.6, E.back], [0.8, 1.0, 1], [1, 0, 1]]), pulses('ev.ring', [0.26]), pulses('ev.embers', [0.26, 0.4, 0.55]),
]));

// 6 — impatient: arms crossed, foot tapping, a long sideways glance and an eye roll
F.push(flourish('impatient', 84, [
  armKeys('right', { x: [[0, 0.09], [0.18, -0.65], [0.9, -0.65], [1, 0.09]], y: [[0, 0], [0.18, -0.9], [0.9, -0.9], [1, 0]], z: [[0, 0.17], [0.18, 0.0], [0.9, 0.0], [1, 0.17]], l: [[0, 0], [0.18, -1.25], [0.9, -1.25], [1, 0]] }),
  armKeys('left', { x: [[0, 0.09], [0.18, -0.65], [0.9, -0.65], [1, 0.09]], y: [[0, 0], [0.18, 0.9], [0.9, 0.9], [1, 0]], z: [[0, -0.17], [0.18, 0.0], [0.9, 0.0], [1, -0.17]], l: [[0, 0], [0.18, -1.25], [0.9, -1.25], [1, 0]] }),
  osc('rightleglower.xRot', 0.5, 14, 0, 1), osc('rightleg.xRot', -0.1, 14, 0.5, 1),
  key('head.yRot', [[0, 0, 1], [0.2, 0.55, 1], [0.5, 0.55, 1], [0.65, -0.5, 1], [0.9, -0.5, 1], [1, 0, 1]]), key('head.xRot', [[0, 0, 1], [0.5, -0.05, 1], [0.58, -0.3, 1], [0.7, 0.0, 1], [1, 0, 1]]), key('head.zRot', [[0, 0, 1], [0.2, 0.1, 1], [0.9, 0.1, 1], [1, 0, 1]]),
  ...tailWave(0.5, 14, 1), tailRaise([[0, 0, 1], [0.3, -0.25, 1], [0.9, -0.25, 1], [1, 0, 1]]), ...wingOpen(1, [[0, 0, 1], [0.3, -0.15, 1], [0.9, -0.15, 1], [1, 0, 1]]),
]));

// 7 — levitate: rises off the ground, wings beating, legs trailing, hair and skirt floating, embers drifting under her
F.push(flourish('levitate', 110, [
  key('root.y', [[0, 0, 1], [0.18, -6.5, E.out], [0.82, -6.5, 1], [1, 0, E.in]]), osc('root.y', -0.5, 34, 0, 1),
  ...flap(0.3, 17, 1), wingFan([[0, 1, 1], [0.18, 1.1, 1], [0.82, 1.1, 1], [1, 1, 1]]), wingLift([[0, 0, 1], [0.18, -0.2, 1], [0.82, -0.2, 1], [1, 0, 1]]), wingOpen(1, [[0, 0, 1], [0.18, 0.45, 1], [0.82, 0.45, 1], [1, 0, 1]]),
  key('rightleg.xRot', [[0, 0, 1], [0.2, 0.25, 1], [0.8, 0.25, 1], [1, 0, 1]]), key('leftleg.xRot', [[0, 0, 1], [0.2, -0.1, 1], [0.8, -0.1, 1], [1, 0, 1]]), key('rightleglower.xRot', [[0, 0, 1], [0.2, 0.6, 1], [0.8, 0.6, 1], [1, 0, 1]]), key('leftleglower.xRot', [[0, 0, 1], [0.2, 0.9, 1], [0.8, 0.9, 1], [1, 0, 1]]),
  armKeys('right', { x: [[0, 0.09], [0.2, -0.3], [0.8, -0.3], [1, 0.09]], z: [[0, 0.17], [0.2, 0.75], [0.8, 0.75], [1, 0.17]], l: [[0, 0], [0.2, -0.3], [0.8, -0.3], [1, 0]] }),
  armKeys('left', { x: [[0, 0.09], [0.2, -0.3], [0.8, -0.3], [1, 0.09]], z: [[0, -0.17], [0.2, -0.75], [0.8, -0.75], [1, -0.17]], l: [[0, 0], [0.2, -0.3], [0.8, -0.3], [1, 0]] }),
  key('head.xRot', [[0, 0, 1], [0.2, -0.25, 1], [0.8, -0.25, 1], [1, 0, 1]]), key('bodytop.xRot', [[0, 0, 1], [0.2, -0.12, 1], [0.8, -0.12, 1], [1, 0, 1]]),
  ...tailWave(0.35, 24, 1), key('fx.glow', [[0, 0, 1], [0.18, 1.0, 1], [0.82, 1.0, 1], [1, 0, 1]]), pulses('ev.embers', [0.1, 0.25, 0.35, 0.45, 0.55, 0.65, 0.75, 0.88]), pulses('ev.ring', [0.18, 0.84]),
]));

module.exports = { motion: noGlow({ idle, walk, run, flourish: F }) };
