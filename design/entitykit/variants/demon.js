// Infernal: the demon Emily. Huge curling ram horns, big ember-orange bat wings on thick bone ribs, a spiked spade tail,
// armoured crop top with a bare midriff, spiked pauldrons and bracers, plated tassets, greaves. Ash skin, black hair with ember streaks,
// glowing amber eyes. Reference: the user's voxel demon girl (ram horns, orange-membraned wings, dark armour with a crimson stripe).
const { hex } = require('../paint');
const { HH } = require('../rig');
const E = require('./emily').PAL;

const PAL = {
  ...E,
  skinHi: hex('#f8e4d8'), skinMid: hex('#eecbba'), skinShad: hex('#d1a08d'), skinDeep: hex('#a96c5d'), blush: hex('#dc6a78'),
  hairHi: hex('#a65c70'), hairLit: hex('#6e3e50'), hairMid: hex('#3e2636'), hairShad: hex('#24141f'), hairDeep: hex('#120912'),
  hairStreak: hex('#f0522c'), hairStreakAll: true, streakT: 0.9,
  clothHi: hex('#36323f'), clothMid: hex('#201d28'), clothDark: hex('#15131b'), clothDeep: hex('#0a090e'),
  crimsonHi: hex('#e8465e'), crimsonMid: hex('#aa1630'), crimsonDark: hex('#6e0c20'), crimsonDeep: hex('#3a0712'),
  armorHi: hex('#74707f'), armorMid: hex('#3d3a49'), armorDark: hex('#26242f'), armorDeep: hex('#121119'),
  hornHi: hex('#d2c4b2'), hornMid: hex('#8e8074'), hornDark: hex('#5c524a'), hornDeep: hex('#2b2521'),
  scaleHi: hex('#62343f'), scaleMid: hex('#301b26'), scaleDark: hex('#1b1017'), scaleDeep: hex('#0b0508'),
  goldHi: hex('#ffd890'), goldMid: hex('#d9962e'), goldShad: hex('#8f5a1a'), goldDeep: hex('#4a2a0a'),
  gemHi: hex('#fff2c4'), gemMid: hex('#ffb42e'), gemShad: hex('#c46c0c'), gemDeep: hex('#4a2204'),
  wingBone: hex('#1c1319'), wingHi: hex('#ffa042'), wingMid: hex('#c85a1c'), wingDark: hex('#702612'), wingDeep: hex('#2c0f09'), wingHot: hex('#ffd278'),
  ember: hex('#ff7a1c'), lash: hex('#140a12'), lip: hex('#951a32'), brow: hex('#14090f'),
};

const FACE = { pupil: 'slit', pupilCol: [30, 8, 0], wing: true, fang: true, blush: 0.28, browTilt: 0.07, browThick: 0.02, smile: 0.012, tilt: 0.09 };

const ARM = { edge: 'crimson', rivets: true };

function spikeRing(kit, bone, y, n, R, len, mat, opts) {
  kit.ring(bone + '_sp', bone, { n, R, y, cubeFn: (nm) => kit.cube(nm, [-0.3, 0, -0.3], [0.6, len, 0.6], mat, { s: 6, rot: [-35, 0, 0], ...opts }) });
}

