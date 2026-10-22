// entitykit preview: a small software renderer for GeckoLib geo + atlas, so a model can be judged without launching the game.
//   node preview.js <id> [--dir out] [--pose idle|none] [--views front,side,back,three] [--scale 8] [--bg 24,22,32]
// Transform convention follows GeckoLib: x is mirrored, bone angles are (-x, -y, +z) applied Z then Y then X, the model faces +Z (south).
const fs = require('fs');
const path = require('path');
const zlib = require('zlib');
const { Tex } = require('./raster');

function decodePng(buf) {
  let p = 8, w = 0, h = 0; const idat = [];
  while (p < buf.length) {
    const len = buf.readUInt32BE(p), type = buf.toString('ascii', p + 4, p + 8);
    const data = buf.subarray(p + 8, p + 8 + len);
    if (type === 'IHDR') { w = data.readUInt32BE(0); h = data.readUInt32BE(4); if (data[8] !== 8 || data[9] !== 6) throw new Error('need 8-bit RGBA'); }
    else if (type === 'IDAT') idat.push(data);
    p += 12 + len;
  }
  const raw = zlib.inflateSync(Buffer.concat(idat));
  const out = new Uint8ClampedArray(w * h * 4), bpp = 4, stride = w * 4;
  for (let y = 0; y < h; y++) {
    const ft = raw[y * (stride + 1)], row = y * (stride + 1) + 1;
    for (let x = 0; x < stride; x++) {
      const a = x >= bpp ? out[y * stride + x - bpp] : 0, b = y > 0 ? out[(y - 1) * stride + x] : 0, c = x >= bpp && y > 0 ? out[(y - 1) * stride + x - bpp] : 0;
      let v = raw[row + x];
      if (ft === 1) v += a; else if (ft === 2) v += b; else if (ft === 3) v += (a + b) >> 1;
      else if (ft === 4) { const pa = Math.abs(b - c), pb = Math.abs(a - c), pc = Math.abs(a + b - 2 * c); v += pa <= pb && pa <= pc ? a : pb <= pc ? b : c; }
      out[y * stride + x] = v & 255;
    }
  }
  return { w, h, d: out };
}

// ---- matrices (row-major 4x4 as arrays of 16, column vectors)
const I4 = () => [1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1];
const mm = (a, b) => { const r = new Array(16).fill(0); for (let i = 0; i < 4; i++) for (let j = 0; j < 4; j++) for (let k = 0; k < 4; k++) r[i * 4 + j] += a[i * 4 + k] * b[k * 4 + j]; return r; };
const T = (x, y, z) => [1, 0, 0, x, 0, 1, 0, y, 0, 0, 1, z, 0, 0, 0, 1];
const RX = (a) => { const c = Math.cos(a), s = Math.sin(a); return [1, 0, 0, 0, 0, c, -s, 0, 0, s, c, 0, 0, 0, 0, 1]; };
const RY = (a) => { const c = Math.cos(a), s = Math.sin(a); return [c, 0, s, 0, 0, 1, 0, 0, -s, 0, c, 0, 0, 0, 0, 1]; };
const RZ = (a) => { const c = Math.cos(a), s = Math.sin(a); return [c, -s, 0, 0, s, c, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1]; };
const ap = (m, p) => [m[0] * p[0] + m[1] * p[1] + m[2] * p[2] + m[3], m[4] * p[0] + m[5] * p[1] + m[6] * p[2] + m[7], m[8] * p[0] + m[9] * p[1] + m[10] * p[2] + m[11]];
const rad = (d) => (d * Math.PI) / 180;
const rotM = (r) => mm(RZ(rad(r[2])), mm(RY(rad(-r[1])), RX(rad(-r[0]))));   // GeckoLib: (-x, -y, +z), Z then Y then X

