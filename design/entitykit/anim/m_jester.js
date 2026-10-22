// JESTER motion — a bouncy, showy trickster who never quite stands still.
// idle: a springy two-hop loop (alternating tucked legs, arms flung up, bells jingling through the springs)
// flourishes: 0 grand bow · 1 giggle · 2 twirl · 3 backflip · 4 juggle · 5 dance · 6 hat tip · 7 ta-da leap
const { noGlow } = require('./legacy_rig');
const { E, osc, oscN, wosc, cst, key, set, loop, hold, sampled, pulses, armR, armL, armKeys, shaped, flourish, clip } = require('./dsl');

const PI2 = Math.PI * 2;
const bump = (a, b, c, d, v, e = E.sine) => [[0, 0, 0], [a, 0, 0], [b, v, e], [c, v, 0], [d, 0, e], [1, 0, 0]];
const smooth = (x) => { const t = Math.max(0, Math.min(1, x)); return t * t * (3 - 2 * t); };

// ---------------------------------------------------------------- idle: two hops per 60-tick cycle; hop 0 tucks the left leg, hop 1 the right
function hopKeys(shape, k) { return shape.map(([t, v, e = E.sine]) => [0.5 * k + 0.5 * t, v, e]); }
function hopTrack(c, k0, k1, shape0, shape1) {
  // shape arrays are in hop-local time 0..1 (one hop = half the cycle)
  const a = hopKeys(shape0, 0), b = hopKeys(shape1 || shape0, 1);
  const ks = [[0, 0, 0], ...a, ...b, [1, 0, 0]];
  const clean = []; for (const kk of ks) { if (!clean.length || kk[0] > clean[clean.length - 1][0] + 1e-6) clean.push(kk); }
  return loop(c, clean, 60);
}
const HOP_Y = [[0.14, 0.9], [0.4, -2.6, E.out], [0.64, -0.1, E.in], [0.76, 0.7, E.out], [0.92, 0, E.sine]];
const TUCK = (v) => [[0.14, v * 0.3], [0.4, v, E.out], [0.64, v * 0.15, E.in], [0.8, v * 0.2], [0.95, 0]];
const NONE = [[0.5, 0]];
const idle = clip([
  osc('fx.glow', 0.12, 60),
  hopTrack('root.y', 0, 1, HOP_Y),
  hopTrack('leftleg.xRot', 0, 1, TUCK(-0.85), TUCK(-0.2)), hopTrack('leftleglower.xRot', 0, 1, TUCK(1.5), TUCK(0.3)),
  hopTrack('rightleg.xRot', 0, 1, TUCK(-0.2), TUCK(-0.85)), hopTrack('rightleglower.xRot', 0, 1, TUCK(0.3), TUCK(1.5)),
  hopTrack('rightarm.zRot', 0, 1, [[0.14, -0.08], [0.4, 0.95, E.out], [0.64, 0.25], [0.95, 0]]),
  hopTrack('leftarm.zRot', 0, 1, [[0.14, 0.08], [0.4, -0.95, E.out], [0.64, -0.25], [0.95, 0]]),
  hopTrack('rightarm.xRot', 0, 1, [[0.14, 0.2], [0.4, -0.35], [0.7, 0.1], [0.95, 0]]), hopTrack('leftarm.xRot', 0, 1, [[0.14, 0.2], [0.4, -0.35], [0.7, 0.1], [0.95, 0]]),
  hopTrack('rightarmlower.xRot', 0, 1, [[0.4, -0.5], [0.8, 0]]), hopTrack('leftarmlower.xRot', 0, 1, [[0.4, -0.5], [0.8, 0]]),
  hopTrack('bodytop.xRot', 0, 1, [[0.14, 0.2], [0.4, -0.1], [0.7, 0.12], [0.95, 0]]),
  hopTrack('head.xRot', 0, 1, [[0.14, 0.1], [0.4, -0.12], [0.7, 0.05], [0.95, 0]]),
  loop('bodybottom.zRot', [[0, 0, 1], [0.2, -0.07, 1], [0.5, 0, 1], [0.7, 0.07, 1], [1, 0, 1]], 60),
  loop('bodytop.yRot', [[0, 0, 1], [0.25, -0.18, 1], [0.5, 0, 1], [0.75, 0.18, 1], [1, 0, 1]], 60),
  osc('head.zRot', 0.08, 53, 1.0), osc('head.yRot', 0.12, 97),
]);

