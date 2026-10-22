// JESTER — Harlequin. Silver hair, red eyes, purple-and-black motley. Banded two-point cap with gold bells, pleated cream ruff, harlequin tabard,
// asymmetric puffed sleeves with frilled cuffs, a two-tier tutu, diamond stockings and curled bell shoes.
const { P, cube, chain, ring, PI } = require('./kit');

const BAND = (i) => (i % 2 === 0 ? { ramp: 'purple' } : { ramp: 'cloth' });

function hatPoint(V, s, o) {
  const n = o.n, name = 'x_hat' + (s > 0 ? 'l' : 'r');
  const parts = chain(name, 'head', V, n, { pivot: [s * (o.dx || 2.4), -7.4, o.dz || 0.2], rot: [o.rx === undefined ? -0.12 : o.rx, 0, s * (o.rz === undefined ? 0.75 : o.rz)] }, (i, t) => {
    const base = BAND(i + (o.phase || 0));
    return {
      rot: [-0.02, 0, s * ((o.curl || 0.27) * (0.45 + 1.1 * t))], len: (o.len || 2.9) * (1 - 0.16 * t), w: (o.w || 4.2) * (1 - 0.8 * t) + 0.7, d: (o.w || 4.2) * (1 - 0.8 * t) + 0.7, over: 0.5,
      mat: 'fabric', o: { ...base, ramp2: base.ramp === 'purple' ? 'cloth' : 'purple', pat: i % 3 === 1 ? 'diamond' : 'plain', pw: 2, folds: 2, foldAmt: 0.45, lift: 0.08, top: 0.1, topRamp: 'gold', hem: 0.1, hemRamp: 'gold', glowTrim: 0.3, outline: true },
      sway: [0.012, 0.0, 0.03 + 0.012 * i, 0.09 + 0.012 * i, i * 0.45 + (o.ph || 0)], phys: { gc: -0.5, v: [0, s * 0.2], p: 15 },
    };
  }, { dir: -1, hd: 1.5 });
  const last = name + (n - 1);
  parts.push(P(name + 'bell', last, V, { pivot: [0, -(o.len || 2.9) * 0.84 - 0.2, 0], hd: 1.5, sway: [0.06, 0.0, 0.08, 0.16, (o.ph || 0) + 2.0], cubes: [
    cube(-1.5, -1.5, -1.5, 3.0, 3.0, 3.0, 'orb', { ramp: 'gold', glow: 0.5, slit: true })] }));
  return parts;
}

