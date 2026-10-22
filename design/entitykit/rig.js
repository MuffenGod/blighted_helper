// The shared Emily body rig (YSM proportions, 136+ bones). Every variant is this rig plus knobs (V) and part overrides (V.parts).
// Bone names are the contract with animations (anim.js) and the Java side (hand_item_r / hand_item_l, ov_blink, ov_grin).
// Parts run in this order, which also fixes the bone order:
//   skeleton, hips, torso, head, ears, horns, hair, arms, legs, gown, tail, wings, extras
const HW = 7.2, HH = 7.0, HD = 6.8;

const parts = {};

parts.skeleton = (kit) => {
  kit.bone('root', null, [0, 0, 0]);
  kit.bone('allbody', 'root', [0, 16.5, 0]);   // hips pivot
  kit.bone('upbody', 'allbody', [0, 2.2, 0]);  // waist pivot
};

parts.hips = (kit, V) => {
  const F = V.fat || 0;
  const R = V.ramp, { SIDES } = kit, BM = V.matBody || 'cloth', BO = V.bodyOpts || {};
  kit.cube('allbody', [-2.8 - F * 0.55, -1.0, -1.85 - F * 0.25], [5.6 + F * 1.1, 2.6, 3.7 + F * 0.5], BM, { s: 6, ramp: R.body, ...BO });
  for (const [n, sg] of SIDES) kit.cm('allbody', sg, [0.8, -0.9, -1.9 - F * 0.25], [2.5 + F * 0.7, 2.7, 3.8 + F * 0.5], BM, { s: 6, rot: [0, 0, -15], ramp: R.body, ...BO });
  kit.cube('allbody', [-3.1, 1.35, -1.98], [6.2, 0.45, 3.96], 'gold', { s: 6, ramp: R.trim });
};

parts.torso = (kit, V) => {
  const F = V.fat || 0;
  const R = V.ramp, { SIDES } = kit, BM = V.matBody || 'cloth', BO = V.bodyOpts || {};
  kit.cube('upbody', [-2.4, -0.3, -1.7], [4.8, 3.7, 3.4], BM, { s: 6, emb: true, ramp: R.body, ...BO, trim: R.trim });
  kit.cube('upbody', [-2.7 - F * 0.45, 3.2, -1.75 - F * 0.2], [5.4 + F * 0.9, 2.6, 3.5 + F * 0.4], BM, { s: 6, ramp: R.body, ...BO });
  kit.cube('upbody', [-2.65, 5.5, -1.65], [5.3, 1.8, 3.3], 'skin', { s: 6, cleavage: true });
  for (const [n, sg] of SIDES) {
    kit.cm('upbody', sg, [1.65, 0.7, -1.7], [1.25, 5.2, 3.4], BM, { s: 6, rot: [0, 0, -14], ramp: R.body, ...BO });
    kit.cm('upbody', sg, [2.1, 6.2, -1.55], [1.9, 1.1, 3.1], 'skin', { s: 6, rot: [0, 0, 22] });
  }
  kit.bone('bust', 'upbody', [0, 5.4, -1.5]);
  kit.cube('bust', [-2.55, -0.55, -1.42], [5.1, 1.55, 1.95], 'skin', { s: 8, cleavage: true, rot: [-36, 0, 0] });
  for (const [n, sg] of SIDES) {
    kit.cm('bust', sg, [0.05, -2.05 - F * 0.1, -1.72 - F * 0.35], [2.62 + F * 0.3, 1.95 + F * 0.25, 2.15 + F * 0.35], BM, { s: 8, goldTop: 0.14, rot: [-6, sg * 6, 0], ramp: R.body, ...BO, trim: R.trim });
    kit.cm('bust', sg, [0.08, -2.48, -1.38], [2.52, 1.05, 1.85], BM, { s: 6, rot: [52, sg * 4, 0], ramp: R.body, ...BO });
  }
  if (V.has.pendant) {
    kit.cube('bust', [-0.34, -1.08, -1.88], [0.68, 0.80, 0.32], 'gold', { s: 8, rot: [0, 0, 45], ramp: R.trim });
    kit.cube('bust', [-0.22, -0.96, -1.94], [0.44, 0.56, 0.32], 'gem', { s: 8, glow: true, rot: [0, 0, 45] });
  }
  kit.cube('upbody', [-1.25, 7.0, -1.15], [2.5, 2.1, 2.3], 'skin', { s: 6 });
  if (V.has.choker) {
    kit.cube('upbody', [-1.35, 7.3, -1.25], [2.7, 0.80, 2.5], 'scale', { s: 6 });
    kit.cube('upbody', [-1.38, 7.22, -1.28], [2.76, 0.18, 2.56], 'gold', { s: 6, ramp: R.trim });
    kit.cube('upbody', [-1.38, 8.02, -1.28], [2.76, 0.18, 2.56], 'gold', { s: 6, ramp: R.trim });
    kit.cube('upbody', [-0.26, 7.38, -1.36], [0.52, 0.65, 0.28], 'gem', { s: 8, glow: true });
  }
};

