// Extra parts for the Gaia-model variants. Units are model pixels: y is DOWN, -z is the front, +x is her left.
// A part: { name, parent, variants, pivot, rot, cubes:[{box:[x,y,z,w,h,d], mat, o, inflate}], follow?, sway? }
// Rotation reminders (model space, standard maths): on a part that extends UP (-y) a positive z rotation leans it toward her left (+x);
// negative x rotation leans an up-pointing part toward the back (+z); y rotation of -phi turns a front-facing panel to face outward at phi.
// On a part that hangs DOWN (+y), a negative x rotation flares the bottom outward at the front.
const smooth = require('./smooth');
const R = (v) => Math.max(1, Math.round(v));
const box = (x, y, z, w, h, d) => [x, y, z, R(w), R(h), R(d)];

function chain(prefix, parent, variant, n, seg) {
  const out = [];
  let p = parent;
  for (let i = 0; i < n; i++) {
    const s = seg(i);
    const nm = prefix + i;
    out.push({ name: nm, parent: p, variants: [variant], pivot: s.pivot, rot: s.rot, cubes: s.cubes, sway: s.sway });
    p = nm;
  }
  return out;
}

// n panels evenly around a vertical axis, each facing outward; rad = x radius, rz = z radius
function ring(prefix, parent, variant, { n, R: rad, rz, y, cx = 0, cz = 0, off = 0, tilt = 0, cubes, sway }) {
  const out = [];
  for (let i = 0; i < n; i++) {
    const phi = ((360 * i) / n + off) * Math.PI / 180;
    out.push({
      name: prefix + i, parent, variants: [variant],
      pivot: [cx + rad * Math.sin(phi), y, cz - (rz || rad) * Math.cos(phi)], rot: [tilt, -phi, 0], cubes: cubes(i, phi), sway,
    });
  }
  return out;
}

// a hanging skirt of two ruffled layers around the hips; colour(i, sinPhi) -> cloth ramp name
function skirt(variant, { n, len1, len2, tilt1, tilt2, hem, colour, colourB, y = 1.2 }) {
  const sway = [0.05, 0.0, 0.04, 0.4, 0.0];
  const parts = [];
  parts.push(...ring('x_' + variant + '_skirta', 'bodybottom', variant, { n, R: 3.9, rz: 2.7, y, tilt: tilt1, sway,
    cubes: (i, phi) => [{ box: box(-1.7, 0, -0.5, 3.4, len1, 1), mat: 'cloth', o: { ramp: colour(i, Math.sin(phi)), goldHem: hem, trim: 'gold' } }] }));
  parts.push(...ring('x_' + variant + '_skirtb', 'bodybottom', variant, { n, R: 4.2, rz: 3.0, y: y + 1.0, off: 180 / n, tilt: tilt2, sway,
    cubes: (i, phi) => [{ box: box(-1.8, 0, -0.5, 3.6, len2, 1), mat: 'cloth', o: { ramp: (colourB || colour)(i, Math.sin(phi)), goldHem: hem, trim: 'gold' } }] }));
  return parts;
}


// One connected, tapered trunk from under the chest to the belt: three stacked blocks (5.4 -> 4.6 -> 5.8 wide) in one material, so the waist reads as a single piece.
function trunk(V, mat, o) {
  const mk = (name, y, w, h, d) => ({ name, parent: 'bodymiddle', variants: [V], pivot: [0, 0, 0], rot: [0, 0, 0], cubes: [{ box: [-w / 2, y, -0.5 - d / 2, w, h, d], mat, o: o || {} }] });
  return [mk('x_trunk0', -4.2, 5.4, 2, 3.4), mk('x_trunk1', -2.2, 4.6, 2, 3.0), mk('x_trunk2', -0.2, 5.8, 2, 3.8)];
}

