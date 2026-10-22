// ANGEL — the nun. A plain black habit with thin gold trim, a white coif band, floating halo, veil, cross pendant and feathered wings.
// Kept deliberately simple: flat black cloth, few pieces, no pleats or sashes.
const { P, cube, chain, ring, PI } = require('./kit');
const { featherWing } = require('./lib_wings');

const HD = 1.5;
const FLAT = { ramp: 'cloth', lift: 0.08, folds: 1, foldAmt: 0.12 };   // flat black cloth

function angel() {
  const V = 'angel';
  const parts = [];

  // ---- halo: a ring of 16 gilded segments with a second ring of small orbs, floating above the veil
  parts.push(P('x_halo', 'head', V, { pivot: [0, -13.0, 0.6], rot: [0.12, 0, 0], sway: [0.025, 0.0, 0.02, 0.05, 0.0] }));
  parts.push(...ring('x_halor', 'x_halo', V, { n: 16, R: 5.6, y: 0, hd: HD, cubes: () => [cube(-1.2, -0.5, -0.8, 2.4, 1.0, 1.6, 'metal', { ramp: 'halo', bevel: 1, glow: 0.32 })] }));
  parts.push(...ring('x_halos', 'x_halo', V, { n: 6, R: 7.4, y: -0.2, off: 30, hd: HD, cubes: () => [cube(-0.5, -0.5, -0.5, 1, 1, 1, 'orb', { ramp: 'halo', glow: 0.9 })] }));

  // ---- veil: white coif band, black cap, a flowing back veil in two halves and short side drapes
  parts.push(P('x_veil', 'head', V, { hd: HD, cubes: [
    cube(-4.2, -8.4, -4.2, 8.4, 2.4, 8.4, 'fabric', FLAT),
    cube(-3.5, -9.3, -3.5, 7.0, 1.0, 7.0, 'fabric', FLAT),
    cube(-4.45, -6.2, -4.45, 8.9, 1.1, 8.9, 'fabric', { ramp: 'ruff', lift: 0.3, folds: 1, foldAmt: 0.12, hemRamp: 'gold', hem: 0.16, glowTrim: 0.3 }),
    cube(-0.7, -7.1, -4.8, 1.4, 1.6, 0.6, 'gem', { ramp: 'gold', glow: 0.7 }),
  ] }));
  for (const [sd, sg] of [['l', 1], ['r', -1]]) {
    parts.push(...chain('x_vb' + sd, 'head', V, 3, { pivot: [sg * 2.3, -8.1, 3.5], rot: [0.3, 0, sg * -0.03] }, (i) => ({
      rot: [i === 0 ? 0 : -0.1, 0, sg * -0.05], len: 6.4, w: 4.6 + 0.2 * i, d: 0.9, over: 0.5,
      mat: 'fabric', o: { ...FLAT, hemRamp: 'gold', ...(i === 2 ? { hem: 0.08 } : {}) },
      sway: [0.05 + 0.02 * i, 0.0, 0.05 + 0.01 * i, 0.06, i * 0.5 + (sg > 0 ? 0 : 1.3)], phys: { gc: 0.5, p: 22 },
    }), { dir: 1, hd: HD }));
    parts.push(...chain('x_vs' + sd, 'head', V, 2, { pivot: [sg * 4.45, -7.6, -0.6], rot: [0, 0, sg * -0.04] }, (i) => ({
      rot: [0.02, 0, sg * -0.03], len: 5.4, w: 0.9, d: 5.2 - 0.5 * i, over: 0.4,
      mat: 'fabric', o: { ...FLAT, hemRamp: 'gold', ...(i === 1 ? { hem: 0.1 } : {}) },
      sway: [0.03, 0.0, 0.03, 0.07, i * 0.5 + (sg > 0 ? 0 : 1)], phys: { gc: 0.4, p: 18 },
    }), { dir: 1, hd: HD }));
    // one cream lock framing the face
    parts.push(...chain('x_lock' + sd + '_', 'head', V, 3, { pivot: [sg * 3.5, -1.2, -2.9], rot: [0.05, 0, sg * 0.05] }, (i) => ({
      rot: [0.03, 0, sg * -0.012], len: 4.4, w: 1.8 - 0.2 * i, d: 1.1 - 0.1 * i, over: 0.4,
      mat: 'strand', o: { ramp: 'hair', dir: 1, taper: 0.5 },
      sway: [0.04, 0.0, 0.035, 0.08, i * 0.6], phys: { gc: 0.5, p: 17 },
    }), { dir: 1, hd: HD }));
    // a small gilded feather ornament at each temple
    parts.push(P('x_tf' + sd + '0', 'head', V, { pivot: [sg * 4.1, -5.4, -0.4], hd: HD,
      rot: [-0.5, sg * 0.35, sg * 0.7],
      cubes: [cube(-0.9, -0.2, -0.2, 1.8, 6.2, 0.4, 'feather', { ramp: 'gold', tip: 'feat', dir: 1, seed: 0, round: 0.6, selfGlow: 0.25 })],
      sway: [0.03, 0.0, 0.03, 0.1, 0.0] }));
  }

  // ---- cross on a chain
  parts.push(P('x_chain', 'bodytop_female', V, { pivot: [0, -5.8, -3.1], hd: HD, sway: [0.03, 0, 0.02, 0.06, 0],
    cubes: [0, 1, 2, 3, 4].flatMap((q) => [cube(-2.6 + 0.52 * q, q * 0.55, -0.3, 0.6, 0.6, 0.6, 'chain', { ramp: 'gold' }), cube(2.0 - 0.52 * q, q * 0.55, -0.3, 0.6, 0.6, 0.6, 'chain', { ramp: 'gold' })]) }));
  parts.push(P('x_cross', 'x_chain', V, { pivot: [0, 2.6, 0], sway: [0.05, 0.0, 0.03, 0.07, 0.4],
    cubes: [cube(-0.5, 0.0, -0.5, 1.0, 4.0, 0.9, 'metal', { ramp: 'gold', bevel: 1, glow: 0.3 }), cube(-1.5, 0.9, -0.5, 3.0, 1.0, 0.9, 'metal', { ramp: 'gold', bevel: 1, glow: 0.3 })] }));

  // ---- coat (2026-10-22, from her reference): a long black coat open at the front, gold-edged lapels, coat sleeves a little fuller than
  // her arms with a white-and-gold band at the cuff, and tails to the ankles at the back and sides (open in front, so her legs show).
  // Arms are her own (Gaia-model) arms at the harlequin's proportions (tweak below); no wrist ruffles, no skirt.
  const EDGE = { ...FLAT, edgeRamp: 'gold', edge: 0.16 };
  parts.push(P('x_coat', 'bodytop_female', V, { hd: HD, cubes: [
    cube(-2.95, -6.35, -0.15, 5.9, 6.6, 0.65, 'fabric', FLAT),                    // back
    cube(2.35, -6.35, -3.35, 0.65, 6.6, 3.85, 'fabric', FLAT),                    // her left side
    cube(-3.0, -6.35, -3.35, 0.65, 6.6, 3.85, 'fabric', FLAT),                    // her right side
    cube(1.35, -6.35, -3.45, 1.3, 6.6, 0.55, 'fabric', EDGE),                     // lapels
    cube(-2.65, -6.35, -3.45, 1.3, 6.6, 0.55, 'fabric', EDGE),
  ] }));
  parts.push(P('x_coatw', 'bodymiddle', V, { hd: HD, cubes: [
    cube(-2.75, -2.0, 0.45, 5.5, 2.6, 0.6, 'fabric', FLAT),
    cube(2.15, -2.0, -1.9, 0.6, 2.6, 2.95, 'fabric', FLAT),
    cube(-2.75, -2.0, -1.9, 0.6, 2.6, 2.95, 'fabric', FLAT),
  ] }));
  for (const [side, sg] of [['right', -1], ['left', 1]]) {
    const ux = sg < 0 ? -2.35 : -0.35, lx = sg < 0 ? -1.75 : -1.25;
    parts.push(P('x_csu' + side[0], side + 'arm', V, { hd: HD, cubes: [cube(ux, 1.4, -1.35, 2.7, 3.8, 2.7, 'fabric', FLAT)] }));
    parts.push(P('x_csl' + side[0], side + 'armlower', V, { hd: HD, cubes: [
      cube(lx, -0.2, -2.85, 3.0, 3.6, 3.7, 'fabric', FLAT),
      cube(lx - 0.1, 3.3, -2.95, 3.2, 1.3, 3.9, 'fabric', { ramp: 'ruff', lift: 0.3, folds: 1, foldAmt: 0.1, hemRamp: 'gold', hem: 0.24, top: 0.24 }),
    ] }));
  }
  // tails: three panels per side around the back, each following the leg on its side, so the coat splits as she walks
  for (const deg of [65, 105, 145, 215, 255, 295]) {
    const phi = deg * PI / 180, sn = Math.sin(phi), cs = Math.cos(phi);
    const left = sn >= 0, sg = left ? 1 : -1, front = deg === 65 || deg === 295;
    parts.push(P('x_ctail' + deg, left ? 'leftleg' : 'rightleg', V, { hd: HD, group: 'coat', ang: phi,
      pivot: [4.0 * sn - sg * 2, 0.6, -0.3 - 3.0 * cs], rot: [-0.07, -phi, 0], sway: [0.01, 0, 0.008, 0.06, deg / 60], phys: { p: 22, z: 0.35, g: 0.5 },
      cubes: [cube(-1.9, 0, -0.4, 3.8, 11.0, 0.8, 'fabric', { ...(front ? EDGE : FLAT), hemRamp: 'gold', hem: 0.05 })] }));
  }

  // ---- shoes: plain black
  for (const side of ['left', 'right']) {
    parts.push(P('x_shoe' + side[0], side + 'leglower', V, { hd: HD, cubes: [cube(-2.2, 5.0, -4.2, 4.4, 2.2, 6.6, 'fabric', { ...FLAT, hemRamp: 'gold', top: 0.2, glowTrim: 0.25 })] }));
  }

  // ---- wings
  parts.push(...featherWing(V, 1), ...featherWing(V, -1));

  return { parts, hide: ['hornbase', 'halo', 'leftwing', 'rightwing', 'tail1', 'skirt1', 'chest', 'hair1'], tweak: { bodytop_female: [0.97, 1, 0.97], rightarm: [1.12, 1, 1.12], leftarm: [1.12, 1, 1.12] } };
}

module.exports = { angel };
