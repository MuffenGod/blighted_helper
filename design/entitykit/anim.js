// Emily v2 animations (Bedrock 1.8.0 format, GeckoLib). Degrees / model units / seconds.
// Layers: "main" (pose), "sway" (secondary motion on chain bones: tail, hair, ears, skirt, wing fan), "blink", "expr" (face), "attack".
// Rotation signs follow GeckoLib: a negative X rotation swings the bottom of a hanging limb toward the model's front.
module.exports = function ({ bones }) {
  const have = new Set(bones);
  const anims = {};
  const sides = [['l', 1], ['r', -1]];
  const N = 8;

  function anim(name, len, loop) { anims['animation.emily.' + name] = { animation_length: len, loop, bones: {} }; return anims['animation.emily.' + name]; }
  function put(a, bone, ch, val) {
    if (!have.has(bone)) return;
    (a.bones[bone] = a.bones[bone] || {})[ch] = val;
  }
  const keys = (obj) => { const o = {}; for (const [t, v] of obj) o[String(+t.toFixed(4))] = v; return o; };
  const sine = (len, base, amp, phase = 0, n = N) => {           // amp is a [x,y,z] amplitude, loops seamlessly
    const out = [];
    for (let i = 0; i <= n; i++) {
      const s = Math.sin((i / n) * Math.PI * 2 + phase);
      out.push([(len * i) / n, base.map((b, k) => +(b + amp[k] * s).toFixed(3))]);
    }
    return keys(out);
  };

  // ------------------------------------------------------------------ wing poses (rotations are DELTAS on top of the fan angles baked into the geo)
  function wingsFolded(a) {
    for (const [n, sg] of sides) {
      put(a, 'wing_' + n, 'rotation', [0, sg * 2, 0]);
      for (let k = 0; k < 4; k++) put(a, 'wg_' + n + k, 'rotation', [0, 0, sg * (-k * 2)]);
    }
  }
  function wingsWrapped(a) {
    for (const [n, sg] of sides) {
      put(a, 'wing_' + n, 'rotation', [0, sg * -76, 0]);
      for (let k = 0; k < 4; k++) put(a, 'wg_' + n + k, 'rotation', [0, 0, sg * (-18 - k * 8)]);
    }
  }

  // ------------------------------------------------------------------ idle (+ variants)
  const idle = anim('idle', 6, true);
  put(idle, 'allbody', 'position', sine(6, [0, 0, 0], [0, 0.12, 0]));
  put(idle, 'allbody', 'rotation', sine(6, [0, 0, 0], [0, 0, 1.2], Math.PI / 2));
  put(idle, 'upbody', 'rotation', sine(6, [0, 0, 0], [0.9, 0, 0]));
  put(idle, 'head', 'rotation', keys([[0, [0, 0, 0]], [1.5, [1.5, 4, 0]], [3, [0, 0, 0]], [4.5, [1.5, -4, 0]], [6, [0, 0, 0]]]));
  put(idle, 'arm_l', 'rotation', sine(6, [3, 0, -4], [0, 0, 1.2], Math.PI));
  put(idle, 'arm_l2', 'rotation', [-12, 0, 0]);
  put(idle, 'arm_r', 'rotation', sine(6, [-30, -4, 5], [1.2, 0, 0.8]));        // sword hand
  put(idle, 'arm_r2', 'rotation', [-26, 0, 0]);
  put(idle, 'leg_l', 'rotation', [0, 0, -1.5]);
  put(idle, 'leg_r', 'rotation', [0, 0, 1.5]);
  wingsFolded(idle);

  const look = anim('idle_look', 4, false);
  put(look, 'head', 'rotation', keys([[0, [0, 0, 0]], [0.8, [-3, -26, 0]], [1.8, [-3, -26, 0]], [2.4, [2, 24, 0]], [3.2, [2, 24, 0]], [4, [0, 0, 0]]]));
  put(look, 'upbody', 'rotation', keys([[0, [0, 0, 0]], [0.8, [0, -6, 0]], [1.8, [0, -6, 0]], [2.4, [0, 6, 0]], [3.2, [0, 6, 0]], [4, [0, 0, 0]]]));
  put(look, 'arm_r', 'rotation', [-30, -4, 5]); put(look, 'arm_r2', 'rotation', [-26, 0, 0]);
  wingsFolded(look);

  const hair = anim('idle_hair', 3.6, false);
  put(hair, 'arm_l', 'rotation', keys([[0, [3, 0, -4]], [0.7, [-118, 24, -26]], [2.7, [-118, 24, -26]], [3.4, [3, 0, -4]]]));
  put(hair, 'arm_l2', 'rotation', keys([[0, [-12, 0, 0]], [0.7, [-95, 0, 0]], [2.7, [-95, 0, 0]], [3.4, [-12, 0, 0]]]));
  put(hair, 'head', 'rotation', keys([[0, [0, 0, 0]], [0.8, [-4, 10, 7]], [2.6, [-4, 10, 7]], [3.4, [0, 0, 0]]]));
  put(hair, 'arm_r', 'rotation', [-30, -4, 5]); put(hair, 'arm_r2', 'rotation', [-26, 0, 0]);
  wingsFolded(hair);

  const sword = anim('idle_sword', 3, false);
  put(sword, 'arm_r', 'rotation', keys([[0, [-30, -4, 5]], [0.6, [-70, -10, 10]], [2.2, [-70, -10, 10]], [3, [-30, -4, 5]]]));
  put(sword, 'arm_r2', 'rotation', keys([[0, [-26, 0, 0]], [0.6, [-60, 0, 0]], [2.2, [-60, 0, 0]], [3, [-26, 0, 0]]]));
  put(sword, 'head', 'rotation', keys([[0, [0, 0, 0]], [0.7, [14, -16, 0]], [2.1, [14, -16, 0]], [3, [0, 0, 0]]]));
  wingsFolded(sword);

  // ------------------------------------------------------------------ walk
  const walk = anim('walk', 1, true);
  const ph = (i, flip) => (flip ? -1 : 1);
  put(walk, 'leg_l', 'rotation', keys([[0, [-16, 0, 0]], [0.25, [0, 0, 0]], [0.5, [16, 0, 0]], [0.75, [0, 0, 0]], [1, [-16, 0, 0]]]));
  put(walk, 'leg_r', 'rotation', keys([[0, [16, 0, 0]], [0.25, [0, 0, 0]], [0.5, [-16, 0, 0]], [0.75, [0, 0, 0]], [1, [16, 0, 0]]]));
  put(walk, 'leg_l2', 'rotation', keys([[0, [2, 0, 0]], [0.25, [16, 0, 0]], [0.5, [2, 0, 0]], [0.75, [5, 0, 0]], [1, [2, 0, 0]]]));
  put(walk, 'leg_r2', 'rotation', keys([[0, [2, 0, 0]], [0.25, [5, 0, 0]], [0.5, [2, 0, 0]], [0.75, [16, 0, 0]], [1, [2, 0, 0]]]));
  put(walk, 'allbody', 'position', keys([[0, [0, 0.1, 0]], [0.25, [0, 0.45, 0]], [0.5, [0, 0.1, 0]], [0.75, [0, 0.45, 0]], [1, [0, 0.1, 0]]]));
  put(walk, 'allbody', 'rotation', keys([[0, [3, 3, 1.6]], [0.5, [3, -3, -1.6]], [1, [3, 3, 1.6]]]));
  put(walk, 'upbody', 'rotation', keys([[0, [0, -4, 0]], [0.5, [0, 4, 0]], [1, [0, -4, 0]]]));
  put(walk, 'head', 'rotation', keys([[0, [-3, 2, 0]], [0.5, [-3, -2, 0]], [1, [-3, 2, 0]]]));
  put(walk, 'arm_l', 'rotation', keys([[0, [15, 0, -4]], [0.5, [-15, 0, -4]], [1, [15, 0, -4]]]));
  put(walk, 'arm_l2', 'rotation', [-14, 0, 0]);
  put(walk, 'arm_r', 'rotation', keys([[0, [-26, -4, 5]], [0.5, [-38, -4, 5]], [1, [-26, -4, 5]]]));
  put(walk, 'arm_r2', 'rotation', [-26, 0, 0]);
  wingsFolded(walk);

  // ------------------------------------------------------------------ attack (sword slash)
  const atk = anim('attack', 0.5, false);
  put(atk, 'arm_r', 'rotation', keys([[0, [-30, -4, 5]], [0.1, [-140, 8, 14]], [0.24, [-25, -16, -8]], [0.5, [-30, -4, 5]]]));
  put(atk, 'arm_r2', 'rotation', keys([[0, [-26, 0, 0]], [0.1, [-40, 0, 0]], [0.24, [-6, 0, 0]], [0.5, [-26, 0, 0]]]));
  put(atk, 'upbody', 'rotation', keys([[0, [0, 0, 0]], [0.1, [-3, 16, 0]], [0.24, [6, -22, 0]], [0.5, [0, 0, 0]]]));
  put(atk, 'allbody', 'rotation', keys([[0, [0, 0, 0]], [0.1, [-2, 0, 0]], [0.24, [10, 0, 0]], [0.5, [0, 0, 0]]]));
  put(atk, 'head', 'rotation', keys([[0, [0, 0, 0]], [0.24, [-6, -8, 0]], [0.5, [0, 0, 0]]]));
  put(atk, 'leg_l', 'rotation', keys([[0, [0, 0, -1.5]], [0.24, [-22, 0, -1.5]], [0.5, [0, 0, -1.5]]]));
  put(atk, 'leg_r', 'rotation', keys([[0, [0, 0, 1.5]], [0.24, [14, 0, 1.5]], [0.5, [0, 0, 1.5]]]));

  // ------------------------------------------------------------------ kneel (seiza): she sinks to the floor, wings wrap around her
  const kn = anim('kneel', 1.6, 'hold_on_last_frame');
  put(kn, 'allbody', 'position', keys([[0, [0, 0, 0]], [1.0, [0, -11.4, 0.4]]]));
  put(kn, 'allbody', 'rotation', keys([[0, [0, 0, 0]], [1.0, [5, 0, 0]]]));
  put(kn, 'leg_l', 'rotation', keys([[0, [0, 0, -1.5]], [1.0, [-72, 8, -3]]]));
  put(kn, 'leg_r', 'rotation', keys([[0, [0, 0, 1.5]], [1.0, [-72, -8, 3]]]));
  put(kn, 'leg_l2', 'rotation', keys([[0, [0, 0, 0]], [1.0, [162, 0, 0]]]));
  put(kn, 'leg_r2', 'rotation', keys([[0, [0, 0, 0]], [1.0, [162, 0, 0]]]));
  put(kn, 'leg_l3', 'rotation', keys([[0, [0, 0, 0]], [1.0, [-60, 0, 0]]]));
  put(kn, 'leg_r3', 'rotation', keys([[0, [0, 0, 0]], [1.0, [-60, 0, 0]]]));
  put(kn, 'upbody', 'rotation', keys([[0, [0, 0, 0]], [1.0, [6, 0, 0]]]));
  put(kn, 'head', 'rotation', keys([[0, [0, 0, 0]], [1.0, [30, 0, 0]]]));
  put(kn, 'arm_l', 'rotation', keys([[0, [3, 0, -4]], [1.0, [-48, 12, -8]]]));
  put(kn, 'arm_r', 'rotation', keys([[0, [-30, -4, 5]], [1.0, [-48, -12, 8]]]));
  put(kn, 'arm_l2', 'rotation', keys([[0, [-12, 0, 0]], [1.0, [-42, 0, 0]]]));
  put(kn, 'arm_r2', 'rotation', keys([[0, [-26, 0, 0]], [1.0, [-42, 0, 0]]]));
  for (let i = 0; i < 14; i++) put(kn, 'sk' + i + '_0', 'scale', keys([[0, [1, 1, 1]], [1.0, [1.45, 0.26, 1.45]]]));
  for (const [n, sg] of sides) {
    put(kn, 'wing_' + n, 'rotation', keys([[0, [0, sg * -6, 0]], [1.4, [0, sg * -76, 0]]]));
    for (let k = 0; k < 4; k++) put(kn, 'wg_' + n + k, 'rotation', keys([[0, [0, 0, sg * (k * 4)]], [1.4, [0, 0, sg * (28 + k * 14)]]]));
  }

  // ------------------------------------------------------------------ sway: secondary motion on chain bones, always running (idle strength)
  const swayIdle = anim('sway', 4, true);
  const swayWalk = anim('sway_walk', 1, true);
  function sway(a, k) {                                // k: strength multiplier, len: loop length
    const len = a.animation_length;
    for (let i = 1; i <= 7; i++) put(a, 'tail' + i, 'rotation', sine(len, [0, 0, 0], [(i % 2 ? 0.8 : 0.5) * (1 + i * 0.35) * k, 0, 0], i * 0.5));
    for (let i = 1; i <= 7; i++) put(a, 'tail' + i, 'position', sine(len, [0, 0, 0], [0, 0, 0]));
    // tail also arcs side to side (rotation y shares the same channel: rebuild with y)
    for (let i = 1; i <= 7; i++) put(a, 'tail' + i, 'rotation', sine(len, [0, 0, 0], [0.4 * k, (2.2 + i * 0.8) * k, 0], i * 0.55 + 1));
    [['hb0_', 5], ['hb1_', 4], ['hb2_', 4]].forEach(([p, cnt], ci) => {
      for (let i = 0; i < cnt + (ci === 0 ? 1 : 0); i++) {
        if (!have.has(p + i)) continue;
        put(a, p + i, 'rotation', sine(len, [0, 0, 0], [(1.4 + i * 0.5) * k, 0, (1.0 + i * 0.5) * k * (ci === 2 ? -1 : 1)], i * 0.6 + ci));
      }
    });
    for (const [n, sg] of sides) {
      for (let li = 0; li < 2; li++) for (let s = 0; s < 4; s++) put(a, 'lock_' + n + li + '_' + s, 'rotation', sine(len, [0, 0, 0], [(0.8 + s * 0.4) * k, 0, (1.0 + s * 0.5) * k * sg], s * 0.5 + li));
      for (let i = 0; i < 4; i++) put(a, 'bang_' + n + i + 'b', 'rotation', sine(len, [0, 0, 0], [1.5 * k, 0, 0.8 * k * sg], i * 0.7));
      put(a, 'ear_' + n, 'rotation', sine(len, [0, 0, 0], [0, 0, 1.2 * k * sg], 0.4));
      put(a, 'ear_' + n + '2', 'rotation', sine(len, [0, 0, 0], [0, 0, 2.0 * k * sg], 0.9));
      put(a, 'ear_' + n + '3', 'rotation', sine(len, [0, 0, 0], [0, 0, 2.5 * k * sg], 1.4));
    }
    for (let i = 0; i < 14; i++) {
      for (let s = 1; s < 3; s++) put(a, 'sk' + i + '_' + s, 'rotation', sine(len, [0, 0, 0], [(1.3 + s * 0.7) * k, 0, 0], i * 0.45 + s));
    }
  }
  sway(swayIdle, 1);
  sway(swayWalk, 3.2);
  // walking: skirt panels in front swing back with the forward leg, back panels forward (phase by angle)
  for (let i = 0; i < 14; i++) {
    const phi = (i * 360) / 14, c = Math.cos((phi * Math.PI) / 180);
    for (let s = 1; s < 3; s++) put(swayWalk, 'sk' + i + '_' + s, 'rotation', sine(1, [0, 0, 0], [(s === 1 ? 6 : 9) * c, 0, 0], 0));
  }

  // ------------------------------------------------------------------ blink + expressions (face overlays: ov_blink = closed eyes, ov_grin = open grin)
  const HIDE = [1, 0, 1], SHOW = [1, 1, 1], OFF = [0, 0, 0];
  const blink = anim('blink', 5.5, true);
  put(blink, 'ov_blink', 'scale', keys([[0, HIDE], [4.9, HIDE], [4.95, SHOW], [5.12, SHOW], [5.17, HIDE], [5.5, HIDE]]));
  function expr(name, grin, calm) {
    const a = anim(name, 1, 'hold_on_last_frame');
    put(a, 'ov_grin', 'scale', grin ? SHOW : OFF);
    if (calm) put(a, 'ov_blink', 'scale', SHOW);
    return a;
  }
  expr('expr_smile', false, false);
  expr('expr_grin', true, false);
  expr('expr_calm', false, true);

  return { format_version: '1.8.0', animations: anims };
};