function jester() {
  const V = 'jester';
  const parts = [];

  // ---- cap: harlequin band, two long banded points that curl out and down, ending in gold bells; a short third tuft at the back
  parts.push(P('x_hatband', 'head', V, { hd: 1.5, cubes: [
    cube(-4.5, -8.0, -4.5, 9.0, 1.9, 9.0, 'fabric', { ramp: 'purple', ramp2: 'cloth', pat: 'diamond', pw: 2, folds: 6, foldAmt: 0.4, top: 0.1, topRamp: 'gold', hem: 0.12, hemRamp: 'gold', glowTrim: 0.35, outline: true }),
    cube(-4.1, -9.0, -4.1, 8.2, 1.2, 8.2, 'fabric', { ramp: 'cloth', ramp2: 'purple', pat: 'diamond', pw: 2, folds: 5, foldAmt: 0.4, outline: true })] }));
  parts.push(...hatPoint(V, 1, { n: 8, len: 3.0, w: 4.4, rz: 0.6, curl: 0.3, phase: 0, ph: 0.0 }));
  parts.push(...hatPoint(V, -1, { n: 7, len: 2.8, w: 4.0, rz: 0.6, curl: 0.3, phase: 1, ph: 1.7, rx: -0.2 }));

  // ---- pleated ruff (two layers) and a harlequin tabard
  parts.push(...ring('x_ruffa', 'neck', V, { n: 10, R: 3.3, y: 1.2, tilt: 0.9, hd: 1.5, group: 'ruff', cubes: () => [cube(-1.55, -2.4, -0.5, 3.1, 2.6, 0.9, 'frill', { ramp: 'ruff', pleat: 2, trim: 'gold', lace: true })] }));
  parts.push(P('x_tabard', 'bodytop_female', V, { hd: 1.5, cubes: [
    cube(-2.7, -5.7, -3.2, 5.4, 6.0, 0.7, 'fabric', { ramp: 'purple', ramp2: 'cloth', pat: 'diamond', pw: 3, folds: 1, foldAmt: 0.3, edge: 0.07, top: 0.08, topRamp: 'gold', hem: 0.1, hemRamp: 'gold', glowTrim: 0.4, outline: true }),
    cube(-0.5, -4.8, -3.6, 1.0, 1.0, 0.5, 'orb', { ramp: 'gold', glow: 0.7 }), cube(-0.5, -2.8, -3.6, 1.0, 1.0, 0.5, 'orb', { ramp: 'gold', glow: 0.7 }), cube(-0.5, -0.8, -3.6, 1.0, 1.0, 0.5, 'orb', { ramp: 'gold', glow: 0.7 })] }));

  // ---- sleeves: asymmetric puffs, frilled cuffs
  for (const [side, sg, A, B] of [['right', -1, 'cloth', 'purple'], ['left', 1, 'purple', 'cloth']]) {
    const x0 = sg < 0 ? -3.4 : -1.0;
    parts.push(P('x_puff' + side[0], side + 'arm', V, { hd: 1.5, cubes: [
      cube(x0, -2.0, -2.5, 4.4, 4.4, 5.0, 'fabric', { ramp: A, ramp2: B, pat: 'diamond', pw: 2, folds: 5, foldAmt: 0.5, lift: 0.06, hem: 0.1, hemRamp: 'gold', outline: true, glowTrim: 0.3 }),
      cube(sg < 0 ? -2.6 : -0.6, 2.4, -2.1, 3.6, 2.4, 4.2, 'fabric', { ramp: A, folds: 4, foldAmt: 0.5, hem: 0.15, hemRamp: 'gold', outline: true })] }));
    parts.push(P('x_cuff' + side[0], side + 'armlower', V, { hd: 1.5, sway: [0.03, 0, 0.03, 0.09, sg > 0 ? 0 : 1], cubes: [
      cube(sg < 0 ? -2.8 : -1.2, 0.0, -2.8, 4.0, 3.2, 4.8, 'fabric', { ramp: B, ramp2: A, pat: 'diamond', pw: 2, folds: 4, foldAmt: 0.4, outline: true }),
      cube(sg < 0 ? -2.9 : -1.9, 3.4, -2.9, 3.8, 1.8, 4.6, 'frill', { ramp: 'ruff', pleat: 2, trim: 'gold', lace: true })] }));
  }

  // ---- motley tutu: two tiers of alternating petals, gold-scalloped hems
  parts.push(...ring('x_tuta', 'bodybottom', V, { n: 12, R: 4.2, rz: 3.0, y: 1.4, tilt: -0.75, hd: 1.5, group: 'skirt', sway: [0.05, 0, 0.03, 0.08, 0.0],
    cubes: (i) => [cube(-1.8, 0, -0.5, 3.6, 5.0, 0.9, 'fabric', { ...BAND(i), ramp2: i % 2 ? 'purple' : 'cloth', folds: 2, foldAmt: 0.6, lift: 0.06, hem: 0.16, hemRamp: 'gold', hemStyle: 'scallop', glowTrim: 0.3, outline: true })] }));

  // ---- diamond stockings are painted on the legs; curled shoes with bells
  for (const [side, sg] of [['right', -1], ['left', 1]]) {
    const A = sg > 0 ? 'purple' : 'cloth', B = sg > 0 ? 'cloth' : 'purple';
    parts.push(P('x_jshoe' + side[0], side + 'leglower', V, { hd: 1.5, cubes: [
      cube(-2.2, 5.2, -3.6, 4.4, 2.0, 5.6, 'fabric', { ramp: A, ramp2: B, pat: 'diamond', pw: 2, folds: 1, foldAmt: 0.3, hem: 0.12, hemRamp: 'gold', top: 0.2, topRamp: 'gold', outline: true, glowTrim: 0.3 })] }));
    parts.push(...chain('x_toe' + side[0], side + 'leglower', V, 3, { pivot: [0, 6.2, -3.4], rot: [-1.5, 0, 0] }, (i, t) => ({
      rot: [-0.42, 0, 0], len: 1.9, w: 3.4 - 1.2 * t, d: 1.9 - 0.5 * t, over: 0.3, mat: 'fabric', o: { ramp: A, ramp2: B, pat: 'plain', folds: 1, foldAmt: 0.3, outline: true, top: i === 0 ? 0.0 : 0 }, sway: [0.03, 0, 0.02, 0.1, i * 0.4],
    }), { dir: 1, hd: 1.5 }));
    parts.push(P('x_tbell' + side[0], 'x_toe' + side[0] + '2', V, { pivot: [0, 1.7, 0], hd: 1.5, sway: [0.1, 0, 0.06, 0.18, sg > 0 ? 0 : 1.4], cubes: [cube(-1.1, 0, -1.1, 2.2, 2.2, 2.2, 'orb', { ramp: 'gold', glow: 0.45, slit: true })] }));
  }

  // ---- juggling props (hidden until the juggling flourish): three glowing balls that orbit in front of her chest
  [['gold', 0], ['red', 1], ['purple', 2]].forEach(([ramp, i]) => {
    parts.push(P('x_ball' + i, 'bodytop', V, { pivot: [0, -1.5, -6.5], hidden: true, hd: 1.5, cubes: [cube(-1.3, -1.3, -1.3, 2.6, 2.6, 2.6, 'orb', { ramp, glow: 0.6 })] }));
  });

  return { parts, hide: ['hornbase', 'halo', 'skirt1', 'tail1', 'leftwing', 'rightwing', 'chest'], tweak: { rightarm: [1.0, 1, 1.0], leftarm: [1.0, 1, 1.0], bodytop: [1.0, 0.95, 1.0] } };
}

module.exports = { jester };