function loadPose(dir, id, which) {
  if (which === 'none') return {};
  const f = path.join(dir, id + '.animation.json');
  if (!fs.existsSync(f)) return {};
  const A = JSON.parse(fs.readFileSync(f)).animations;
  const pose = {};
  const val = (c) => {
    if (Array.isArray(c)) return c;
    const ks = Object.keys(c).map(Number).sort((a, b) => a - b);
    const k = c[String(ks[0])] || c[ks[0]];
    return Array.isArray(k) ? k : (k.vector || k.post || k.pre);
  };
  const names = which.split('+');
  for (const n of names) {
    const a = A['animation.emily.' + n];
    if (!a) continue;
    for (const [b, ch] of Object.entries(a.bones)) {
      pose[b] = pose[b] || {};
      if (ch.rotation) pose[b].rotation = (pose[b].rotation || [0, 0, 0]).map((v, i) => v + val(ch.rotation)[i]);
      if (ch.position) pose[b].position = (pose[b].position || [0, 0, 0]).map((v, i) => v + val(ch.position)[i]);
      if (ch.scale) pose[b].scale = val(ch.scale);
    }
  }
  return pose;
}

function renderModel({ geo, tex, pose, view, S, W, H, ox, oy, bg, cx0 }) {
  const bones = geo.bones, by = {};
  for (const b of bones) by[b.name] = b;
  const cache = {};
  const mxOf = (n) => {   // matrix mapping rest-space (x mirrored) points to posed space
    if (cache[n]) return cache[n];
    const b = by[n];
    const par = b.parent ? mxOf(b.parent) : I4();
    const pz = pose[n] || {};
    const piv = [-b.pivot[0], b.pivot[1], b.pivot[2]];
    const rot = (b.rotation || [0, 0, 0]).map((v, i) => v + ((pz.rotation && pz.rotation[i]) || 0));
    const pos = pz.position ? [-pz.position[0], pz.position[1], pz.position[2]] : [0, 0, 0];
    let m = mm(par, T(piv[0] + pos[0], piv[1] + pos[1], piv[2] + pos[2]));
    m = mm(m, rotM(rot));
    if (pz.scale) { const s = pz.scale; m = mm(m, [s[0], 0, 0, 0, 0, s[1], 0, 0, 0, 0, s[2], 0, 0, 0, 0, 1]); }
    m = mm(m, T(-piv[0], -piv[1], -piv[2]));
    return (cache[n] = m);
  };
  // view: screen x/depth from world (x_w = -x_m, z_w = -z_m)
  const proj = {
    front: (p) => [p[0] * -1 * -1, p[1], p[2] * -1],   // x_w = -x_m ; depth toward camera = z_w = -z_m
    back: (p) => [-(-p[0]), p[1], -(-p[2])],
    side: (p) => [p[2], p[1], -p[0]],
  };
  const toScreen = (pm, vw) => {   // pm in mirrored model space -> [sx, sy, depth]
    const xw = -pm[0], zw = -pm[2], y = pm[1];
    if (vw === 'front') return [xw, y, zw];
    if (vw === 'back') return [-xw, y, -zw];
    if (vw === 'side') return [-zw, y, xw];
    if (vw === 'three') { const a = rad(-32), c = Math.cos(a), s = Math.sin(a); const xr = xw * c + zw * s, zr = -xw * s + zw * c; return [xr, y, zr]; }
    return [xw, y, zw];
  };
  const img = new Float32Array(W * H * 3), zb = new Float32Array(W * H).fill(-1e9);
  for (let i = 0; i < W * H; i++) { img[i * 3] = bg[0]; img[i * 3 + 1] = bg[1]; img[i * 3 + 2] = bg[2]; }
  const FACES = {
    north: { c: [[0, 1, 0], [1, 1, 0], [1, 0, 0], [0, 0, 0]], n: [0, 0, -1] },     // u -> +x, top-left first
    south: { c: [[1, 1, 1], [0, 1, 1], [0, 0, 1], [1, 0, 1]], n: [0, 0, 1] },
    east: { c: [[1, 1, 0], [1, 1, 1], [1, 0, 1], [1, 0, 0]], n: [1, 0, 0] },
    west: { c: [[0, 1, 1], [0, 1, 0], [0, 0, 0], [0, 0, 1]], n: [-1, 0, 0] },
    up: { c: [[0, 1, 0], [1, 1, 0], [1, 1, 1], [0, 1, 1]], n: [0, 1, 0] },
    down: { c: [[0, 0, 1], [1, 0, 1], [1, 0, 0], [0, 0, 0]], n: [0, -1, 0] },
  };
  const tris = [];
  for (const b of bones) {
    if (!b.cubes) continue;
    const bm = mxOf(b.name);
    for (const c of b.cubes) {
      const o = c.origin, s = c.size, inf = c.inflate || 0;
      let cm = bm;
      if (c.rotation) {
        const cp = c.pivot || [o[0] + s[0] / 2, o[1] + s[1] / 2, o[2] + s[2] / 2];
        const pm = [-cp[0], cp[1], cp[2]];
        cm = mm(cm, mm(T(pm[0], pm[1], pm[2]), mm(rotM(c.rotation), T(-pm[0], -pm[1], -pm[2]))));
      }
      for (const [fn, F] of Object.entries(FACES)) {
        const uv = c.uv[fn];
        if (!uv) continue;
        const pts = F.c.map((q) => {
          const x = o[0] - inf + q[0] * (s[0] + 2 * inf), y = o[1] - inf + q[1] * (s[1] + 2 * inf), z = o[2] - inf + q[2] * (s[2] + 2 * inf);
          return toScreen(ap(cm, [-x, y, z]), view);
        });
        const uvs = [[0, 0], [1, 0], [1, 1], [0, 1]].map(([u, v]) => [uv.uv[0] + u * uv.uv_size[0], uv.uv[1] + v * uv.uv_size[1]]);
        // shading from the quad normal
        const e1 = [pts[1][0] - pts[0][0], pts[1][1] - pts[0][1], pts[1][2] - pts[0][2]], e2 = [pts[3][0] - pts[0][0], pts[3][1] - pts[0][1], pts[3][2] - pts[0][2]];
        let n = [e1[1] * e2[2] - e1[2] * e2[1], e1[2] * e2[0] - e1[0] * e2[2], e1[0] * e2[1] - e1[1] * e2[0]];
        const nl = Math.hypot(n[0], n[1], n[2]) || 1; n = n.map((v) => v / nl);
        if (n[2] < 0) n = n.map((v) => -v);
        const shade = 0.62 + 0.38 * Math.max(0, n[2] * 0.7 + n[1] * 0.55 + n[0] * -0.3);
        tris.push({ p: [pts[0], pts[1], pts[2]], t: [uvs[0], uvs[1], uvs[2]], shade }, { p: [pts[0], pts[2], pts[3]], t: [uvs[0], uvs[2], uvs[3]], shade });
      }
    }
  }
  for (const tr of tris) {
    const P = tr.p.map((q) => [q[0] * S + ox, oy - q[1] * S, q[2]]);
    const minx = Math.max(0, Math.floor(Math.min(P[0][0], P[1][0], P[2][0]))), maxx = Math.min(W - 1, Math.ceil(Math.max(P[0][0], P[1][0], P[2][0])));
    const miny = Math.max(0, Math.floor(Math.min(P[0][1], P[1][1], P[2][1]))), maxy = Math.min(H - 1, Math.ceil(Math.max(P[0][1], P[1][1], P[2][1])));
    const d = (P[1][1] - P[2][1]) * (P[0][0] - P[2][0]) + (P[2][0] - P[1][0]) * (P[0][1] - P[2][1]);
    if (Math.abs(d) < 1e-9) continue;
    for (let y = miny; y <= maxy; y++) for (let x = minx; x <= maxx; x++) {
      const px = x + 0.5, py = y + 0.5;
      const l0 = ((P[1][1] - P[2][1]) * (px - P[2][0]) + (P[2][0] - P[1][0]) * (py - P[2][1])) / d;
      const l1 = ((P[2][1] - P[0][1]) * (px - P[2][0]) + (P[0][0] - P[2][0]) * (py - P[2][1])) / d;
      const l2 = 1 - l0 - l1;
      if (l0 < -1e-4 || l1 < -1e-4 || l2 < -1e-4) continue;
      const z = l0 * P[0][2] + l1 * P[1][2] + l2 * P[2][2];
      const i = y * W + x;
      if (z <= zb[i]) continue;
      const u = l0 * tr.t[0][0] + l1 * tr.t[1][0] + l2 * tr.t[2][0], v = l0 * tr.t[0][1] + l1 * tr.t[1][1] + l2 * tr.t[2][1];
      const tx = Math.min(tex.w - 1, Math.max(0, Math.floor(u))), ty = Math.min(tex.h - 1, Math.max(0, Math.floor(v)));
      const ti = (ty * tex.w + tx) * 4;
      if (tex.d[ti + 3] < 128) continue;
      zb[i] = z;
      img[i * 3] = tex.d[ti] * tr.shade; img[i * 3 + 1] = tex.d[ti + 1] * tr.shade; img[i * 3 + 2] = tex.d[ti + 2] * tr.shade;
    }
  }
  return img;
}