// ---------------------------------------------------------------- walk and run: springy bounce, swaying hips, bobbing head, arms flared out
const walk = clip([
  wosc('head.zRot', 0.06, 2, 0.4), wosc('head.xRot', 0.04, 2),
  cst('rightarm.zRot', 0.12), cst('leftarm.zRot', -0.12), wosc('rightarmlower.xRot', -0.12, 1, 1.2), wosc('leftarmlower.xRot', -0.12, 1, 4.3),
]);
const run = clip([
  wosc('head.zRot', 0.08, 2, 0.4), cst('rightarm.zRot', 0.28), cst('leftarm.zRot', -0.28),
]);

// ---------------------------------------------------------------- flourishes
const F = [];

// 0 — grand bow with a flourish of the hat and a burst of confetti
{
  const B = [[0, 0, 1], [0.15, -0.1, 1], [0.4, 0.9, E.out], [0.7, 0.9, 1], [0.9, -0.06, 1], [1, 0, 1]];
  F.push(flourish('bow', 80, [
    shaped([['bodytop.xRot', 1], ['bodybottom.xRot', -0.18], ['head.xRot', 0.25]], B),
    key('root.y', [[0, 0, 1], [0.15, -0.2, 1], [0.4, 1.0, 1], [0.7, 1.0, 1], [0.9, 0, 1], [1, 0, 1]]),
    key('rightleg.xRot', bump(0.15, 0.4, 0.7, 0.92, 0.55)), key('rightleglower.xRot', bump(0.15, 0.4, 0.7, 0.92, 0.8)),
    key('leftleg.xRot', bump(0.15, 0.4, 0.7, 0.92, -0.5)), key('leftleglower.xRot', bump(0.15, 0.4, 0.7, 0.92, 0.9)),
    armKeys('right', { x: [[0, 0.09], [0.12, -0.2], [0.4, -0.5], [0.7, -0.5], [0.92, 0.09], [1, 0.09]], z: [[0, 0.17], [0.15, 1.7, E.out], [0.42, 1.25], [0.7, 1.25], [0.92, 0.17], [1, 0.17]],
      l: [[0, 0], [0.2, -0.4], [0.4, -0.2], [0.7, -0.2], [0.9, 0], [1, 0]] }),
    armKeys('left', { x: [[0, 0.09], [0.4, 0.55], [0.7, 0.55], [0.92, 0.09], [1, 0.09]], z: [[0, -0.17], [0.4, -0.1], [0.7, -0.1], [0.92, -0.17], [1, -0.17]], l: [[0, 0], [0.4, -1.3], [0.7, -1.3], [0.92, 0], [1, 0]] }),
    osc('x_hatbell.zRot', 0.25, 11, 0, 1), osc('x_hatbbell.zRot', 0.25, 13, 1, 1),
    key('fx.glow', [[0, 0, 1], [0.43, 0.9, 1], [0.7, 0, 1], [1, 0, 1]]), pulses('ev.confetti', [0.43]), pulses('ev.bells', [0.2, 0.45, 0.75]),
  ]));
}

// 1 — giggle: shoulders shake, hand over the mouth, knees bouncing, notes floating up
F.push(flourish('giggle', 70, [
  osc('root.y', -0.55, 7, 0, 1), osc('bodytop.zRot', 0.08, 9, 0, 1), osc('head.zRot', 0.22, 7, 1, 1), osc('rightleglower.xRot', 0.25, 7, 0, 1), osc('leftleglower.xRot', 0.25, 7, 3, 1),
  key('bodytop.xRot', [[0, 0, 1], [0.15, 0.15, 1], [0.85, 0.15, 1], [1, 0, 1]]), key('head.yRot', [[0, 0, 1], [0.3, 0.25, 1], [0.7, -0.2, 1], [1, 0, 1]]),
  armKeys('left', { x: [[0, 0.09], [0.2, -1.95], [0.85, -1.95], [1, 0.09]], y: [[0, 0], [0.2, 0.4], [0.85, 0.4], [1, 0]], z: [[0, -0.17], [0.2, 0.1], [0.85, 0.1], [1, -0.17]], l: [[0, 0], [0.2, -1.55], [0.85, -1.55], [1, 0]] }),
  armKeys('right', { x: [[0, 0.09], [0.2, -0.45], [0.85, -0.45], [1, 0.09]], z: [[0, 0.17], [0.2, 0.3], [0.85, 0.3], [1, 0.17]], l: [[0, 0], [0.2, -1.0], [0.85, -1.0], [1, 0]] }),
  osc('x_hatbell.zRot', 0.2, 8, 0, 1), osc('x_hatbbell.zRot', 0.2, 9, 2, 1), pulses('ev.note', [0.2, 0.33, 0.46, 0.59, 0.72]),
]));

