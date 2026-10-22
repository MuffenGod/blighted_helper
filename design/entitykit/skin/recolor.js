// Recolours Emily's Gaia skin (128x64, used untouched by the Gaia mob) into the top-left corner of a 256x128 variant skin.
// Regions come from the texOffs / box sizes in HelperGaiaModel.createBodyLayer.
const { mixc } = require('../paint');

const HEAD = [0, 0, 24, 12], CROWN = [36, 0, 64, 14], HAIR1 = [36, 14, 58, 25], HAIR2 = [36, 25, 60, 37], LOCKS = [48, 37, 52, 43];
const WINGS = [100, 40, 128, 64];
const SKIRTS = [[96, 8, 8, 4, 4], [96, 16, 9, 5, 4], [96, 25, 10, 5, 4]];   // u, v, w, h, d of skirt1..3
const BODICE = [[0, 16, 5, 6, 3]];
const inR = (x, y, r) => x >= r[0] && y >= r[1] && x < r[2] && y < r[3];

function hsv(r, g, b) {
  const mx = Math.max(r, g, b), mn = Math.min(r, g, b), d = mx - mn;
  let h = 0;
  if (d) {
    if (mx === r) h = ((g - b) / d) % 6; else if (mx === g) h = (b - r) / d + 2; else h = (r - g) / d + 4;
    h *= 60; if (h < 0) h += 360;
  }
  return [h, mx ? d / mx : 0, mx / 255];
}
const lum = (r, g, b) => (0.3 * r + 0.59 * g + 0.11 * b) / 255;
const clamp01 = (t) => Math.max(0, Math.min(1, t));
function rampAt(stops, t) {
  t = clamp01(t) * (stops.length - 1);
  const i = Math.min(stops.length - 2, Math.floor(t));
  return mixc(stops[i], stops[i + 1], t - i);
}

// which box face does pixel (x,y) belong to, for the listed boxes? returns {face, i (0..1 across), j} or null
function faceOf(x, y, boxes) {
  for (const [u, v, w, h, d] of boxes) {
    const rects = {
      west: [u, v + d, d, h], north: [u + d, v + d, w, h], east: [u + d + w, v + d, d, h], south: [u + 2 * d + w, v + d, w, h],
      up: [u + d, v, w, d], down: [u + d + w, v, w, d],
    };
    for (const [f, [rx, ry, rw, rh]] of Object.entries(rects)) {
      if (x >= rx && y >= ry && x < rx + rw && y < ry + rh) return { face: f, i: (x - rx + 0.5) / rw, j: (y - ry + 0.5) / rh };
    }
  }
  return null;
}

// cfg: { hair, trim, eye, wing: ramps (arrays of rgb), cloth: ramp | (x,y,L)=>rgb|null, skin: (rgb)=>rgb, hairStreak }
function recolorPixel(x, y, c, cfg) {
  const [r, g, b, a] = c;
  if (a === 0) return c;
  const [h, s, v] = hsv(r, g, b);
  const L = lum(r, g, b);
  const hairRegion = inR(x, y, CROWN) || inR(x, y, HAIR1) || inR(x, y, HAIR2) || inR(x, y, LOCKS);
  const goldish = h > 28 && h < 64 && s > 0.30;
  if ((hairRegion || inR(x, y, HEAD)) && goldish && cfg.hair) {
    let out = rampAt(cfg.hair, (L - 0.40) / 0.55);
    if (cfg.hairStreak && hairRegion && ((x * 7 + y * 13) % 17 === 0) && L > 0.55) out = cfg.hairStreak;
    return [...out, a];
  }
  if (h > 95 && h < 180 && s > 0.28 && cfg.eye) return [...rampAt(cfg.eye, (L - 0.25) / 0.65), a];
  if (inR(x, y, WINGS) && (h < 20 || h > 335) && s > 0.45 && v > 0.2 && cfg.wing) return [...rampAt(cfg.wing, (L - 0.08) / 0.42), a];
  if (goldish && cfg.trim && !inR(x, y, HEAD)) return [...rampAt(cfg.trim, (L - 0.35) / 0.6), a];
  if (v < 0.34 && s < 0.6 && cfg.cloth) {
    if (typeof cfg.cloth === 'function') {
      const out = cfg.cloth(x, y, L);
      if (out) return [...out, a];
    } else return [...rampAt(cfg.cloth, L / 0.25), a];
  }
  if (cfg.skin && h < 40 && s < 0.55 && v > 0.6) return [...cfg.skin([r, g, b]), a];
  return c;
}

function recolorInto(dst, src, cfg) {
  for (let y = 0; y < src.h; y++) for (let x = 0; x < src.w; x++) {
    const k = (y * src.w + x) * 4;
    const c = [src.d[k], src.d[k + 1], src.d[k + 2], src.d[k + 3]];
    dst.set(x, y, recolorPixel(x, y, c, cfg));
  }
  // region overrides (stockings, bare arms, hidden gown panels...) run after the colour pass; they can read the variant's skin tone
  const ctx = { skin: dst.get(19, 17), get: (x, y) => dst.get(x, y) };
  for (const ov of cfg.overrides || []) {
    const [x0, y0, x1, y1] = ov.rect;
    for (let y = y0; y < y1; y++) for (let x = x0; x < x1; x++) {
      const out = ov.fn(x, y, dst.get(x, y), ctx);
      if (out) dst.set(x, y, out);
    }
  }
}

module.exports = { recolorInto, rampAt, faceOf, SKIRTS, BODICE, lum };
