// Hair builders: a layered cascade of strand chains that replaces Gaia's flat back-hair slab (hide hair1 for the variants that use it).
const { P, cube, chain } = require('./kit');

// cols x layers strand chains hanging from the back of the head. Each strand: n segments, tapering, swaying with its own phase.
// o: { parent, ramp, tipRamp, cols, layers:[{z,len,n}], width (total span), y0, w }
function hairCascade(V, o = {}) {
  const parent = o.parent || 'neck', cols = o.cols || 5, span = o.span || 7.6, y0 = o.y0 === undefined ? -5.0 : o.y0;
  const layers = o.layers || [{ z: 2.0, len: 15, n: 5 }, { z: 3.1, len: 18, n: 5 }];
  const parts = [];
  layers.forEach((L, li) => {
    for (let c = 0; c < cols; c++) {
      const u = cols === 1 ? 0 : (c / (cols - 1)) * 2 - 1, x = u * span / 2;
      const VAR = [1.0, 0.8, 0.94, 0.72, 1.0, 0.86, 0.96];
      const len = L.len * (1 - 0.16 * Math.abs(u)) * VAR[(c + 3 * li) % VAR.length];
      parts.push(...chain((o.prefix || 'x_hc') + li + '_' + c + '_', parent, V, L.n, { pivot: [x, y0 + 0.5 * li, L.z], rot: [0.16 + 0.04 * li, 0, u * 0.1] }, (i, t) => ({
        rot: [0.015, 0, u * 0.02 * (i ? 1 : 0)], len: len / L.n, w: (o.w || 2.2) * (1 - 0.3 * t), d: 1.5 * (1 - 0.2 * t), over: 0.4, mat: 'strand',
        o: { ramp: o.ramp || 'hair', dir: 1, taper: 0.55, ...(o.tipRamp && i >= L.n - 2 ? { tipRamp: o.tipRamp } : {}) },
        sway: [0.04 + 0.01 * i, 0.0, 0.03, 0.06 + 0.01 * (c % 3), i * 0.5 + c * 0.9 + li * 1.6], phys: { gc: 0.55, p: 17 + 2 * li },
      }), { dir: 1, hd: 1.5 }));
    }
  });
  return parts;
}

// a hanging pigtail from the side of the head
function pigtail(V, s, o = {}) {
  const n = o.n || 6;
  return chain('x_pig' + (s > 0 ? 'l' : 'r'), o.parent || 'head', V, n, { pivot: [s * (o.dx || 4.0), o.y || -4.6, o.z || 0.4], rot: [0.05, 0, s * (o.rz === undefined ? 0.45 : o.rz)] }, (i, t) => ({
    rot: [0.03, 0, s * (i === 0 ? 0 : -0.1)], len: (o.len || 3.4), w: (o.w || 2.8) * (1 - 0.5 * t) + 0.5, d: 2.0 * (1 - 0.4 * t) + 0.4, over: 0.4, mat: 'strand',
    o: { ramp: o.ramp || 'hair', dir: 1, taper: 0.6, ...(o.tipRamp && i >= n - 2 ? { tipRamp: o.tipRamp } : {}) }, sway: [0.07, 0.0, 0.06, 0.07, i * 0.55 + (s > 0 ? 0 : 1.2)], phys: { gc: 0.5, p: 18 },
  }), { dir: 1, hd: 1.5 });
}

module.exports = { hairCascade, pigtail };