// 2 — twirl: a hopping pirouette, arms wide, skirt flaring out and the cap whipping around
F.push(flourish('twirl', 64, [
  key('root.yRot', [[0, 0, 1], [0.14, 0, 1], [0.86, PI2, 1], [1, PI2, 1]]),
  key('root.y', [[0, 0, 1], [0.14, 0.9, 1], [0.38, -3.2, E.out], [0.62, -3.2, 1], [0.86, 0.5, E.in], [0.95, 0.9, 1], [1, 0, 1]]),
  key('rightleg.xRot', bump(0.14, 0.38, 0.62, 0.86, -0.6)), key('rightleglower.xRot', bump(0.14, 0.38, 0.62, 0.86, 1.1)), key('leftleg.xRot', bump(0.14, 0.38, 0.62, 0.86, -0.3)), key('leftleglower.xRot', bump(0.14, 0.38, 0.62, 0.86, 0.6)),
  armKeys('right', { x: [[0, 0.09], [0.25, -0.2], [0.75, -0.2], [1, 0.09]], z: [[0, 0.17], [0.25, 1.45], [0.75, 1.45], [1, 0.17]], l: [[0, 0], [0.25, -0.15], [0.75, -0.15], [1, 0]] }),
  armKeys('left', { x: [[0, 0.09], [0.25, -0.2], [0.75, -0.2], [1, 0.09]], z: [[0, -0.17], [0.25, -1.45], [0.75, -1.45], [1, -0.17]], l: [[0, 0], [0.25, -0.15], [0.75, -0.15], [1, 0]] }),
  key('bodytop.zRot', [[0, 0, 1], [0.4, 0.1, 1], [0.7, -0.1, 1], [1, 0, 1]]), key('head.xRot', [[0, 0, 1], [0.4, -0.15, 1], [0.9, -0.05, 1], [1, 0, 1]]),
  key('fx.glow', [[0, 0, 1], [0.4, 0.6, 1], [0.86, 0.9, 1], [1, 0, 1]]), pulses('ev.bells', [0.4, 0.6]), pulses('ev.confetti', [0.88]),
]));

