// GAIA — the Dragoness. Gold hair and circlet, black-and-gold armoured gown, swept black dragon horns tipped in gold, crimson dragon wings with ember veins,
// a ridged spiked tail with a flame tip, gold pauldrons / bracers / tassets / greaves. Her face, hair crown and gown texture are the original.
const { P, cube, chain, ring, PI } = require('./kit');
const { batWing } = require('./lib_wings');

function dragonHorn(V, s) {
  const n = 5, name = 'x_dhorn' + (s > 0 ? 'l' : 'r');
  const parts = [];
  parts.push(P(name + '_base', 'head', V, { pivot: [s * 2.8, -6.0, -0.9], rot: [-0.5, 0, s * 0.3], hd: 1.5, cubes: [
    cube(-1.6, -1.4, -1.6, 3.2, 1.8, 3.2, 'metal', { ramp: 'gold', bevel: 1, rivets: true, glow: 0.2 })] }));
  parts.push(...chain(name, 'head', V, n, { pivot: [s * 2.8, -6.8, -0.9], rot: [-0.85, s * 0.1, s * 0.34] }, (i, t) => ({
    rot: [-0.2, 0, s * 0.03], len: 2.9 * (1 - 0.1 * t), w: 2.8 * (1 - 0.7 * t) + 0.5, d: 2.5 * (1 - 0.7 * t) + 0.5, over: 0.5,
    mat: 'horn2', o: { ramp: 'horn', ring: 5, dir: -1, crack: true, ...(i === n - 1 ? { tipGlow: [255, 200, 90], tipFrac: 0.4 } : {}), ...(i === n - 2 ? { tipGlow: [255, 180, 70], tipFrac: 0.85 } : {}) },
    sway: [0.005, 0, 0.005, 0.05, i * 0.4],
  }), { dir: -1, hd: 1.5 }));
  return parts;
}

function gaia() {
  const V = 'gaia';
  const parts = [];

  // ---- horns, wings, tail
  parts.push(...dragonHorn(V, 1), ...dragonHorn(V, -1));
  const WO = { ramp: 'wingweb', bone: 'wingbone', glowVein: [255, 150, 60], knuckle: 'gold' };
  parts.push(...batWing(V, 1, { ...WO, prefix: 'x_gwl' }), ...batWing(V, -1, { ...WO, prefix: 'x_gwr' }));
  parts.push(...chain('x_gtail', 'bodybottom', V, 8, { pivot: [0, 1.0, 2.0], rot: [0.95, 0, 0] }, (i, t) => ({
    rot: [i === 0 ? 0 : 0.0 + 0.1 * t, 0, 0], len: 2.7, w: 2.6 * (1 - 0.62 * t) + 0.5, d: 2.3 * (1 - 0.62 * t) + 0.5, over: 0.5,
    mat: 'horn2', o: { ramp: i % 3 === 2 ? 'gold' : 'horn', ring: 3, dir: 1 }, sway: [0.03, 0.04, 0.0, 0.07, i * 0.45],
    extraCubes: i % 2 === 0 ? [cube(-0.4, 0.2, 1.0 + (2.3 * (1 - 0.62 * t)) / 2 - 0.5, 0.8, 1.6, 0.8, 'horn2', { ramp: 'gold', ring: 2, dir: 1 })] : [],
  }), { dir: 1, hd: 1.5 }));
  parts.push(P('x_gtailflame', 'x_gtail7', V, { pivot: [0, 2.8, 0], sway: [0.04, 0.05, 0.0, 0.1, 4.0], cubes: [
    cube(-2.6, -0.3, 0, 5.2, 6.4, 0, 'spade', { ramp: 'ember', glow: [255, 140, 40], taperTop: 0.4, split: 0.5 }),
    cube(0, -0.3, -2.6, 0, 6.4, 5.2, 'spade', { ramp: 'ember', glow: [255, 140, 40], taperTop: 0.4, split: 0.5 })] }));

  // ---- collar and chest emblem (no shoulder plates or wrist cuffs: her sleeves and gloves are the original ones)
  parts.push(P('x_collar', 'neck', V, { hd: 1.5, cubes: [cube(-2.1, 0.0, -2.0, 4.2, 1.2, 4.0, 'metal', { ramp: 'gold', bevel: 1, rivets: true, glow: 0.2 })] }));
  parts.push(P('x_emblem', 'bodytop_female', V, { pivot: [0, -3.3, -3.2], hd: 1.5, cubes: [
    cube(-1.7, -1.5, -0.3, 3.4, 3.2, 0.6, 'metal', { ramp: 'gold', bevel: 1, glow: 0.25 }),
    cube(-0.9, -0.8, -0.55, 1.8, 1.8, 0.6, 'gem', { ramp: 'gem', glow: 0.9 })] }));
  // ---- greaves: a narrow gilded shin guard over the boot
  for (const [side, sg] of [['right', -1], ['left', 1]]) {
    parts.push(P('x_greave' + side[0], side + 'leglower', V, { hd: 1.5, cubes: [
      cube(-1.0, 0.6, -2.2, 2.0, 3.6, 0.7, 'metal', { ramp: 'gold', bevel: 1, ridge: true, glow: 0.15 }),
      cube(-2.15, 5.0, -4.0, 4.3, 2.2, 6.4, 'metal', { ramp: 'cloth', bevel: 1, line: 'h' })] }));
  }

  return { parts, hide: ['hornbase', 'leftwing', 'rightwing', 'tail1', 'chest'], tweak: {} };
}

module.exports = { gaia };