parts.head = (kit, V) => {
  const R = V.ramp, { SIDES } = kit;
  kit.bone('m_head', 'upbody', [0, 8.6, 0]);
  kit.bone('head', 'm_head', [0, 0, 0]);
  kit.cube('head', [-HW / 2, 0.0, -HD / 2], [HW, HH, HD], 'headface', { s: 12, skip: ['up'] });
  for (const [n, sg] of SIDES) {
    kit.cm('head', sg, [1.3, -0.15, -HD / 2 + 0.15], [2.3, 1.5, HD - 0.5], 'skin', { s: 6, rot: [0, 0, -8] });
    kit.cm('head', sg, [0.58, 3.62, -HD / 2 - 0.07], [2.25, 0.32, 0.12], 'lash', { s: 8, rot: [0, 0, -2] });
    if (V.has.circlet) kit.cm('head', sg, [0.3, 5.05, -HD / 2 - 0.12], [2.9, 0.24, 0.16], 'gold', { s: 8, rot: [0, 0, -7], ramp: R.trim });
  }
  if (V.has.circlet) {
    kit.cube('head', [-0.36, 4.72, -HD / 2 - 0.22], [0.72, 0.92, 0.24], 'gold', { s: 8, ramp: R.trim });
    kit.cube('head', [-0.24, 4.84, -HD / 2 - 0.28], [0.48, 0.68, 0.24], 'gem', { s: 8, glow: true });
  }
  kit.bone('ov_blink', 'head', [0, 0, 0]);
  kit.cube('ov_blink', [-HW / 2, 1.8, -HD / 2 - 0.09], [HW, 2.2, 0.08], 'ov_blink', { s: 10, skip: ['up', 'down', 'east', 'west', 'south'] });
  kit.bone('ov_grin', 'head', [0, 0, 0]);
  kit.cube('ov_grin', [-1.4, 0.55, -HD / 2 - 0.09], [2.8, 1.1, 0.08], 'ov_grin', { s: 10, skip: ['up', 'down', 'east', 'west', 'south'] });
};

parts.ears = (kit, V) => {
  if (!V.has.ears) return;
  const e = V.ear || {};
  const k = e.len || 1, rot = e.rot || [4, 26, 22];
  kit.pair('ear', 'head', [HW / 2 - 0.2, 3.2, -0.3], rot, (nm, sg) => {
    kit.cm(nm, sg, [0, -0.44, -0.45], [1.25 * k, 0.88, 0.85], 'skin', { s: 6 });
    kit.bone(nm + '2', nm, kit.mrel(sg, [1.15 * k, 0.04, 0]), kit.mr(sg, [0, 8, 8]));
    kit.cm(nm + '2', sg, [0, -0.32, -0.34], [1.10 * k, 0.64, 0.65], 'skin', { s: 6 });
    kit.bone(nm + '3', nm + '2', kit.mrel(sg, [1.00 * k, 0.03, 0]), kit.mr(sg, [0, 6, 5]));
    kit.cm(nm + '3', sg, [0, -0.20, -0.22], [0.85 * k, 0.40, 0.42], 'skin', { s: 6, blushSpot: true });
  });
};