// 3 — backflip: crouch, leap, a full tucked rotation about her centre, landing with arms flung wide
{
  const C = 16;                                         // body centre above the feet, model units
  const TH = (t) => -PI2 * smooth((t - 0.26) / 0.42);   // root.xRot: negative = backwards
  const JY = (t) => { const u = (t - 0.2) / 0.52; return u > 0 && u < 1 ? -7 * 4 * u * (1 - u) : 0; };
  const LAND = (t) => (t > 0.72 ? 1.4 * Math.sin(Math.PI * Math.min(1, (t - 0.72) / 0.2)) : 0);
  const CROUCH = (t) => (t < 0.2 ? 1.3 * Math.sin(Math.PI * 0.5 * Math.min(1, t / 0.18)) * (t < 0.18 ? 1 : (0.2 - t) / 0.02) : 0);
  F.push(flourish('backflip', 84, [
    sampled('root.xRot', TH, 36),
    sampled('root.y', (t) => JY(t) + LAND(t) + CROUCH(t) + (-C + C * Math.cos(TH(t))), 36),
    sampled('root.z', (t) => C * Math.sin(TH(t)), 36),
    key('rightleg.xRot', bump(0.2, 0.34, 0.56, 0.7, -1.6)), key('leftleg.xRot', bump(0.2, 0.34, 0.56, 0.7, -1.6)), key('rightleglower.xRot', bump(0.2, 0.34, 0.56, 0.7, 2.0)), key('leftleglower.xRot', bump(0.2, 0.34, 0.56, 0.7, 2.0)),
    key('bodytop.xRot', [[0, 0, 1], [0.14, 0.3, 1], [0.34, 0.5, 1], [0.56, 0.5, 1], [0.72, -0.1, 1], [1, 0, 1]]), key('head.xRot', [[0, 0, 1], [0.34, 0.5, 1], [0.56, 0.4, 1], [0.74, -0.25, 1], [0.9, -0.1, 1], [1, 0, 1]]),
    armKeys('right', { x: [[0, 0.09], [0.14, 0.7], [0.26, -2.9, E.out], [0.36, -1.2], [0.56, -1.2], [0.72, -0.3], [0.9, -0.2], [1, 0.09]], z: [[0, 0.17], [0.26, 0.2], [0.56, 0.3], [0.72, 1.3], [0.9, 1.1], [1, 0.17]], l: [[0, 0], [0.36, -1.7], [0.56, -1.7], [0.74, -0.2], [1, 0]] }),
    armKeys('left', { x: [[0, 0.09], [0.14, 0.7], [0.26, -2.9, E.out], [0.36, -1.2], [0.56, -1.2], [0.72, -0.3], [0.9, -0.2], [1, 0.09]], z: [[0, -0.17], [0.26, -0.2], [0.56, -0.3], [0.72, -1.3], [0.9, -1.1], [1, -0.17]], l: [[0, 0], [0.36, -1.7], [0.56, -1.7], [0.74, -0.2], [1, 0]] }),
    key('fx.glow', [[0, 0, 1], [0.4, 0.8, 1], [0.74, 1.0, 1], [1, 0, 1]]), pulses('ev.confetti', [0.74]), pulses('ev.bells', [0.3, 0.5, 0.74]),
  ]));
}

// 4 — juggling: three glowing balls arc between her hands while she watches them
{
  const P = 40;                                         // ticks per ball cycle
  const BALLS = [0, 1, 2];
  const jt = BALLS.flatMap((i) => [
    osc(`x_ball${i}.x`, 5.5, P, Math.PI / 2 + (i * PI2) / 3, 1),
    loop(`x_ball${i}.y`, Array.from({ length: 25 }, (_, q) => { const u = q / 24; return [u, -0.5 - 6.5 * Math.abs(Math.sin(Math.PI * (u + i / 3 + 0.0))), 0]; }), P),
    key(`x_ball${i}.vis`, [[0, 0, 0], [0.07, 0, 0], [0.09, 1, 0], [0.91, 1, 0], [0.93, 0, 0], [1, 0, 0]]),
  ]);
  F.push(flourish('juggle', 110, [
    ...jt,
    key('head.xRot', [[0, 0, 1], [0.12, -0.28, 1], [0.88, -0.28, 1], [1, 0, 1]]), osc('head.yRot', 0.22, P, 0, 1), osc('bodytop.zRot', 0.05, P, 0, 1), osc('root.y', -0.3, P / 2, 0, 1),
    armKeys('right', { x: [[0, 0.09], [0.12, -0.95], [0.88, -0.95], [1, 0.09]], z: [[0, 0.17], [0.12, 0.25], [0.88, 0.25], [1, 0.17]], l: [[0, 0], [0.12, -1.0], [0.88, -1.0], [1, 0]] }),
    armKeys('left', { x: [[0, 0.09], [0.12, -0.95], [0.88, -0.95], [1, 0.09]], z: [[0, -0.17], [0.12, -0.25], [0.88, -0.25], [1, -0.17]], l: [[0, 0], [0.12, -1.0], [0.88, -1.0], [1, 0]] }),
    osc('rightarm.xRot', 0.22, P, 0, 1), osc('leftarm.xRot', 0.22, P, Math.PI, 1), osc('rightarmlower.xRot', -0.3, P, 1.4, 1), osc('leftarmlower.xRot', -0.3, P, 1.4 + Math.PI, 1),
    osc('x_hatbell.zRot', 0.15, 10, 0, 1), osc('x_hatbbell.zRot', 0.15, 12, 1, 1),
    key('fx.glow', [[0, 0, 1], [0.1, 0.5, 1], [0.9, 0.5, 1], [1, 0, 1]]), pulses('ev.bells', [0.3, 0.5, 0.7]), pulses('ev.confetti', [0.92]),
  ]));
}

