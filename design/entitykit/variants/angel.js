// Holy: the angel Emily. A floating gold halo, two large layered-feather wings, a long ivory robe with gold trim and an azure
// sash and tabard, bell sleeves, blue eyes and gold-white hair. No horns, no tail, soft rounded ears.
const { hex } = require('../paint');
const { HH } = require('../rig');
const E = require('./emily').PAL;

const PAL = {
  ...E,
  skinHi: hex('#fff6ef'), skinMid: hex('#ffe9de'), skinShad: hex('#f1c8b6'), skinDeep: hex('#d99a84'), blush: hex('#f8a0a4'),
  hairHi: hex('#fffdf2'), hairLit: hex('#fff1c6'), hairMid: hex('#f6e0a0'), hairShad: hex('#dcbc70'), hairDeep: hex('#b08e44'),
  clothHi: hex('#ffffff'), clothMid: hex('#fffdf6'), clothDark: hex('#eee6d4'), clothDeep: hex('#e2d9c6'),
  azureHi: hex('#c4ecff'), azureMid: hex('#5cb6ea'), azureDark: hex('#2f7bbb'), azureDeep: hex('#183c70'),
  goldHi: hex('#fffadc'), goldMid: hex('#f6d36c'), goldShad: hex('#cc9c36'), goldDeep: hex('#8c6414'),
  gemHi: hex('#ecfcff'), gemMid: hex('#4fcdf5'), gemShad: hex('#1c82bc'), gemDeep: hex('#082c4e'),
  featHi: hex('#ffffff'), featMid: hex('#f4f2fb'), featDark: hex('#d0d2ea'), featDeep: hex('#9fa3cb'), featTip: hex('#f8dc82'),
  wingBone: hex('#e8e2f0'), scaleHi: hex('#ffffff'), scaleMid: hex('#eee9f6'), scaleDark: hex('#d2cde4'), scaleDeep: hex('#a49fc2'),
  lash: hex('#4a3626'), lip: hex('#dd6e82'), brow: hex('#c7a45e'),
};

const FACE = { pupil: 'round', pupilCol: [10, 36, 74], fang: false, blush: 0.6, smile: 0.028, tilt: 0.0, browTilt: 0.0 };
const NF = 3;   // feathers per finger

const parts = {
  gown(kit, V) {
    const NP = 12, RX = 3.3, RZ = 2.2;
    const H = [5.4, 5.8, 6.2], W = [2.6, 3.4, 4.3], FL = [-4, -7, -10];
    for (let i = 0; i < NP; i++) {
      const rad = (i * 2 * Math.PI) / NP;
      const px = RX * Math.sin(rad), pz = -RZ * Math.cos(rad);
      const tp = +(Math.atan2(RZ * Math.sin(rad), RX * Math.cos(rad)) * 180 / Math.PI).toFixed(2);
      let parent = 'allbody';
      for (let k = 0; k < 3; k++) {
        const nm = 'sk' + i + '_' + k;
        if (k === 0) kit.bone(nm, parent, [px, 0.6, pz], [FL[0], tp, 0]);
        else kit.bone(nm, parent, [0, -H[k - 1] + 0.2, 0], [FL[k], 0, 0]);
        kit.cube(nm, [-W[k] / 2, -H[k], -0.35], [W[k], H[k], 0.7], 'cloth', {
          s: 6, ramp: 'cloth', trim: 'gold', goldHem: k === 2 ? 0.1 : 0, emb: i % 3 === 0 && k >= 1, hemWaves: 6,
        });
        parent = nm;
      }
    }
  },

  extras(kit, V) {
    const { SIDES } = kit;
    // ---- halo: a ring of gold segments that bobs above her head
    kit.bone('halo', 'head', [0, HH + 3.4, 0.4], [6, 0, 0]);
    kit.ring('halo_s', 'halo', {
      n: 16, R: 3.7, y: 0,
      cubeFn: (nm) => kit.cube(nm, [-1.0, -0.28, -0.32], [2.0, 0.56, 0.64], 'gold', { s: 8, glowK: 1.0 }),
    });
    kit.ring('halo_i', 'halo', {
      n: 12, R: 2.7, y: 0.05,
      cubeFn: (nm) => kit.cube(nm, [-0.75, -0.12, -0.14], [1.5, 0.24, 0.28], 'gold', { s: 8, glowK: 0.7 }),
    });
    kit.addSway({ bones: ['halo'], rot: [1.2, 6, 1.2], pos: [0, 0.4, 0], phase: 0.3, grow: 0 });
    // ---- azure sash and tabard
    kit.cube('upbody', [-2.95, -0.5, -2.0], [5.9, 1.1, 4.0], 'cloth', { s: 6, ramp: 'azure', trim: 'gold', goldTop: 0.12, goldHem: 0.12 });
    kit.cube('upbody', [-0.5, -0.55, -2.12], [1.0, 1.2, 0.3], 'gem', { s: 8 });
    kit.bone('tabard_f', 'allbody', [0, 1.0, -2.5], [-3, 0, 0]);
    kit.cube('tabard_f', [-1.5, -15.5, -0.3], [3.0, 15.5, 0.6], 'cloth', { s: 6, ramp: 'azure', trim: 'gold', goldHem: 0.08, goldLeft: 0.06, goldRight: 0.06, emb: true });
    kit.bone('tabard_b', 'allbody', [0, 1.0, 2.5], [3, 0, 0]);
    kit.cube('tabard_b', [-1.5, -15.5, -0.3], [3.0, 15.5, 0.6], 'cloth', { s: 6, ramp: 'azure', trim: 'gold', goldHem: 0.08 });
    kit.addSway({ bones: ['tabard_f', 'tabard_b'], rot: [1.6, 0, 0.6], phase: 0.4, grow: 0 });
    for (const [n, sg] of SIDES) {
      // gold shoulder guard
      kit.cm('arm_' + n, sg, [-1.75, -0.5, -1.75], [3.5, 1.1, 3.5], 'gold', { s: 6 });
      kit.cm('arm_' + n, sg, [-0.9, 0.4, -1.0], [1.8, 0.5, 2.0], 'gold', { s: 6 });
      // bell sleeve
      kit.cm('arm_' + n + '2', sg, [-1.65, -4.7, -1.65], [3.3, 3.6, 3.3], 'cloth', { s: 6, ramp: 'cloth', trim: 'gold', goldHem: 0.14, goldTop: 0.08 });
      // sandals with a golden strap
      kit.cm('leg_' + n + '3', sg, [-1.34, -1.7, -1.2], [2.68, 0.35, 2.4], 'gold', { s: 6 });
    }
  },
};