const parts = {
  torso(kit, V) {
    const { SIDES } = kit;
    // bare midriff, a corset belt, a cropped armoured top
    kit.cube('upbody', [-2.4, -0.3, -1.7], [4.8, 3.7, 3.4], 'skin', { s: 6, abs: true });
    kit.cube('upbody', [-2.62, -0.45, -1.82], [5.24, 1.05, 3.64], 'armor', { s: 6, edge: 'crimson', edgeAt: 'bottom', rivets: true });
    kit.cube('upbody', [-0.95, -0.35, -1.98], [1.9, 1.15, 0.34], 'armor', { s: 8, rune: true, edge: 'crimson' });
    kit.cube('upbody', [-2.7, 3.3, -1.75], [5.4, 2.5, 3.5], 'armor', { s: 6, edge: 'crimson', edgeAt: 'bottom' });
    kit.cube('upbody', [-2.65, 5.5, -1.65], [5.3, 1.8, 3.3], 'skin', { s: 6, cleavage: true });
    for (const [n, sg] of SIDES) {
      kit.cm('upbody', sg, [1.65, 0.7, -1.7], [1.25, 5.2, 3.4], 'cloth', { s: 6, rot: [0, 0, -14], ramp: 'cloth' });
      kit.cm('upbody', sg, [2.1, 6.2, -1.55], [1.9, 1.1, 3.1], 'skin', { s: 6, rot: [0, 0, 22] });
    }
    kit.bone('bust', 'upbody', [0, 5.4, -1.5]);
    kit.cube('bust', [-2.55, -0.55, -1.42], [5.1, 1.55, 1.95], 'skin', { s: 8, cleavage: true, rot: [-36, 0, 0] });
    for (const [n, sg] of SIDES) {
      kit.cm('bust', sg, [0.05, -2.05, -1.72], [2.62, 1.95, 2.15], 'armor', { s: 8, rot: [-6, sg * 6, 0], edge: 'crimson', edgeAt: 'top' });
      kit.cm('bust', sg, [0.08, -2.48, -1.38], [2.52, 1.05, 1.85], 'armor', { s: 6, rot: [52, sg * 4, 0] });
    }
    kit.cube('bust', [-0.34, -1.08, -1.9], [0.68, 0.8, 0.34], 'gold', { s: 8, rot: [0, 0, 45] });
    kit.cube('bust', [-0.22, -0.96, -1.96], [0.44, 0.56, 0.34], 'gem', { s: 8, rot: [0, 0, 45] });
    kit.cube('upbody', [-1.25, 7.0, -1.15], [2.5, 2.1, 2.3], 'skin', { s: 6 });
    // spiked collar
    kit.cube('upbody', [-1.45, 7.25, -1.35], [2.9, 0.9, 2.7], 'armor', { s: 6, edge: 'crimson', edgeAt: 'bottom' });
    kit.ring('collar', 'upbody', { n: 7, R: 1.55, y: 8.0, off: 20, cubeFn: (nm) => kit.cube(nm, [-0.25, 0, -0.25], [0.5, 1.4, 0.5], 'horn', { s: 6, rot: [-26, 0, 0] }) });
  },

  gown(kit, V) {
    const NP = 10, RX = 3.3, RZ = 2.2;
    const H = [2.9, 2.6, 2.3], W = [2.7, 2.9, 3.0], FL = [-9, -12, -15];
    for (let i = 0; i < NP; i++) {
      const rad = (i * 2 * Math.PI) / NP;
      const px = RX * Math.sin(rad), pz = -RZ * Math.cos(rad);
      const tp = +(Math.atan2(RZ * Math.sin(rad), RX * Math.cos(rad)) * 180 / Math.PI).toFixed(2);
      let parent = 'allbody';
      for (let k = 0; k < 3; k++) {
        const nm = 'sk' + i + '_' + k;
        if (k === 0) kit.bone(nm, parent, [px, 0.6, pz], [FL[0], tp, 0]);
        else kit.bone(nm, parent, [0, -H[k - 1] + 0.2, 0], [FL[k], 0, 0]);
        if (k === 0) kit.cube(nm, [-W[k] / 2, -H[k], -0.4], [W[k], H[k], 0.8], 'armor', { s: 6, edge: 'crimson', edgeAt: 'bottom', rivets: i % 2 === 0 });
        else if (k === 1) kit.cube(nm, [-W[k] / 2, -H[k], -0.35], [W[k], H[k], 0.7], 'cloth', { s: 6, ramp: 'cloth' });
        else kit.cube(nm, [-W[k] / 2, -H[k], -0.35], [W[k], H[k], 0.7], 'cloth', { s: 6, ramp: 'cloth', goldHem: 0.2, trim: 'crimson', hemWaves: 6 });
        parent = nm;
      }
    }
  },

  horns(kit) {
    // huge ram horns: nine chained ridged segments that sweep up-out-back, then curl down around the ear
    const N = 10, L = 2.5;
    for (const [n, sg] of kit.SIDES) {
      const names = kit.chain('horn_' + n, 'head', N, (k) => {
        const w = 3.3 - k * 0.3;
        return {
          rel: k === 0 ? kit.mrel(sg, [3.0, HH - 0.9, -0.3]) : [0, L - 0.3, 0],
          rot: k === 0 ? kit.mr(sg, [-14, -4, 52]) : kit.mr(sg, [-4.5, 0, 13.5]),
          cubes: [{ o: [-w / 2, 0, -w / 2], sz: [w, L, w], mat: 'ramhorn', opts: { s: 6, ring: 2.0, tipGlow: k === N - 1 ? 0.55 : 0 } }],
        };
      });
      kit.cube(names[0], [-1.7, -0.5, -1.7], [3.4, 0.9, 3.4], 'armor', { s: 6, edge: 'crimson', rivets: true });   // armoured horn collar
    }
  },

  extras(kit, V) {
    const { SIDES } = kit;
    for (const [n, sg] of SIDES) {
      // pauldrons with three swept spikes
      kit.cm('arm_' + n, sg, [-2.0, -0.7, -2.0], [4.0, 1.5, 4.0], 'armor', { s: 6, edge: 'crimson', rivets: true });
      kit.cm('arm_' + n, sg, [-1.6, 0.7, -1.6], [3.2, 0.9, 3.2], 'armor', { s: 6, edge: 'crimson' });
      [[-0.9, 0, 30], [0.2, 0, 0], [0.9, 0, -20]].forEach(([dx, dz, rz], i) => {
        kit.cm('arm_' + n, sg, [0.9 + dx * 0.6 + i * 0.35, 1.4, -0.3], [0.9, 2.6 - i * 0.4, 0.9], 'horn', { s: 6, rot: [0, 0, 24 + i * 10] });
      });
      // bracers with a spike
      kit.cm('arm_' + n + '2', sg, [-1.2, -4.6, -1.22], [2.4, 3.3, 2.44], 'armor', { s: 6, edge: 'crimson', edgeAt: 'top', rivets: true });
      kit.cm('arm_' + n + '2', sg, [1.0, -3.2, -0.3], [0.7, 2.3, 0.7], 'horn', { s: 6, rot: [0, 0, 42] });
      // greaves and spiked knees
      kit.cm('leg_' + n + '2', sg, [-1.5, -6.2, -1.6], [3.0, 5.6, 3.2], 'armor', { s: 6, edge: 'crimson', edgeAt: 'bottom', rivets: true });
      kit.cm('leg_' + n + '2', sg, [-1.55, -0.6, -2.0], [3.1, 1.6, 1.2], 'armor', { s: 6, edge: 'crimson' });
      kit.cm('leg_' + n + '2', sg, [-0.35, -0.2, -3.0], [0.7, 0.7, 1.9], 'horn', { s: 6, rot: [18, 0, 0] });
      // boot: pointed toe and a heel spur
      kit.cm('leg_' + n + '3', sg, [-0.45, -0.9, 0.9], [0.9, 0.9, 2.0], 'horn', { s: 6, rot: [-30, 0, 0] });
      kit.cm('leg_' + n + '3', sg, [-0.55, -2.0, -4.4], [1.1, 0.9, 1.9], 'armor', { s: 6, edge: 'crimson' });
    }
    // tattered rags hanging from the hips behind the tail
    kit.bone('rag', 'allbody', [0, 1.0, 2.4], [8, 0, 0]);
    kit.cube('rag', [-2.2, -6.5, -0.2], [4.4, 6.5, 0.4], 'cloth', { s: 6, ramp: 'cloth', goldHem: 0.16, trim: 'crimson', hemWaves: 5 });
    kit.addSway({ bones: ['rag'], rot: [3.5, 0.6, 1.2], phase: 0.5, grow: 0 });
  },
};