// 5 — dance: hips swing, arms pump, knees pop, head bobs, bells ring to the beat (one beat every 15 ticks)
{
  const BEAT = 30;
  F.push(flourish('dance', 96, [
    osc('bodybottom.zRot', 0.22, BEAT, 0, 1), osc('bodybottom.yRot', 0.18, BEAT * 2, 0, 1), osc('bodytop.zRot', -0.16, BEAT, 0, 1), osc('bodytop.yRot', -0.2, BEAT * 2, 0, 1),
    key('root.y', [[0, 0, 1], [0.1, -0.5, 1], [0.9, -0.5, 1], [1, 0, 1]]), osc('root.y', 0.5, BEAT / 2, Math.PI / 2, 1),
    osc('rightleg.xRot', -0.35, BEAT, 0, 1), osc('rightleglower.xRot', 0.55, BEAT, Math.PI / 2, 1), osc('leftleg.xRot', -0.35, BEAT, Math.PI, 1), osc('leftleglower.xRot', 0.55, BEAT, Math.PI * 1.5, 1),
    armKeys('right', { x: [[0, 0.09], [0.12, -0.9], [0.88, -0.9], [1, 0.09]], z: [[0, 0.17], [0.12, 0.5], [0.88, 0.5], [1, 0.17]], l: [[0, 0], [0.12, -1.2], [0.88, -1.2], [1, 0]] }),
    armKeys('left', { x: [[0, 0.09], [0.12, -0.9], [0.88, -0.9], [1, 0.09]], z: [[0, -0.17], [0.12, -0.5], [0.88, -0.5], [1, -0.17]], l: [[0, 0], [0.12, -1.2], [0.88, -1.2], [1, 0]] }),
    osc('rightarm.xRot', 0.45, BEAT, 0, 1), osc('leftarm.xRot', 0.45, BEAT, Math.PI, 1), osc('rightarm.zRot', 0.3, BEAT, 1.0, 1), osc('leftarm.zRot', -0.3, BEAT, 1.0 + Math.PI, 1),
    osc('head.zRot', 0.2, BEAT, 0.6, 1), osc('head.xRot', 0.1, BEAT / 2, 0, 1), osc('head.yRot', 0.15, BEAT * 2, 1, 1),
    osc('x_hatbell.zRot', 0.18, BEAT / 2, 0, 1), osc('x_hatbbell.zRot', 0.18, BEAT / 2, 1.5, 1),
    key('fx.glow', [[0, 0, 1], [0.1, 0.4, 1], [0.9, 0.4, 1], [1, 0, 1]]), pulses('ev.note', [0.15, 0.3, 0.45, 0.6, 0.75, 0.9]), pulses('ev.bells', [0.2, 0.4, 0.6, 0.8]),
  ]));
}

// 6 — tip of the hat with a roguish lean, other hand on her hip
F.push(flourish('hattip', 64, [
  armKeys('right', { x: [[0, 0.09], [0.2, -2.7, E.back], [0.8, -2.7], [1, 0.09]], y: [[0, 0], [0.2, -0.3], [0.8, -0.3], [1, 0]], z: [[0, 0.17], [0.2, 0.6], [0.8, 0.6], [1, 0.17]], l: [[0, 0], [0.2, -1.8], [0.8, -1.8], [1, 0]] }),
  armKeys('left', { x: [[0, 0.09], [0.2, 0.25], [0.8, 0.25], [1, 0.09]], z: [[0, -0.17], [0.2, -0.95], [0.8, -0.95], [1, -0.17]], l: [[0, 0], [0.2, -1.05], [0.8, -1.05], [1, 0]] }),
  key('bodytop.xRot', [[0, 0, 1], [0.2, -0.12, 1], [0.8, -0.12, 1], [1, 0, 1]]), key('head.zRot', [[0, 0, 1], [0.25, -0.22, 1], [0.8, -0.22, 1], [1, 0, 1]]), key('head.xRot', [[0, 0, 1], [0.25, 0.18, 1], [0.8, 0.18, 1], [1, 0, 1]]),
  key('bodybottom.zRot', [[0, 0, 1], [0.25, 0.12, 1], [0.8, 0.12, 1], [1, 0, 1]]), key('root.y', bump(0.18, 0.28, 0.4, 0.5, -1.2)),
  key('rightleg.xRot', bump(0.2, 0.3, 0.8, 0.95, -0.25)), key('rightleglower.xRot', bump(0.2, 0.3, 0.8, 0.95, 0.35)),
  osc('x_hatbell.zRot', 0.14, 9, 0, 1), osc('x_hatbbell.zRot', 0.14, 11, 1, 1), osc('x_hat0.zRot', 0.1, 14, 0, 1),
  key('fx.glow', [[0, 0, 1], [0.35, 0.8, 1], [0.7, 0, 1], [1, 0, 1]]), pulses('ev.confetti', [0.36]), pulses('ev.bells', [0.3]),
]));

