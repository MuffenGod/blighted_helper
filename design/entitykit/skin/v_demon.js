// DEMON — Infernal Fiend: gothic succubus. Violet hair, big silver ram horns, bare pale arms with spiked bracers, grey crop top with a ruby heart,
// leather harness, layered navy skirt with chains, checkered thigh-high boots, articulated black-violet bat wings and a spaded tail.
const { P, cube, chain, ring, PI } = require('./kit');
const { batWing } = require('./lib_wings');

// A ram horn: a chain of ridged segments that curls up, back and around. side = +1 (her left) / -1.
function ramHorn(V, side, o = {}) {
  const n = o.n || 7, s = side;
  const name = 'x_horn' + (s > 0 ? 'l' : 'r');
  const parts = [];
  parts.push(P(name + '_base', 'head', V, { pivot: [s * 2.7, -6.1, -0.5], hd: 1.5, cubes: [
    cube(-1.5, -1.0, -1.5, 3.0, 1.4, 3.0, 'metal', { ramp: 'silver', bevel: 1, rivets: true })] }));
  parts.push(...chain(name, 'head', V, n, { pivot: [s * 2.7, -7.0, -0.5], rot: [o.rx === undefined ? -0.1 : o.rx, s * (o.ry === undefined ? 0.0 : o.ry), s * (o.rz === undefined ? 0.5 : o.rz)] }, (i, t) => ({
    rot: [-(o.curl || 0.42), 0, s * (o.twist === undefined ? -0.02 : o.twist)], len: (o.len || 2.5) * (1 - 0.1 * t), w: 2.5 * (1 - 0.8 * t) + 0.45, d: 2.2 * (1 - 0.78 * t) + 0.45, over: 0.5,
    mat: 'horn2', o: { ramp: 'horn', ring: 4, dir: -1, crack: true, ...(i === n - 1 ? { tipGlow: [255, 80, 255], tipFrac: 0.5 } : {}) },
    sway: [0.006, 0, 0.006, 0.05, i * 0.4],
  }), { dir: -1, hd: 1.5 }));
  return parts;
}

