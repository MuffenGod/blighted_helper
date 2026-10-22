// High-density painters for the premium helper variants. A painter fills one face rectangle of a cube:
//   painter(tex, {x,y,w,h}, face, {mat, o (cube options), glow (Tex), pal})
// faces: north = front (-z), south = back, east/west = the sides, up/down = ends. Cubes are thin plates / rods, so most painters care about north+south.
// Everything is resolution independent (u,v in 0..1 plus the pixel size) so the same painter works at 1x, 2x or 4x.
const { hash } = require('../raster');
const { mixc, clamp } = require('../paint');

const smooth = (a, b, x) => { const t = clamp((x - a) / (b - a), 0, 1); return t * t * (3 - 2 * t); };
const BAYER = [[0, 8, 2, 10], [12, 4, 14, 6], [3, 11, 1, 9], [15, 7, 13, 5]];
const bay = (x, y) => (BAYER[y & 3][x & 3] + 0.5) / 16;
const mulc = (c, k) => [clamp(Math.round(c[0] * k), 0, 255), clamp(Math.round(c[1] * k), 0, 255), clamp(Math.round(c[2] * k), 0, 255)];
const addc = (c, d) => [clamp(Math.round(c[0] + d), 0, 255), clamp(Math.round(c[1] + d), 0, 255), clamp(Math.round(c[2] + d), 0, 255)];
const FACE_K = { up: 1.04, north: 1.0, east: 0.9, west: 0.9, south: 0.9, down: 0.72 };