// 7 — ta-da: a high split leap with arms flung into a V, landing in a flamboyant pose
F.push(flourish('tada', 70, [
  key('root.y', [[0, 0, 1], [0.16, 1.1, 1], [0.34, -5.2, E.out], [0.52, -5.2, 1], [0.7, 0.5, E.in], [0.78, 1.0, 1], [0.9, 0, 1], [1, 0, 1]]),
  key('rightleg.xRot', [[0, 0, 1], [0.16, -0.5, 1], [0.34, -1.1, E.out], [0.52, -1.1, 1], [0.72, -0.3, 1], [0.9, -0.15, 1], [1, 0, 1]]), key('leftleg.xRot', [[0, 0, 1], [0.16, -0.5, 1], [0.34, 0.8, E.out], [0.52, 0.8, 1], [0.72, 0.1, 1], [0.9, 0, 1], [1, 0, 1]]),
  key('rightleglower.xRot', bump(0.16, 0.34, 0.52, 0.74, 0.5)), key('leftleglower.xRot', [[0, 0, 1], [0.16, 0.9, 1], [0.34, 0.2, 1], [0.52, 0.2, 1], [0.74, 1.0, 1], [0.9, 0.2, 1], [1, 0, 1]]),
  key('bodytop.xRot', [[0, 0, 1], [0.16, 0.2, 1], [0.34, -0.2, 1], [0.52, -0.2, 1], [0.78, 0.15, 1], [1, 0, 1]]), key('head.xRot', [[0, 0, 1], [0.34, -0.3, 1], [0.52, -0.3, 1], [0.78, -0.1, 1], [1, 0, 1]]),
  key('bodybottom.zRot', [[0, 0, 1], [0.78, 0, 1], [0.9, -0.15, 1], [1, 0, 1]]),
  armKeys('right', { x: [[0, 0.09], [0.16, 0.6], [0.3, -2.5, E.out], [0.52, -2.5], [0.78, -0.3], [0.92, -0.2], [1, 0.09]], z: [[0, 0.17], [0.3, 0.9], [0.52, 0.9], [0.78, 1.5], [0.92, 1.2], [1, 0.17]], l: [[0, 0], [0.3, -0.1], [0.78, -0.3], [1, 0]] }),
  armKeys('left', { x: [[0, 0.09], [0.16, 0.6], [0.3, -2.5, E.out], [0.52, -2.5], [0.78, -0.3], [0.92, -0.2], [1, 0.09]], z: [[0, -0.17], [0.3, -0.9], [0.52, -0.9], [0.78, -1.5], [0.92, -1.2], [1, -0.17]], l: [[0, 0], [0.3, -0.1], [0.78, -0.3], [1, 0]] }),
  osc('x_hatbell.zRot', 0.22, 9, 0, 1), osc('x_hatbbell.zRot', 0.22, 10, 1, 1),
  key('fx.glow', [[0, 0, 1], [0.34, 1.0, 1], [0.8, 0.6, 1], [1, 0, 1]]), pulses('ev.confetti', [0.36, 0.74]), pulses('ev.bells', [0.34, 0.74]),
]));

module.exports = { motion: noGlow({ idle, walk, run, flourish: F }) };