parts.horns = (kit, V) => {
  if (!V.has.horns) return;
  const R = V.ramp;
  kit.pair('horn', 'head', [3.0, HH - 0.2, -0.5], [-12, -10, -34], (nm, sg) => {
    kit.cm(nm, sg, [-1.12, -0.2, -1.12], [2.24, 0.45, 2.24], 'gold', { s: 6, ramp: R.trim });
    kit.cm(nm, sg, [-1.02, 0.2, -1.02], [2.04, 2.6, 2.04], 'horn', { s: 6, goldInlay: true, trim: R.trim });
    kit.bone(nm + '2', nm, [0, 2.5, 0], kit.mr(sg, [4, 0, 20]));
    kit.cm(nm + '2', sg, [-0.84, 0.0, -0.84], [1.68, 2.5, 1.68], 'horn', { s: 6, goldInlay: true, trim: R.trim });
    kit.bone(nm + '3', nm + '2', [0, 2.4, 0], kit.mr(sg, [6, 0, 24]));
    kit.cm(nm + '3', sg, [-0.68, 0.0, -0.68], [1.36, 2.3, 1.36], 'horn', { s: 6 });
    kit.bone(nm + '4', nm + '3', [0, 2.2, 0], kit.mr(sg, [6, 0, 20]));
    kit.cm(nm + '4', sg, [-0.50, 0.0, -0.50], [1.0, 2.0, 1.0], 'horn', { s: 6 });
    kit.bone(nm + '5', nm + '4', [0, 1.9, 0], kit.mr(sg, [4, 0, 12]));
    kit.cm(nm + '5', sg, [-0.32, 0.0, -0.32], [0.64, 1.6, 0.64], 'horn', { s: 6 });
  });
};

parts.hair = (kit, V) => {
  const { SIDES, mrel, mr } = kit;
  kit.cube('head', [-4.1, HH - 0.8, -3.75], [8.2, 1.35, 7.55], 'hair', { s: 6, crown: true });
  kit.cube('head', [-3.7, HH + 0.3, -3.35], [7.4, 0.95, 6.75], 'hair', { s: 6, crown: true });
  kit.cube('head', [-2.9, HH + 1.05, -2.6], [5.8, 0.7, 5.2], 'hair', { s: 6, crown: true });
  kit.cube('head', [-4.2, 0.4, 2.4], [8.4, HH - 0.6, 1.5], 'hair', { s: 6 });
  kit.bone('bang_mid', 'head', [0, 6.85, -HD / 2 - 0.38], [-3, 0, 0]);
  kit.cube('bang_mid', [-0.36, -1.25, -0.24], [0.72, 1.35, 0.52], 'hair', { s: 6, tip: true });
  for (const [n, sg] of SIDES) {
    kit.cm('head', sg, [3.3, 3.2, -3.1], [1.0, 3.6, 6.4], 'hair', { s: 6 });
    kit.cm('head', sg, [3.3, 0.2, 0.2], [1.0, 3.2, 3.2], 'hair', { s: 6 });
    kit.cm('head', sg, [0.05, 5.75, -HD / 2 - 0.36], [3.85, 1.45, 0.82], 'hair', { s: 6, crown: true, rot: [3, 0, 8] });
    const b0 = 'bang_' + n + '0';
    kit.bone(b0, 'head', mrel(sg, [0.65, 6.35, -HD / 2 - 0.38]), mr(sg, [-3, 0, 20]));
    kit.cm(b0, sg, [-0.58, -2.85, -0.32], [1.16, 2.95, 0.68], 'hair', { s: 6, tip: true });
    const b1 = 'bang_' + n + '1';
    kit.bone(b1, 'head', mrel(sg, [1.85, 6.25, -HD / 2 - 0.35]), mr(sg, [-3, 0, 24]));
    kit.cm(b1, sg, [-0.66, -3.35, -0.34], [1.32, 3.45, 0.72], 'hair', { s: 6, tip: true });
    const b2 = 'bang_' + n + '2';
    kit.bone(b2, 'head', mrel(sg, [3.10, 5.75, -HD / 2 - 0.18]), mr(sg, [-2, -5, 11]));
    kit.cm(b2, sg, [-0.65, -3.5, -0.44], [1.30, 3.6, 0.88], 'hair', { s: 6, tip: true });
    [
      [3.55, 4.8, -2.6, 1.50, 4, 4],
      [3.80, 4.4, -1.2, 1.60, 4, 6],
    ].forEach(([lx, ly, lz, w0, segs, zrot], li) => {
      let parent = 'head';
      for (let k = 0; k < segs; k++) {
        const nm = 'lock_' + n + li + '_' + k;
        const rZ = k === 0 ? zrot : (k % 2 === 1 ? -4 : 4);
        const rX = k === 0 ? -3 : (k === 1 ? 3 : 2);
        kit.bone(nm, parent, k === 0 ? mrel(sg, [lx, ly, lz]) : [0, -2.7, 0], mr(sg, [rX, 0, rZ]));
        const w = w0 * (1.0 - k * 0.13);
        kit.cm(nm, sg, [-w / 2, -2.8, -0.5], [w, 2.9, 1.0], 'hair', { s: 6, dark: li === 1 && k >= 2, tip: k === segs - 1 });
        parent = nm;
      }
    });
  }
  if (!V.has.hairBack) return;
  [
    [0.0, 3.8, 5, 3.2, 0, false],
    [2.2, 3.1, 5, 3.0, -5, false],
    [-2.2, 3.1, 5, 3.0, 5, false],
    [3.6, 2.5, 4, 2.4, -9, true],
    [-3.6, 2.5, 4, 2.4, 9, true],
  ].forEach(([bx, w0, segs, bz, zrot, isDark], ci) => {
    let parent = 'head';
    for (let k = 0; k < segs; k++) {
      const nm = 'hb' + ci + '_' + k;
      const rx = k === 0 ? 8 : (k % 2 === 1 ? 5 : -3);
      const rz = k === 0 ? zrot : (k % 2 === 1 ? -zrot * 0.5 : zrot * 0.5);
      kit.bone(nm, parent, k === 0 ? [bx, 5.2, bz] : [0, -3.1, 0], [rx, 0, rz]);
      const w = w0 * (1.0 - k * 0.10);
      kit.cube(nm, [-w / 2, -3.2, -0.55], [w, 3.3, 1.1], 'hair', { s: 6, dark: isDark, tip: k === segs - 1 });
      parent = nm;
    }
  });
};

