// node skin/swatch.js <variant> <mat> <w> <h> <d> [json opts]  -> out/swatch_<mat>.png  (the cube's six faces unfolded, 6x nearest-neighbour, on a grey checker)
const fs = require('fs'), path = require('path');
const { Tex, encodePng } = require('../raster');
const { hex } = require('../paint');
const pals = require('./palettes');
const { makeHD } = require('./hd_paint');
const [, , V, mat, w, h, d, opts] = process.argv;
const PAL = pals[V];
const { P } = makeHD(PAL);
const W = +w, H = +h, D = +d;
const tw = 2 * D + 2 * W + 2, th = D + H + 2;
const t = new Tex(tw, th), glow = new Tex(tw, th);
const [u, v] = [1, 1];
const rects = { west: [u, v + D, D, H], north: [u + D, v + D, W, H], east: [u + D + W, v + D, D, H], south: [u + 2 * D + W, v + D, W, H], up: [u + D, v, W, D], down: [u + D + W, v, W, D] };
for (const [f, [x, y, rw, rh]] of Object.entries(rects)) if (rw > 0 && rh > 0) P[mat](t, { x, y, w: rw, h: rh }, f, { mat, o: opts ? JSON.parse(opts) : {}, glow });
const S = Math.max(2, Math.floor(700 / Math.max(tw, th)));
const out = new Uint8ClampedArray(tw * S * th * S * 4);
for (let y = 0; y < th * S; y++) for (let x = 0; x < tw * S; x++) {
  const c = t.get(Math.floor(x / S), Math.floor(y / S)), g = glow.get(Math.floor(x / S), Math.floor(y / S));
  const bg = ((Math.floor(x / 8) + Math.floor(y / 8)) & 1) ? 70 : 90;
  const a = c[3] / 255; const k = (y * tw * S + x) * 4;
  out[k] = Math.min(255, c[0] * a + bg * (1 - a) + (g[3] ? g[0] : 0)); out[k + 1] = Math.min(255, c[1] * a + bg * (1 - a) + (g[3] ? g[1] : 0)); out[k + 2] = Math.min(255, c[2] * a + bg * (1 - a) + (g[3] ? g[2] : 0)); out[k + 3] = 255;
}
fs.mkdirSync(path.join(__dirname, '..', 'out', 'shots'), { recursive: true });
const file = path.join(__dirname, '..', 'out', 'shots', `swatch_${mat}_${W}x${H}.png`);
fs.writeFileSync(file, encodePng(tw * S, th * S, out));
console.log(file, tw, th, 'x', S);
