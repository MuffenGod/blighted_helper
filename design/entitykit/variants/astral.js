// Astral: the void sorceress. Emily's rig in midnight indigo, silver trim, violet eyes and a cosmic-membrane wing pair, crowned by orbiting stars.
const { hex } = require('../paint');
const { HH } = require('../rig');
const E = require('./emily').PAL;

const PAL = {
  ...E,
  skinHi: hex('#f8f4ff'), skinMid: hex('#eae2fa'), skinShad: hex('#cbc0e8'), skinDeep: hex('#9c8ec6'), blush: hex('#dca4ec'),
  hairHi: hex('#c4b0ff'), hairLit: hex('#8068e8'), hairMid: hex('#4c39ac'), hairShad: hex('#2e2274'), hairDeep: hex('#170f42'),
  clothHi: hex('#40407e'), clothMid: hex('#212150'), clothDark: hex('#141432'), clothDeep: hex('#0a0a1e'),
  scaleHi: hex('#4a4a96'), scaleMid: hex('#252560'), scaleDark: hex('#14143a'), scaleDeep: hex('#08081e'),
  goldHi: hex('#ffffff'), goldMid: hex('#d8def6'), goldShad: hex('#8c96c6'), goldDeep: hex('#4c548a'),
  gemHi: hex('#f8e4ff'), gemMid: hex('#d474ff'), gemShad: hex('#7c26c4'), gemDeep: hex('#2c0c54'),
  wingBone: hex('#16122e'), wingHi: hex('#a070ff'), wingMid: hex('#4c2ca0'), wingDark: hex('#1e1050'), wingDeep: hex('#0a0622'), wingHot: hex('#ff9ef4'),
  ember: hex('#c88cff'), lash: hex('#120a24'), lip: hex('#b0449c'), brow: hex('#2c2074'),
};

const parts = {
  extras(kit) {
    // five four-point stars orbiting above her head
    kit.bone('stars', 'head', [0, HH + 3.6, 0]);
    for (let i = 0; i < 5; i++) {
      const a = (i * 72 * Math.PI) / 180, nm = 'star' + i;
      kit.bone(nm, 'stars', [Math.sin(a) * 3.6, Math.cos(a * 2) * 0.5, -Math.cos(a) * 3.6], [0, 0, 0]);
      kit.cube(nm, [-0.9, -0.15, -0.15], [1.8, 0.3, 0.3], 'gold', { s: 8, glowK: 1.0 });
      kit.cube(nm, [-0.15, -0.9, -0.15], [0.3, 1.8, 0.3], 'gold', { s: 8, glowK: 1.0 });
      kit.cube(nm, [-0.4, -0.4, -0.15], [0.8, 0.8, 0.3], 'gem', { s: 8, rot: [0, 0, 45] });
    }
    kit.addSway({ bones: ['stars'], rot: [0, 14, 0], pos: [0, 0.35, 0], phase: 0.2, grow: 0 });
  },
};

module.exports = {
  id: 'astral',
  PAL,
  swatches: {},
  bounds: { w: 9, h: 6, off: [0, 1.8, 0] },
  V: {
    fat: 1,
    has: { horns: false },
    face: { pupil: 'slit', pupilCol: [20, 6, 40], wing: true },
    ramp: { trim: 'gold' },
    ear: { len: 1.2, rot: [4, 24, -16] },
    wing: { out: true, wedge: true, s: 5, WA: [84, 62, 40, 18], WL: [13, 18, 17, 14], root: [8, 16, 0], membOpts: { glowVeins: true, fireGrad: true, scallop: 0.3 } },
    parts,
  },
};