parts.arms = (kit, V) => {
  const R = V.ramp;
  kit.pair('arm', 'upbody', [3.55, 6.0, 0.0], [0, 0, -7], (nm, sg) => {
    kit.cm(nm, sg, [-1.05, -2.2, -1.15], [2.1, 2.0, 2.3], V.armMat || 'scale', { s: 6, inflate: 0.08 + (V.fat || 0) * 0.22, ...(V.armOpts || {}) });
    kit.cm(nm, sg, [-0.95, -5.2, -1.0], [1.9, 3.2, 2.0], V.upperArmMat || 'skin', { s: 6, ...((V.fat || 0) ? { inflate: V.fat * 0.2 } : {}), ...(V.upperArmOpts || {}) });
    kit.cm(nm, sg, [-1.0, -5.3, -1.05], [2.0, 1.8, 2.1], V.armMat || 'scale', { s: 6, ...(V.armOpts || {}) });
    kit.bone(nm + '2', nm, [0, -5.1, 0], [-8, 0, 0]);
    kit.cm(nm + '2', sg, [-0.92, -4.6, -0.95], [1.84, 4.7, 1.9], 'glove', { s: 6, goldCuff: true, ramp: R.glove, trim: R.trim, ...((V.fat || 0) ? { inflate: V.fat * 0.14 } : {}), ...(V.gloveOpts || {}) });
    kit.bone(nm + '3', nm + '2', [0, -4.5, 0]);
    kit.cm(nm + '3', sg, [-0.82, -1.8, -0.85], [1.64, 1.9, 1.7], 'glove', { s: 6, ramp: R.glove, ...(V.gloveOpts && V.gloveOpts.hand || {}) });
  });
  kit.bone('hand_item_r', 'arm_r3', [0, -1.2, -0.3]);
  kit.bone('hand_item_l', 'arm_l3', [0, -1.2, -0.3]);
};