// ---------------------------------------------------------------------------------------------------------- JESTER
function jester() {
  const V = 'jester';
  const HAT = { a: 'cloth', b: 'purple', stitch: true };
  const parts = [];
  parts.push({ name: 'x_hatband', parent: 'head', variants: [V], pivot: [0, 0, 0], rot: [0, 0, 0],
    cubes: [{ box: box(-4.5, -9, -4.5, 9, 2, 9), mat: 'split', o: { ...HAT, band: 0.0, trim: 'gold' } }] });
  // two curling points: a long one to her left and a shorter one to her right, each with a gold bell
  const point = (prefix, side, N, L, base, lean, curl, sway) => {
    const w = (i) => R(base * Math.pow(1 - i / (N + 1), 1.2) + 1);
    parts.push(...chain(prefix, 'head', V, N, (i) => ({
      pivot: i === 0 ? [side * 1.9, -9.6, 0] : [0, -(L - 1.2), 0],
      rot: i === 0 ? [-0.05, 0, side * lean] : [-0.035, 0, side * (curl + i * 0.012)],
      cubes: [{ box: box(-w(i) / 2, -L, -w(i) / 2, w(i), L + 1.5, w(i)), mat: 'split', o: HAT }],
      sway: sway,
    })));
    parts.push({ name: prefix + 'bell', parent: prefix + (N - 1), variants: [V], pivot: [0, -(L - 0.6), 0], rot: [0, 0, 0],
      cubes: [{ box: box(-1.5, -2, -1.5, 3, 3, 3), mat: 'gold', o: { glowK: 0.8 } }], sway });
  };
  parts.push({ name: 'x_hatdome', parent: 'head', variants: [V], pivot: [0, 0, 0], rot: [0, 0, 0],
    cubes: [{ box: box(-4, -11.5, -4, 8, 3, 8), mat: 'split', o: HAT }] });
  point('x_hat', 1, 6, 2.6, 6.2, 0.95, 0.27, [0.03, 0.015, 0.07, 0.13, 0.0]);
  point('x_hatb', -1, 5, 2.4, 5.4, 0.95, 0.27, [0.03, 0.015, 0.07, 0.15, 1.7]);
  // layered ruff under the chin, wrist ruffles
  parts.push(...ring('x_ruffa', 'neck', V, { n: 9, R: 2.7, y: 0.9, tilt: 0.75, cubes: () => [{ box: box(-1.5, -2, -0.5, 3, 2, 1), mat: 'ruff', o: { folds: 2 } }] }));
  parts.push(...ring('x_ruffb', 'neck', V, { n: 9, R: 3.5, y: 1.9, off: 20, tilt: 1.05, cubes: () => [{ box: box(-1.5, -2, -0.5, 3, 2, 1), mat: 'ruff', o: { folds: 2 } }] }));
  for (const side of ['right', 'left']) {
    parts.push(...ring('x_cuff' + side[0], side + 'armlower', V, { n: 6, R: 1.7, y: 5.4, cx: side === 'right' ? -0.3 : 0.3, cz: -1, tilt: -0.85,
      cubes: () => [{ box: box(-1, 0, -0.5, 2, 2, 1), mat: 'ruff', o: { folds: 2 } }] }));
  }
  // short motley tutu: purple on her left, black on her right, gold frill
  parts.push(...skirt(V, { n: 9, len1: 5, len2: 4, tilt1: -0.85, tilt2: -1.2, hem: 0.3, colourB: () => 'ruff',
    colour: (i, s) => (s > 0.15 ? 'purple' : s < -0.15 ? 'cloth' : (i % 2 ? 'purple' : 'cloth')) }));
  // curled shoes with bells
  for (const side of ['right', 'left']) {
    parts.push({ name: 'x_toe' + side[0], parent: side + 'leglower', variants: [V], pivot: [0, 6.3, -1.8], rot: [-0.45, 0, 0],
      cubes: [{ box: box(-1.5, -1, -2.5, 3, 2, 3), mat: 'cloth', o: { ramp: 'purple' } }, { box: box(-1, -2, -3.5, 2, 2, 2), mat: 'gold', o: { glowK: 0.6 } }] });
  }
  parts.push(...trunk(V, 'split', { a: 'cloth', b: 'purple', stitch: true }));
  const sh = smooth.smoothHead(V);
  smooth.reparent(parts, V, 'head', 'x_head');
  parts.unshift(...sh.parts);
  return { parts, hide: ['hornbase', 'halo', 'skirt1', 'tail1', 'leftwing', 'rightwing', 'chest', ...sh.hide], tweak: { rightarm: [1.12, 1, 1.12], leftarm: [1.12, 1, 1.12], bodytop: [1.0, 0.93, 1.0] } };
}