function main() {
  const a = process.argv.slice(2);
  const opt = (k, d) => { const i = a.indexOf('--' + k); return i >= 0 ? a[i + 1] : d; };
  const ids = opt('ids', a[0]).split(',');
  const dir = path.resolve(opt('dir', path.join(__dirname, 'out')));
  const views = opt('views', 'front,side,back').split(',');
  const S0 = +opt('scale', 8), SS = 2, S = S0 * SS;
  const bg = opt('bg', '30,28,40').split(',').map(Number);
  // --focus cx,cy,size : frame a window of 'size' model units centred on (cx,cy) (units, y up) instead of the whole figure
  const focus = opt('focus', null) ? opt('focus', null).split(',').map(Number) : null;
  const vw = focus ? Math.round(focus[2] * S) : Math.round(54 * S), vh = focus ? Math.round(focus[2] * S) : Math.round(56 * S);
  const cells = [];
  for (const id of ids) for (const v of views) cells.push([id, v]);
  const W = vw * cells.length, H = vh;
  const out = new Float32Array(W * H * 3);
  const cache = {};
  cells.forEach(([id, v], i) => {
    if (!cache[id]) cache[id] = { geo: JSON.parse(fs.readFileSync(path.join(dir, id + '.geo.json')))['minecraft:geometry'][0], tex: decodePng(fs.readFileSync(path.join(dir, id + '.png'))), pose: loadPose(dir, id, opt('pose', 'idle')) };
    const c = cache[id];
    const ox = focus ? vw / 2 - focus[0] * S : vw / 2, oy = focus ? vh / 2 + focus[1] * S : vh - 2 * S;
    const sub = renderModel({ geo: c.geo, tex: c.tex, pose: c.pose, view: v, S, W: vw, H: vh, ox, oy, bg });
    for (let y = 0; y < vh; y++) for (let x = 0; x < vw; x++) for (let k = 0; k < 3; k++) out[(y * W + i * vw + x) * 3 + k] = sub[(y * vw + x) * 3 + k];
  });
  const ow = W / SS, oh = H / SS, t = new Tex(ow, oh);
  for (let y = 0; y < oh; y++) for (let x = 0; x < ow; x++) {
    let r = 0, g = 0, b = 0;
    for (let dy = 0; dy < SS; dy++) for (let dx = 0; dx < SS; dx++) { const j = ((y * SS + dy) * W + x * SS + dx) * 3; r += out[j]; g += out[j + 1]; b += out[j + 2]; }
    t.set(x, y, [r / (SS * SS), g / (SS * SS), b / (SS * SS), 255]);
  }
  const f = path.join(dir, 'preview_' + (ids.length > 1 ? 'sheet' : ids[0]) + opt('tag', '') + '.png');
  t.save(f);
  console.log(f);
}
if (require.main === module) main();
module.exports = { decodePng, renderModel, loadPose };