parts.legs = (kit, V) => {
  const R = V.ramp;
  const L = V.leg || {};
  kit.pair('leg', 'allbody', [1.70, 0.0, 0.0], null, (nm, sg) => {
    const m = L.mat || 'skin', mo = L.opts || { blushSpot: true }, mo2 = L.opts2 || {};
    kit.cm(nm, sg, [-1.36, -7.2, -1.46], [2.72, 7.2, 2.92], m, { s: 6, ...((V.fat || 0) ? { inflate: V.fat * 0.34 } : {}), ...mo });
    kit.cm(nm, sg, [-1.20, -7.5, -1.50], [2.40, 1.4, 1.4], m, { s: 6, rot: [22, 0, 0], ...((V.fat || 0) ? { inflate: V.fat * 0.3 } : {}), ...mo2 });
    if (sg > 0 && L.garter !== false) kit.cm(nm, sg, [-1.40, -3.4, -1.52], [2.80, 0.36, 3.04], 'gold', { s: 6, ramp: R.trim });
    kit.bone(nm + '2', nm, [0, -7.2, 0.1]);
    kit.cm(nm + '2', sg, [-1.26, -6.8, -1.36], [2.52, 6.8, 2.68], L.lowerMat || m, { s: 6, ...((V.fat || 0) ? { inflate: V.fat * 0.22 } : {}), ...(L.lowerOpts || mo2) });
    kit.cm(nm + '2', sg, [-1.32, -6.6, -1.42], [2.64, 0.42, 2.80], 'gold', { s: 6, ramp: R.trim });
    kit.bone(nm + '3', nm + '2', [0, -6.8, -0.1]);
    kit.cm(nm + '3', sg, [-1.28, -2.5, -2.6], [2.56, 1.1, 3.9], 'shoe', { s: 6, goldSole: true, ramp: R.shoe, trim: R.trim });
    kit.cm(nm + '3', sg, [-1.24, -1.6, -2.0], [2.48, 1.5, 2.5], 'shoe', { s: 6, rot: [24, 0, 0], ramp: R.shoe });
    kit.cm(nm + '3', sg, [-0.55, -2.5, 0.55], [1.1, 2.3, 0.85], 'gold', { s: 6, ramp: R.trim });
  });
};

// floor-length gown: N tangent panels around the hips, each 3 chained segments flaring outward; panel 1 is the front-left slit
parts.gown = (kit, V) => {
  const G = V.gown || {};
  const NP = G.NP || 12, RX = G.RX || 3.25, RZ = G.RZ || 2.15;
  const H_SEGS = G.H_SEGS || [4.9, 5.2, 5.5], W_SEGS = G.W_SEGS || [2.45, 3.05, 3.75], FLARE = G.FLARE || [-5, -6, -7];
  const slit = G.slit !== false;
  const R = V.ramp;
  for (let i = 0; i < NP; i++) {
    const rad = (i * 2 * Math.PI) / NP;
    const px = RX * Math.sin(rad), pz = -RZ * Math.cos(rad);
    const tangentPhi = +(Math.atan2(RZ * Math.sin(rad), RX * Math.cos(rad)) * 180 / Math.PI).toFixed(2);
    const isSlitOpen = slit && i === 1;
    const maxK = isSlitOpen ? 1 : 3;
    let parent = 'allbody';
    for (let k = 0; k < maxK; k++) {
      const nm = 'sk' + i + '_' + k;
      const segH = isSlitOpen ? 1.1 : H_SEGS[k];
      const fullW = W_SEGS[k];
      let ox = -fullW / 2, segW = fullW;
      if (slit && i === 0) { ox = -fullW / 2; segW = (fullW / 2) + 0.72; }
      else if (slit && i === 2) { ox = -0.62; segW = (fullW / 2) + 0.62; }
      if (k === 0) kit.bone(nm, parent, [px, 0.6, pz], [FLARE[0], tangentPhi, 0]);
      else kit.bone(nm, parent, [0, -H_SEGS[k - 1] + 0.2, 0], [FLARE[k], 0, 0]);
      const spec = G.panel ? G.panel(i, k, { isSlitOpen, segH, segW, ox }) : null;
      if (spec) kit.cube(nm, [ox, -segH, -0.35], [segW, segH, 0.7], spec.mat, spec.opts);
      else kit.cube(nm, [ox, -segH, -0.35], [segW, segH, 0.7], 'cloth', {
        s: 6,
        goldHem: (!isSlitOpen && k === 2) ? 0.09 : (isSlitOpen ? 0.22 : 0),
        goldLeft: (slit && i === 0) ? 0.10 : 0,
        goldRight: (slit && i === 2) ? 0.10 : 0,
        emb: (i === 0 || i === 11 || i === 2) && k >= 1,
        ramp: R.body, trim: R.trim,
      });
      parent = nm;
    }
  }
};