module.exports = {
  id: 'demon',
  PAL,
  swatches: { armor: PAL.armorDark, ramhorn: PAL.hornMid, horn: PAL.hornMid },
  bounds: { w: 9, h: 6.5, off: [0, 2.0, 0] },
  V: {
    face: FACE,
    fat: 1,
    has: { horns: true, choker: false, circlet: true, pendant: false },
    ear: { len: 1.5, rot: [8, 22, 20] },
    ramp: { shoe: 'armor', trim: 'crimson', glove: 'armor' },
    gloveOpts: { trim: 'crimson', cuffH: 0.2 },
    armOpts: { ramp: 'scale' },
    leg: { mat: 'cloth', opts: { ramp: 'cloth' }, opts2: { ramp: 'cloth' }, lowerOpts: { ramp: 'cloth' }, garter: false },
    tail: {
      TW: [3.0, 2.7, 2.4, 2.1, 1.8, 1.5, 1.2], TL: [3.8, 3.8, 3.7, 3.6, 3.4, 3.2, 3.0], TROT: [-40, -14, 8, 16, 18, 14, 10], spikes: 5,
      tip: (kit) => {
        kit.cube('tail_tip', [-0.3, -1.7, 0.0], [0.6, 3.4, 3.6], 'cloth', { s: 6, ramp: 'crimson', rot: [45, 0, 0] });
        kit.cube('tail_tip', [-1.7, -0.3, 0.0], [3.4, 0.6, 3.6], 'cloth', { s: 6, ramp: 'crimson', rot: [0, 45, 0] });
      },
    },
    wing: {
      out: true, wedge: true, WA: [84, 62, 40, 18], s: 5, WL: [14.5, 20, 19, 15.5], WW: [7.4, 8.2, 7.8, 6.6], rib: 0.95, root: [8, 18, 0],
      membOpts: { fireGrad: true, glowVeins: true, scallop: 0.36 },
      arm: (kit, n, sg) => {
        kit.cm('wing_' + n, sg, [-0.5, 0.9, -0.7], [7.4, 1.5, 1.4], 'wingBone', { s: 6, rot: [0, 0, -32] });
        kit.cm('wing_' + n, sg, [0.0, -12.0, -0.06], [6.2, 14.0, 0.12], 'wingMemb', { s: 5, skip: ['up', 'down', 'east', 'west'], fireGrad: true, glowVeins: true, scallop: 0.3 });
        kit.cm('wing_' + n, sg, [5.4, 3.4, -0.5], [1.0, 3.2, 1.0], 'horn', { s: 6, rot: [0, 0, 18] });
      },
    },
    parts,
  },
};
