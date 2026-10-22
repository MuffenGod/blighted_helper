// Harlequin: the jester Emily. Purple / black motley, silver hair, red eyes, huge curling belled hat, cream ruff.
// Reference: the user's purple jester voxel girl (white hair, red eyes, one long curled two-tone hat, white ruff, short split dress,
// black thigh-high stockings). The dragon tail and wings stay (harlequin colours); horns give way to the hat.
const { hex } = require('../paint');
const { HH } = require('../rig');
const E = require('./emily').PAL;

const PAL = {
  ...E,
  hairHi: hex('#ffffff'), hairLit: hex('#f2f0fb'), hairMid: hex('#dbd8ea'), hairShad: hex('#b3afcb'), hairDeep: hex('#7d7899'),
  purpleHi: hex('#b08cf0'), purpleMid: hex('#6d40c4'), purpleDark: hex('#4a2a8e'), purpleDeep: hex('#2a1658'),
  ruffHi: hex('#fffcf8'), ruffMid: hex('#f0e8f6'), ruffDark: hex('#cdc0de'), ruffDeep: hex('#8f80ae'),
  scaleHi: hex('#5b40a0'), scaleMid: hex('#2d1c58'), scaleDark: hex('#1a1034'), scaleDeep: hex('#0c0719'),
  gemHi: hex('#ffe6e6'), gemMid: hex('#ff3c4e'), gemShad: hex('#b3162b'), gemDeep: hex('#3d0710'),
  wingBone: hex('#120a22'), wingHi: hex('#8a54e6'), wingMid: hex('#43257f'), wingDark: hex('#1d0e3a'), wingDeep: hex('#0d0618'),
  lip: hex('#d62a4c'), brow: hex('#6c6788'),
};

const FACE = {
  pupil: 'slit', pupilCol: [28, 4, 10], wing: true, fang: true, blush: 0.62,
  // a small red diamond under the left eye
  extra: (u, v, col) => {
    const d = Math.abs(u - 0.752) / 0.034 + Math.abs(v - 0.70) / 0.05;
    if (d < 1) return { col: [214, 30, 52] };
    return null;
  },
};

const HAT = { a: 'cloth', b: 'purple', stitch: true };

function ruffRow(kit, name, parent, o) {
  kit.ring(name, parent, {
    n: o.n, R: o.R, y: o.y, off: o.off || 0,
    cubeFn: (nm) => kit.cube(nm, [-o.w / 2, o.down ? -o.h : -0.2, -0.25], [o.w, o.h, 0.5], 'ruff', { s: 8, rot: [o.tilt, 0, 0], folds: o.folds || 4 }),
  });
}