parts.tail = (kit, V) => {
  if (!V.has.tail) return;
  const T = V.tail || {};
  const TW = T.TW || [2.8, 2.5, 2.2, 1.9, 1.6, 1.3, 1.0];
  const TL = T.TL || [3.6, 3.6, 3.5, 3.4, 3.2, 3.0, 2.8];
  const TROT = T.TROT || [-42, -16, 8, 16, 18, 14, 10];
  let parent = 'allbody';
  for (let k = 0; k < 7; k++) {
    const nm = 'tail' + (k + 1);
    kit.bone(nm, parent, k === 0 ? [0, 0.5, 2.1] : [0, 0, TL[k - 1] - 0.3], [TROT[k], 0, 0]);
    kit.cube(nm, [-TW[k] / 2, -TW[k] / 2, 0], [TW[k], TW[k], TL[k]], 'scale', { s: 6, ...(T.opts || {}) });
    if (T.spikes && k < T.spikes) {             // a dorsal spike per segment
      kit.cube(nm, [-0.35, TW[k] / 2 - 0.1, 0.4], [0.7, 1.3 + (T.spikes - k) * 0.25, TL[k] * 0.55], 'horn', { s: 6, rot: [-14, 0, 0], ramp: 'horn' });
    }
    parent = nm;
  }
  kit.bone('tail_tip', 'tail7', [0, 0, 2.6]);
  if (T.tip) T.tip(kit);
  else kit.cube('tail_tip', [-0.9, -0.25, 0.0], [1.8, 0.5, 2.0], 'gold', { s: 6, rot: [0, 45, 0], ramp: V.ramp.trim });
};

parts.wings = (kit, V) => {
  if (!V.has.wings) return;
  const W = V.wing || {};
  const WA = W.WA || [78, 54, 30, 8], WL = W.WL || [13.5, 17.0, 16.0, 13.0], WW = W.WW || [6.2, 6.8, 6.4, 5.4];
  const mo = W.membOpts || {};
  const zs = W.out ? -1 : 1;   // Emily's original fan angles point inward in GeckoLib space; W.out flips them so wings open outward
  for (const [n, sg] of kit.SIDES) {
    kit.bone('wing_' + n, 'upbody', kit.mrel(sg, [2.0, 5.4, 1.9]), kit.mr(sg, W.root || [6, 16, 0]));
    if (W.arm) W.arm(kit, n, sg);
    else {
      kit.cm('wing_' + n, sg, [-0.4, 0.9, -0.5], [6.2, 1.0, 1.0], 'wingBone', { s: 6, rot: [0, 0, 32 * zs] });
      kit.cm('wing_' + n, sg, [0.0, -9.5, -0.06], [5.2, 11.5, 0.12], 'wingMemb', { s: 6, skip: ['up', 'down', 'east', 'west'], ...mo });
      kit.cm('wing_' + n, sg, [4.7, 3.0, -0.35], [0.65, 2.0, 0.65], 'wingBone', { s: 6, rot: [0, 0, -18 * zs] });
    }
    for (let k = 0; k < 4; k++) {
      const nm = 'wg_' + n + k;
      kit.bone(nm, 'wing_' + n, kit.mrel(sg, [5.0, 3.0, 0.0]), kit.mr(sg, [0, 0, WA[k] * zs]));
      if (W.finger) W.finger(kit, nm, sg, k, { WL: WL[k], WW: WW[k] });
      else {
        let ww = WW[k], mk = mo;
        if (W.wedge) {   // membrane card is a wedge that fills the gap to the next finger
          const d = WA[k] - (k < 3 ? WA[k + 1] : WA[k] - 24);
          const tw = WL[k] * Math.tan((d * Math.PI) / 180);
          ww = tw + 1.0; mk = { ...mo, wedge: tw / ww, side: sg };
        }
        kit.cm(nm, sg, [-ww + 0.3, -WL[k], -0.06], [ww, WL[k], 0.12], 'wingMemb', { s: W.s || 6, skip: ['up', 'down', 'east', 'west'], ...mk });
        kit.cm(nm, sg, [-0.32, -WL[k], -0.32], [W.rib || 0.64, WL[k], W.rib || 0.64], 'wingBone', { s: 6 });
      }
    }
  }
};

parts.extras = () => {};

const ORDER = ['skeleton', 'hips', 'torso', 'head', 'ears', 'horns', 'hair', 'arms', 'legs', 'gown', 'tail', 'wings', 'extras'];

function defaults(V) {
  return {
    ...V,
    ramp: { body: 'cloth', glove: 'cloth', shoe: 'cloth', trim: 'gold', ...(V.ramp || {}) },
    has: { circlet: true, ears: true, horns: true, hairBack: true, tail: true, wings: true, choker: true, pendant: true, ...(V.has || {}) },
  };
}

function build(kit, V0) {
  const V = defaults(V0);
  const p = { ...parts, ...(V.parts || {}) };
  for (const name of ORDER) p[name](kit, V, parts[name]);
  return V;
}

module.exports = { build, parts, HW, HH, HD };