function demon() {
  const V = 'demon';
  const parts = [];

  // ---- horns, wings, tail
  parts.push(...ramHorn(V, 1), ...ramHorn(V, -1));
  parts.push(...batWing(V, 1, { glowVein: [224, 64, 255] }), ...batWing(V, -1, { glowVein: [224, 64, 255] }));
  parts.push(...chain('x_tail', 'bodybottom', V, 7, { pivot: [0, 1.0, 2.0], rot: [0.9, 0, 0] }, (i, t) => ({
    rot: [i === 0 ? 0 : 0.05 + 0.2 * t, 0, 0], len: 2.7, w: 2.2 * (1 - 0.6 * t) + 0.5, d: 2.0 * (1 - 0.6 * t) + 0.5, over: 0.5,
    mat: 'horn2', o: { ramp: 'wingbone', ring: 3, dir: 1 }, sway: [0.03, 0.04, 0.0, 0.07, i * 0.45], phys: { gc: 0.3, p: 20 },
  }), { dir: 1, hd: 1.5 }));
  parts.push(P('x_tailspade', 'x_tail6', V, { pivot: [0, 2.7, 0], rot: [0, 0, 0], sway: [0.04, 0.05, 0.0, 0.07, 4.2], cubes: [
    cube(-2.6, -0.3, 0, 5.2, 6.2, 0, 'spade', { ramp: 'wingbone', glow: [170, 40, 210], taperTop: 0.45, split: 0.55 }),
    cube(0, -0.3, -2.6, 0, 6.2, 5.2, 'spade', { ramp: 'wingbone', glow: [170, 40, 210], taperTop: 0.45, split: 0.55 })] }));

  // ---- head and neck: ruby heart choker and two chunky twin tails (her own back hair stays)
  parts.push(P('x_choker', 'neck', V, { hd: 1.5, cubes: [cube(-2.0, 0.2, -1.9, 4.0, 1.0, 3.8, 'fabric', { ramp: 'leather', foldAmt: 0.2, folds: 3, top: 0.2, topRamp: 'silver', hem: 0.2, hemRamp: 'silver' }),
    cube(-0.7, 0.4, -2.2, 1.4, 1.4, 0.6, 'gem', { ramp: 'ruby', glow: 0.6 })] }));
  for (const [sd, sg] of [['l', 1], ['r', -1]]) {
    parts.push(...chain('x_tt' + sd, 'head', V, 3, { pivot: [sg * 3.8, -4.4, 1.8], rot: [0.1, 0, sg * 0.2] }, (i, t) => ({
      rot: [0.04, 0, sg * -0.05], len: 5.2, w: 2.8 - 0.5 * t, d: 2.2 - 0.4 * t, over: 0.4, mat: 'strand',
      o: { ramp: 'hair', dir: 1, taper: 0.25, ...(i >= 2 ? { tipRamp: 'ember' } : {}) }, sway: [0.06, 0.0, 0.05, 0.07, i * 0.55 + (sg > 0 ? 0 : 1.1)], phys: { gc: 0.5, p: 18 },
    }), { dir: 1, hd: 1.5 }));
  }

  // ---- chest: just the ruby heart
  parts.push(P('x_heart', 'bodytop_female', V, { pivot: [0, -3.0, -3.25], hd: 1.5, cubes: [
    cube(-1.4, -1.4, -0.4, 2.8, 2.8, 0.9, 'metal', { ramp: 'silver', bevel: 1 }), cube(-0.8, -0.8, -0.55, 1.6, 1.6, 0.6, 'gem', { ramp: 'ruby', glow: 0.55 })] }));

  // ---- arms: slim leather armbands and wrist bands
  for (const [side, sg] of [['right', -1], ['left', 1]]) {
    parts.push(P('x_band' + side[0], side + 'arm', V, { hd: 1.5, cubes: [cube(sg < 0 ? -2.1 : -1.9, 1.2, -1.6, 4.0, 1.2, 3.2, 'fabric', { ramp: 'leather', foldAmt: 0.2, folds: 2, hem: 0.2, hemRamp: 'silver' })] }));
    parts.push(P('x_brac' + side[0], side + 'armlower', V, { hd: 1.5, cubes: [cube(sg < 0 ? -1.85 : -1.05, 4.0, -2.65, 2.9, 1.4, 3.3, 'fabric', { ramp: 'leather', foldAmt: 0.2, folds: 2, top: 0.15, topRamp: 'silver', hem: 0.15, hemRamp: 'silver' })] }));
  }

  // ---- belt and a single short skirt
  parts.push(P('x_belt', 'bodybottom', V, { hd: 1.5, cubes: [cube(-3.9, 0.4, -2.5, 7.8, 1.6, 5.0, 'fabric', { ramp: 'leather', foldAmt: 0.15, folds: 2, top: 0.12, topRamp: 'silver', hem: 0.12, hemRamp: 'silver', outline: true }),
    cube(-1.3, 0.2, -2.9, 2.6, 2.0, 0.7, 'metal', { ramp: 'silver', bevel: 1, rivets: true }), cube(-0.7, 0.6, -3.1, 1.4, 1.2, 0.5, 'gem', { ramp: 'ruby', glow: 0.9 })] }));
  parts.push(...ring('x_sk0_', 'bodybottom', V, { n: 12, R: 4.4, rz: 3.2, y: 1.6, tilt: -0.5, hd: 1.5, group: 'skirt',
    cubes: () => [cube(-1.8, 0, -0.5, 3.6, 5.2, 0.9, 'fabric', { ramp: 'navy', folds: 1, foldAmt: 0.5, lift: 0.05, hem: 0.1, hemRamp: 'silver', outline: true })], sway: [0.05, 0, 0.03, 0.07, 0.0] }));

  // ---- checkered thigh-high boots with heels
  for (const [side, sg] of [['right', -1], ['left', 1]]) {
    parts.push(P('x_thigh' + side[0], side + 'leg', V, { hd: 1.5, cubes: [
      cube(-2.15, 1.0, -2.15, 4.3, 5.2, 4.3, 'fabric', { ramp: 'cloth', ramp2: 'greyd', pat: 'check', pw: 2, folds: 1, foldAmt: 0.2, outline: true }),
      cube(-2.3, 0.6, -2.3, 4.6, 1.0, 4.6, 'fabric', { ramp: 'leather', hem: 0.3, hemRamp: 'silver', foldAmt: 0.1, folds: 3 })] }));
    parts.push(P('x_boot' + side[0], side + 'leglower', V, { hd: 1.5, cubes: [
      cube(-2.25, 0.0, -2.25, 4.5, 5.9, 4.5, 'fabric', { ramp: 'cloth', ramp2: 'greyd', pat: 'check', pw: 2, folds: 1, foldAmt: 0.2, outline: true }),
      cube(-2.3, 5.2, -4.3, 4.6, 2.0, 6.4, 'fabric', { ramp: 'leather', foldAmt: 0.2, folds: 1, top: 0.2, topRamp: 'silver', outline: true }),
      cube(-2.1, 5.9, 1.5, 4.2, 1.4, 1.4, 'metal', { ramp: 'silver', bevel: 1 })] }));
  }

  return { parts, hide: ['hornbase', 'halo', 'leftwing', 'rightwing', 'tail1', 'skirt1', 'chest'], tweak: { bodytop_female: [0.95, 1, 0.96], rightarm: [0.94, 1.03, 0.94], leftarm: [0.94, 1.03, 0.94] } };
}

module.exports = { demon };