// feather helpers (cards are thin in z; both faces painted)
function featherCard(kit, bone, sg, x, len, w, rotZ, zoff, glowTip) {
  kit.cm(bone, sg, [x - w / 2, -len, -0.07 + zoff], [w, len, 0.12], 'feather', { s: 5, skip: ['up', 'down', 'east', 'west'], rot: [0, 0, rotZ], pivotTop: true, side: sg, glowTip });
}

module.exports = {
  id: 'angel',
  PAL,
  swatches: { feather: [0, 0, 0, 0] },
  bounds: { w: 9, h: 6.5, off: [0, 2.0, 0] },
  V: {
    face: FACE,
    fat: 1,
    has: { horns: false, tail: false, choker: false },
    ear: { len: 0.7, rot: [4, 20, -4] },
    ramp: { shoe: 'gold', trim: 'gold' },
    armMat: 'cloth', armOpts: { ramp: 'cloth', trim: 'gold' },
    gloveOpts: { cuffH: 0.2 },
    leg: { mat: 'skin', opts: { blushSpot: true }, garter: false },
    wing: {
      out: true, WA: [86, 66, 46, 24], WL: [14, 19, 19.5, 16], WW: [5, 5, 5, 5], root: [8, 16, 0],
      arm: (kit, n, sg) => {
        kit.cm('wing_' + n, sg, [-0.5, 0.9, -0.6], [7.2, 1.3, 1.2], 'cloth', { s: 6, ramp: 'cloth', trim: 'gold', rot: [0, 0, -30] });
        // covert feathers along the strut
        for (let i = 0; i < 6; i++) {
          featherCard(kit, 'wing_' + n, sg, 0.6 + i * 1.05, 4.4 - i * 0.25, 2.3, -6 - i * 2, 0.05 + i * 0.012, false);
        }
        featherCard(kit, 'wing_' + n, sg, 4.7, 5.4, 2.6, -8, 0.2, true);
      },
      finger: (kit, nm, sg, k, d) => {
        // NF overlapping primary feathers per finger: long on the outside, shorter and slightly fanned inward
        for (let j = 0; j < NF; j++) {
          const len = d.WL * (1 - j * 0.1);
          featherCard(kit, nm, sg, -j * 1.45 - 0.1, len, 3.4 - j * 0.15, -j * 5.5, 0.05 + (k * 3 + j) * 0.012, j === 0);
        }
      },
    },
    parts,
  },
};