// ---------------------------------------------------------------------------------------------------------- DEMON
// Gothic succubus: dark violet hair, flat silver slab horns, bare pale arms with armbands, grey crop top, short navy skirt,
// thigh-high checkered boots, a big black bat wing and a thin tail.
function demon() {
  const V = 'demon';
  const parts = [];
  for (const [side, sg] of [['l', 1], ['r', -1]]) {
    parts.push(...chain('x_horn' + side, 'head', V, 3, (i) => ({
      pivot: i === 0 ? [sg * 2.6, -6.5, -0.4] : [0, -2.6, 0],
      rot: i === 0 ? [-0.95, 0, sg * 0.55] : [-0.35 + i * 0.1, 0, sg * 0.1],
      cubes: [{ box: box(-2, -3, -1, 4 - i * 0.4, 3, 2), mat: 'horn', o: { ramp: 'horn' } }],
    })));
  }
  parts.push(...skirt(V, { n: 10, len1: 3, len2: 2.5, tilt1: -0.45, tilt2: -0.8, hem: 0.0, colour: () => 'navy' }));
  parts.push({ name: 'x_buckle', parent: 'bodybottom', variants: [V], pivot: [0, 1.5, -2.5], rot: [0, 0, 0], cubes: [{ box: box(-1, -1, -1, 2, 2, 1), mat: 'gold', o: {} }] });
  for (const [side, sg] of [['right', -1], ['left', 1]]) {
    // thigh-high boots
    parts.push({ name: 'x_thigh' + side[0], parent: side + 'leg', variants: [V], pivot: [0, 0, 0], rot: [0, 0, 0],
      cubes: [{ box: box(-2.05, 1.6, -2.05, 4, 4, 4), mat: 'checker', o: { a: 'cloth', b: 'greyd', ck: 2 } }, { box: box(-2.15, 1.2, -2.15, 4.3, 1, 4.3), mat: 'cloth', o: { ramp: 'cloth' } }] });
    parts.push({ name: 'x_boot' + side[0], parent: side + 'leglower', variants: [V], pivot: [0, 0, 0], rot: [0, 0, 0],
      cubes: [{ box: box(-2.1, 0.4, -2.1, 4, 6.5, 4), mat: 'checker', o: { a: 'cloth', b: 'greyb', ck: 2 } }, { box: box(-2.2, 4.6, -3.2, 4.4, 2.3, 1.2), mat: 'cloth', o: { ramp: 'cloth' } }] });
    // armband above the elbow
    parts.push({ name: 'x_band' + side[0], parent: side + 'arm', variants: [V], pivot: [0, 0, 0], rot: [0, 0, 0],
      cubes: [{ box: box(sg < 0 ? -2.7 : -0.4, 1.4, -1.7, 3, 2, 3), mat: 'cloth', o: { ramp: 'cloth' } }] });
  }
  // big black bat wings that flap like the Gaia ones
  const ws = [['l', 1.5, 0.65, 'leftwing', true], ['r', -1.5, -0.65, 'rightwing', false]];
  for (const [side, px, ry, follow, flip] of ws) {
    parts.push({ name: 'x_wing' + side, parent: 'bodytop', variants: [V], pivot: [px, -5, 1.6], rot: [0.4363, ry, 0], follow,
      cubes: [{ box: [0, -2, 0, 0, 20, 14], mat: 'batwing', o: { glowVeins: true, flipU: flip, concave: 0.38 }, share: 'demon_wing' }] });
  }
  parts.push(...trunk(V, 'skin', {}));
  return { parts, hide: ['hornbase', 'halo', 'leftwing', 'rightwing', 'skirt1', 'chest'], tweak: { bodytop_female: [0.95, 1, 0.96], rightarm: [0.94, 1.03, 0.94], leftarm: [0.94, 1.03, 0.94] } };
}