const parts = {
  extras(kit, V) {
    const { SIDES } = kit;
    // ---- neck ruff: two layered rings of fanned cards
    ruffRow(kit, 'ruffA', 'upbody', { n: 11, R: 2.5, y: 7.0, w: 2.9, h: 2.2, tilt: 44 });
    ruffRow(kit, 'ruffB', 'upbody', { n: 11, R: 3.25, y: 6.2, w: 3.6, h: 2.2, tilt: 60, off: 16 });
    // ---- wrist ruffles
    for (const [n, sg] of SIDES) {
      kit.ring('cuff_' + n, 'arm_' + n + '2', {
        n: 7, R: 1.25, y: -4.1,
        cubeFn: (nm) => kit.cube(nm, [-0.85, -1.5, -0.15], [1.7, 1.5, 0.3], 'ruff', { s: 8, rot: [-48, 0, 0], folds: 3 }),
      });
    }
    // ---- the hat: split band, then a long two-tone point that curls over and hangs with a gold bell
    kit.bone('hat', 'head', [0, 0, 0]);
    kit.cube('hat', [-4.55, HH + 0.1, -4.25], [9.1, 1.9, 8.5], 'split', { ...HAT, s: 6, band: 0.16, trim: 'gold' });
    const W0 = 7.6, N = 12, L = 2.8;
    const names = kit.chain('hatp', 'hat', N, (k) => {
      const w = W0 * Math.pow(1 - k / (N + 1.0), 1.25) + 0.5;
      return {
        rel: k === 0 ? [0.3, HH + 1.9, 0] : [0, L - 0.35, 0],
        rot: k === 0 ? [-3, 0, 24] : [-2.4, 0, 9 + k * 1.4],
        cubes: [{ o: [-w / 2, 0, -w / 2], sz: [w, L, w], mat: 'split', opts: { ...HAT, s: 6 } }],
      };
    });
    kit.bone('hat_bell', names[N - 1], [0, L - 0.2, 0]);
    kit.cube('hat_bell', [-0.9, -0.2, -0.9], [1.8, 1.8, 1.8], 'gold', { s: 8, glowK: 0.7 });
    kit.addSway({ bones: names.concat(['hat_bell']), rot: [1.6, 0.4, 2.6], phase: 0.2, step: 0.55, grow: 0.28 });
    // ---- curled shoes with bells
    for (const [n, sg] of SIDES) {
      const t0 = 'toe_' + n + '0';
      kit.bone(t0, 'leg_' + n + '3', [0, -1.95, -2.5], [-28, 0, 0]);
      kit.cube(t0, [-1.1, -0.55, -1.8], [2.2, 1.1, 1.8], 'shoe', { s: 6, ramp: 'purple', trim: 'gold' });
      const t1 = 'toe_' + n + '1';
      kit.bone(t1, t0, [0, 0.1, -1.7], [-34, 0, 0]);
      kit.cube(t1, [-0.9, -0.45, -1.7], [1.8, 0.9, 1.7], 'shoe', { s: 6, ramp: 'cloth' });
      kit.cube(t1, [-0.6, -0.5, -2.7], [1.2, 1.2, 1.2], 'gold', { s: 8, glowK: 0.7 });
    }
    // anklets of gold bells
    for (const [n, sg] of SIDES) {
      kit.ring('anklet_' + n, 'leg_' + n + '3', {
        n: 5, R: 1.55, Rz: 1.55, y: -0.9, cubeFn: (nm) => kit.cube(nm, [-0.3, -0.3, -0.3], [0.6, 0.6, 0.6], 'gold', { s: 8, glowK: 0.5 }),
      });
    }
  },
};

module.exports = {
  id: 'jester',
  PAL,
  swatches: { split: PAL.purpleMid, ruff: PAL.ruffMid },
  bounds: { w: 8, h: 6.5, off: [0, 2.0, 0] },
  V: {
    face: FACE,
    fat: 1,
    matBody: 'split',
    bodyOpts: { a: 'cloth', b: 'purple', stitch: true },
    has: { horns: false, circlet: true, choker: false },
    ear: { len: 1.15 },
    ramp: { shoe: 'cloth' },
    gloveOpts: { trim: 'ruff', cuffH: 0.16 },
    leg: { mat: 'cloth', opts: { ramp: 'cloth' }, opts2: { ramp: 'cloth' }, lowerOpts: { ramp: 'cloth' }, garter: false },
    gown: {
      NP: 12, RX: 3.25, RZ: 2.15, slit: false,
      H_SEGS: [1.9, 1.9, 1.9], W_SEGS: [2.8, 3.4, 4.0], FLARE: [-16, -22, -28],
      panel: (i, k, g) => {
        const base = { s: 6, goldHem: k === 2 ? 0.14 : 0, trim: 'gold' };
        if (i === 0 || i === 6) return { mat: 'split', opts: { ...HAT, ...base, a: 'cloth', b: 'purple' } };
        return { mat: 'cloth', opts: { ...base, ramp: i >= 1 && i <= 5 ? 'purple' : 'cloth' } };
      },
    },
    tail: {
      tip: (kit) => {
        kit.cube('tail_tip', [-1.3, -0.25, 0.0], [2.6, 0.5, 2.6], 'split', { ...HAT, s: 6, rot: [0, 45, 0] });
        kit.cube('tail_tip', [-0.6, -0.6, 2.4], [1.2, 1.2, 1.2], 'gold', { s: 8, glowK: 0.7 });
      },
    },
    wing: { out: true, wedge: true, WA: [84, 62, 40, 18], WL: [11.5, 14.5, 13.5, 11], root: [6, 16, 0] },
    parts,
  },
};
