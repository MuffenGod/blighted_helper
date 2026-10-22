// Emily (Gaia, the original dragon knight). Reproduces the shipped emily.geo.json through the kit (regression check).
const { hex } = require('../paint');
const PAL = {
  skinHi: hex('#fff3ea'), skinMid: hex('#fde4d5'), skinShad: hex('#eec1ab'), skinDeep: hex('#d4967e'), blush: hex('#f59494'),
  hairHi: hex('#fff8d8'), hairLit: hex('#fbe49e'), hairMid: hex('#ecc56a'), hairShad: hex('#cfa044'), hairDeep: hex('#a47626'),
  clothHi: hex('#2e2b42'), clothMid: hex('#1b1928'), clothDark: hex('#100f18'), clothDeep: hex('#08070d'),
  scaleHi: hex('#3d425a'), scaleMid: hex('#212434'), scaleDark: hex('#11121c'), scaleDeep: hex('#07080d'),
  goldHi: hex('#fff3b8'), goldMid: hex('#edc35a'), goldShad: hex('#b5842c'), goldDeep: hex('#744e14'),
  gemHi: hex('#e0fff2'), gemMid: hex('#46e896'), gemShad: hex('#12864c'), gemDeep: hex('#05351c'),
  wingBone: hex('#111018'), wingHi: hex('#8a182e'), wingMid: hex('#4d0e1e'), wingDark: hex('#220811'), wingDeep: hex('#0e0409'),
  lash: hex('#1a0e16'), lip: hex('#c84860'),
};
module.exports = { id: 'emily', PAL, V: {}, bounds: { w: 8, h: 5, off: [0, 1.5, 0] } };