// ---------------------------------------------------------------------------------------------------------- ANGEL (nun)
// Built after the user's nun reference: floor-length black habit with a gold-embroidered centre panel and gold hem, a blue-and-gold woven sash
// with hanging ends, white shoulder pads, wide black sleeves with white cuffs and gold stripes, long cream front locks under a black veil with a
// white headband, big winged gold temple ornaments, an octagon halo, a cross on a chain, cream wings, a white sock and shoe on one leg and a black shoe on the other.
function angel() {
  const V = 'angel';
  const parts = [];
  // veil: cap, white headband with a gold jewel, side drapes
  parts.push({ name: 'x_veil', parent: 'head', variants: [V], pivot: [0, 0, 0], rot: [0, 0, 0],
    cubes: [{ box: box(-4.2, -8.4, -4.2, 8, 3, 8), mat: 'cloth', o: { ramp: 'cloth', goldHem: 0.0 } },
      { box: box(-4, -6.0, -4.2, 8, 1, 1), mat: 'cloth', o: { ramp: 'ruff' } },
      { box: box(-1, -6.9, -4.5, 2, 2, 1), mat: 'gold', o: { glowK: 0.4 } }] });
  for (const [s, sg] of [['l', 1], ['r', -1]]) {
    parts.push({ name: 'x_drape' + s, parent: 'head', variants: [V], pivot: [sg * 3.9, -7.0, 0.5], rot: [0, 0, sg * -0.06], sway: [0.03, 0.0, 0.04, 0.08, 0.0],
      cubes: [{ box: box(-0.5, 0, -3.2, 1, 11, 6), mat: 'cloth', o: { ramp: 'cloth', goldHem: 0.12, trim: 'gold' } }] });
    // long cream locks framing the face down over her chest
    parts.push({ name: 'x_lock' + s, parent: 'head', variants: [V], pivot: [sg * 3.8, -0.6, -2.4], rot: [0.04, 0, sg * 0.03],
      cubes: [{ box: box(-1, 0, -1, 2, 10, 2), mat: 'hair', o: { tip: true } }] });
    // big winged gold-and-cream ornament at each temple
    parts.push({ name: 'x_temple' + s, parent: 'head', variants: [V], pivot: [sg * 4.2, -5.8, -1.4], rot: [0, sg * 0.55, sg * -0.5],
      cubes: [{ box: [0, -4, -1, 0, 8, 7], mat: 'featherwing', o: { n: 4, flipU: sg < 0 }, share: 'nun_temple' + s }] });
  }
  // octagonal halo
  parts.push({ name: 'x_halo', parent: 'head', variants: [V], pivot: [0, -11.4, 0.3], rot: [0.12, 0, 0], cubes: [], sway: [0.03, 0.15, 0.03, 0.06, 0.0] });
  parts.push(...ring('x_halos', 'x_halo', V, { n: 8, R: 5.0, y: 0, off: 22.5, cubes: () => [{ box: box(-2.1, -0.6, -0.6, 4.2, 1, 1), mat: 'gold', o: { glowK: 0.55 } }] }));
  // cross on a chain
  parts.push({ name: 'x_chain', parent: 'bodytop_female', variants: [V], pivot: [0, -5.6, -3.0], rot: [0, 0, 0],
    cubes: [0, 1, 2, 3].flatMap((k) => [
      { box: box(-2.6 + 0.5 * k, k * 0.9, -1, 1, 1, 1), mat: 'gold', o: {} },
      { box: box(1.6 - 0.5 * k, k * 0.9, -1, 1, 1, 1), mat: 'gold', o: {} },
    ]) });
  parts.push({ name: 'x_cross', parent: 'bodytop_female', variants: [V], pivot: [0, -2.4, -3.0], rot: [0, 0, 0],
    cubes: [{ box: box(-0.5, -1, -1, 1, 4, 1), mat: 'gold', o: {} }, { box: box(-1.5, 0, -1, 3, 1, 1), mat: 'gold', o: {} }] });
  // blue-and-gold woven sash with hanging ends
  parts.push({ name: 'x_sash', parent: 'bodymiddle', variants: [V], pivot: [0, 0, 0], rot: [0, 0, 0],
    cubes: [{ box: box(-2.7, -2.4, -2.2, 5, 3, 3), mat: 'checker', o: { a: 'azure', b: 'gold', ck: 2 } }] });
  parts.push({ name: 'x_sashtail', parent: 'bodybottom', variants: [V], pivot: [1.6, 0.4, -2.5], rot: [-0.04, 0, 0.08], sway: [0.05, 0.0, 0.04, 0.1, 0.5],
    cubes: [{ box: box(-1.2, 0, -0.5, 2.4, 9, 1), mat: 'cloth', o: { ramp: 'azure', trim: 'gold', goldHem: 0.18, goldLeft: 0.12, goldRight: 0.12 } }] });
  // white shoulder pads, wide sleeves with white cuffs and a gold stripe
  for (const [side, sg] of [['right', -1], ['left', 1]]) {
    parts.push({ name: 'x_pad' + side[0], parent: side + 'arm', variants: [V], pivot: [0, 0, 0], rot: [0, 0, 0],
      cubes: [{ box: box(sg < 0 ? -3.5 : -1.5, -2.7, -2.7, 5, 2, 5), mat: 'cloth', o: { ramp: 'ruff', trim: 'gold', goldHem: 0.0 } }] });
    parts.push({ name: 'x_sleeve' + side[0], parent: side + 'armlower', variants: [V], pivot: [0, 0, 0], rot: [0, 0, 0],
      cubes: [{ box: box(sg < 0 ? -2.6 : -1.4, 1.5, -3.4, 4, 4, 4), mat: 'cloth', o: { ramp: 'cloth', goldHem: 0.3, trim: 'ruff' } },
        { box: box(sg < 0 ? -2.6 : -1.4, 3.0, -3.4, 4, 1, 4), mat: 'gold', o: {}, inflate: 0.2 }] });
  }
  // floor-length habit: each panel follows the leg on its side so the robe splits as she walks; the front and back centre seams are gold-edged
  const NP = 12;
  for (let i = 0; i < NP; i++) {
    const phi = ((360 * i) / NP + 15) * Math.PI / 180;
    const sn = Math.sin(phi), cs = Math.cos(phi);
    const left = sn >= 0, sg = left ? 1 : -1;
    const nearSeam = Math.abs(cs) > 0.9;
    parts.push({ name: 'x_robe' + i, parent: left ? 'leftleg' : 'rightleg', variants: [V],
      pivot: [3.9 * sn - sg * 2, 0.8, -0.5 - 2.7 * cs], rot: [-0.06, -phi, 0],
      cubes: [{ box: box(-1.9, 0, -0.5, 3.8, 12, 1), mat: 'cloth',
        o: { ramp: 'cloth', goldHem: 0.1, trim: 'gold', ...(nearSeam ? { goldLeft: 0.16, goldRight: 0.16, emb: true } : {}) } }] });
  }
  // white sock on her left, black stocking (texture) on her right; mismatched white and black shoes
  parts.push({ name: 'x_sockl', parent: 'leftleg', variants: [V], pivot: [0, 0, 0], rot: [0, 0, 0],
    cubes: [{ box: box(-2.05, 1.4, -2.05, 4, 5, 4), mat: 'cloth', o: { ramp: 'ruff', trim: 'gold', goldTop: 0.2 } }] });
  parts.push({ name: 'x_sockll', parent: 'leftleglower', variants: [V], pivot: [0, 0, 0], rot: [0, 0, 0],
    cubes: [{ box: box(-2.05, 0.0, -2.05, 4, 6, 4), mat: 'cloth', o: { ramp: 'ruff' } }] });
  parts.push({ name: 'x_shoel', parent: 'leftleglower', variants: [V], pivot: [0, 0, 0], rot: [0, 0, 0],
    cubes: [{ box: box(-2.2, 5.2, -3.8, 4, 2, 6), mat: 'cloth', o: { ramp: 'ruff', trim: 'gold', goldTop: 0.3 } }] });
  parts.push({ name: 'x_shoer', parent: 'rightleglower', variants: [V], pivot: [0, 0, 0], rot: [0, 0, 0],
    cubes: [{ box: box(-2.2, 5.2, -3.8, 4, 2, 6), mat: 'cloth', o: { ramp: 'cloth', trim: 'gold', goldTop: 0.3 } }] });
  // cream feathered wings
  const ws = [['l', 1.5, 0.65, 'leftwing', true], ['r', -1.5, -0.65, 'rightwing', false]];
  for (const [side, px, ry, follow, flip] of ws) {
    parts.push({ name: 'x_awing' + side, parent: 'bodytop', variants: [V], pivot: [px, -5, 1.6], rot: [0.4363, ry, 0], follow,
      cubes: [{ box: [0, -2, 0, 0, 25, 17], mat: 'featherwing', o: { n: 6, flipU: flip }, share: 'angel_wing_a' }] });
    parts.push({ name: 'x_awingb' + side, parent: 'x_awing' + side, variants: [V], pivot: [(side === 'l' ? 1 : -1) * 0.5, 1.5, 1.0], rot: [0, 0, (side === 'l' ? 1 : -1) * 0.10],
      cubes: [{ box: [0, -1, 0, 0, 20, 14], mat: 'featherwing', o: { n: 5, flipU: flip }, share: 'angel_wing_b' }] });
  }
  parts.push(...trunk(V, 'cloth', { ramp: 'cloth' }));
  return { parts, hide: ['hornbase', 'halo', 'leftwing', 'rightwing', 'tail1', 'skirt1', 'chest'], tweak: { bodytop_female: [0.97, 1, 0.97] } };
}

// ---------------------------------------------------------------------------------------------------------- GAIA (the original dragon)
// Same look and skin; the bust is removed and a small filler tapers the waist.
function gaia() {
  const V = 'gaia';
  const parts = [];
  parts.push(...trunk(V, 'cloth', { ramp: 'cloth' }));
  return { parts, hide: ['chest'], tweak: {} };
}

module.exports = { jester, demon, angel, gaia };