function makeHD(PAL) {
  const R = (n) => ({ hi: PAL[n + 'Hi'], mid: PAL[n + 'Mid'], dark: PAL[n + 'Dark'] || PAL[n + 'Shad'], deep: PAL[n + 'Deep'] });
  // 4-stop ramp sampled at v in 0..1 with ordered dithering so bands read as pixel art rather than smooth gradients
  const ramp4 = (Rm, v, x, y, d = 0.5) => {
    const stops = [Rm.deep, Rm.dark, Rm.mid, Rm.hi];
    const s = clamp(v, 0, 1) * 3 + (bay(x, y) - 0.5) * d, i = clamp(Math.floor(s), 0, 2);
    return stops[clamp(Math.round(s - i) + i, 0, 3)];
  };
  const smoothRamp = (Rm, v) => { const s = clamp(v, 0, 1) * 3, i = Math.min(2, Math.floor(s)); return mixc([Rm.deep, Rm.dark, Rm.mid, Rm.hi][i], [Rm.deep, Rm.dark, Rm.mid, Rm.hi][i + 1], s - i); };
  const clear = (t, r) => { for (let y = 0; y < r.h; y++) for (let x = 0; x < r.w; x++) t.set(r.x + x, r.y + y, [0, 0, 0, 0]); };
  const fill = (t, r, fn) => { for (let y = 0; y < r.h; y++) for (let x = 0; x < r.w; x++) { const c = fn(x, y, (x + 0.5) / r.w, (y + 0.5) / r.h); t.set(r.x + x, r.y + y, c ? (c.length === 4 ? c : [...c, 255]) : [0, 0, 0, 0]); } };
  const P = {};
  const H = { R, ramp4, smoothRamp, clear, fill, smooth, bay, mulc, addc, FACE_K };

  // ------------------------------------------------------------------ feather: a barbed vane around a centre shaft, pointed tip, gold-dusted end
  // o: { ramp, dir (+1 root at top of the face, -1 root at the bottom), tip (ramp name for the tip dust), seed, round (0 pointed .. 1 rounded), curl }
  P.feather = (t, r, f, ctx) => {
    const o = ctx.o || {}, Rm = R(o.ramp || 'feat'), Tp = o.tip ? R(o.tip) : null, dir = o.dir === undefined ? 1 : o.dir, seed = o.seed || 0;
    if (f === 'down') return clear(t, r);
    if (f === 'up') return fill(t, r, () => mixc(Rm.dark, Rm.deep, 0.4));
    if (f === 'east' || f === 'west') return fill(t, r, (x, y, u, v) => { const a = dir > 0 ? v : 1 - v; const lim = (0.5 + 0.5 * smooth(0.0, 0.18, a)) * (1 - Math.pow(smooth(0.55, 0.985, a), 1.6)); return lim > 0.8 ? mixc(Rm.mid, Rm.dark, 0.5) : null; });
    const W = r.w, Hh = r.h, back = f === 'south';
    const mid = (W - 1) / 2;
    fill(t, r, (x, y, u, v) => {
      const a = dir > 0 ? v : 1 - v;                          // 0 at the root, 1 at the tip
      const xi = (back ? (W - 1 - x) : x) - mid, ax = Math.abs(xi) / Math.max(0.5, mid + 0.5);   // 0 on the shaft .. ~1 at the edge
      const lean = (o.curl || 0) * a * a * (W / 2);
      const xs = xi - lean, axs = Math.abs(xs) / Math.max(0.5, mid + 0.5);
      const rd = o.round === undefined ? 0.5 : o.round;
      const lim = (0.5 + 0.5 * smooth(0.0, 0.18, a)) * (1 - Math.pow(smooth(0.55, 0.985, a), 1.1 + rd * 0.9));
      if (lim < 0.09 || axs > lim + 0.02 || a > 0.985) return null;
      const edge = lim - axs;
      const along = (dir > 0 ? y : Hh - 1 - y);
      // barbs: crisp diagonal lines sweeping toward the tip
      const barb = (((along - Math.round(Math.abs(xs) * 1.5) + seed) % 5) + 5) % 5 === 0;
      let v0 = 1.02 - 0.34 * axs - 0.08 * a + (xs < 0 ? 0.06 : -0.05);
      if (barb) v0 -= 0.17;
      let col = ramp4(Rm, v0, x, y, 0.25);
      if (Math.abs(xs) < 0.6) col = mixc(Rm.dark, Rm.mid, 0.25 + 0.2 * (1 - a));          // the quill
      else if (Math.abs(xs) < 1.6 && xs < 0) col = mixc(col, Rm.hi, 0.5);                  // lit side of the quill
      if (edge < 0.5 / W + 0.06 && a > 0.1) col = mixc(col, Rm.dark, 0.7);                 // thin outline
      if (Tp && a > 0.78) col = mixc(col, ramp4(Tp, 0.62 + 0.4 * (1 - axs) - (a - 0.78) * 1.4, x, y, 0.4), smooth(0.78, 0.95, a) * 0.9);
      if (o.selfGlow && ctx.glow) ctx.glow.set(r.x + x, r.y + y, [...mulc(col, o.selfGlow), 255]);
      if (o.glowTip && ctx.glow && a > 0.86) ctx.glow.set(r.x + x, r.y + y, [...mulc(o.glowTip, 0.5 * smooth(0.86, 0.99, a)), 255]);
      return mulc(col, FACE_K[f] || 1);
    });
  };
  // gilded rod / bone: lit left edge, rivet dots, darker underside
  P.wingbone = (t, r, f, ctx) => {
    const o = ctx.o || {}, Rm = R(o.ramp || 'gold');
    fill(t, r, (x, y, u, v) => {
      let k = 0.55 + 0.4 * (1 - Math.abs(u - 0.3) * 1.6);
      if (f === 'down' || f === 'up') k = 0.5;
      let c = ramp4(Rm, k, x, y, 0.2);
      if ((f === 'north' || f === 'south') && (y % 7) === 3 && Math.abs(u - 0.5) < 0.3) c = Rm.deep;
      return mulc(c, FACE_K[f] || 1);
    });
  };
  require('./hd_paint_more').add(P, H, PAL);
  return { P, H };
}

module.exports = { makeHD, smooth, bay, mulc, addc };
