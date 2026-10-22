// node skin/build_hd.js [--install] [--dev]   (premium pipeline: 512x512 atlas, hd parts, richer painters)
// Builds the 256x128 skins (Emily's Gaia skin recoloured in the top-left 128x64, plus painted extra parts) and variant_parts.json
// for the jester / infernal / holy variants of the Gaia mob. The Gaia mob's own skin and model are never touched.
const fs = require('fs');
const path = require('path');
const { Tex } = require('../raster');
const { decodePng } = require('../preview');
const { makePainters, hex } = require('../paint');
const { makeExtraPainters } = require('./painters_extra');
const { recolorInto, faceOf, BODICE, rampAt } = require('./recolor');
const old = require('./variants_skin');
const hd = require('./variants_hd');
const { finalize } = require('./kit');
const { makeHD } = require('./hd_paint');
const PALS = require('./palettes');
let TEXW = 512, TEXH = 512;   // chosen below: the smallest atlas every variant packs into
const design = (id) => (hd[id] || old[id]);

const ROOT = path.resolve(__dirname, '..', '..', '..');
const RES = path.join(ROOT, 'blighted_helper', 'src', 'main', 'resources', 'assets', 'blighted_helper');
const OUT = path.join(__dirname, '..', 'out', 'skin');
fs.mkdirSync(OUT, { recursive: true });
const GAIA = decodePng(fs.readFileSync(path.join(RES, 'textures', 'entity', 'emily_gaia.png')));
const GAIA_GLOW = decodePng(fs.readFileSync(path.join(RES, 'textures', 'entity', 'emily_gaia_glow.png')));
const H = hex;
const Emily = require('../variants/emily').PAL;

// ---- texture regions (u0, v0, u1, v1) of the Gaia layout that the variants repaint
const LEG_UP = [24, 12, 36, 22], LEG_LO = [24, 22, 36, 32];
const GOWN_PANEL = [[76, 34, 92, 45], [76, 45, 92, 54]];          // the gown panel that covers her right leg
const SLEEVE = [64, 0, 76, 6], GLOVE = [64, 6, 74, 15], CROP = [0, 16, 16, 25];
const inR = (x, y, r) => x >= r[0] && y >= r[1] && x < r[2] && y < r[3];
const clearRects = (rects) => rects.map((rect) => ({ rect, fn: () => [0, 0, 0, 0] }));

// ---- palettes for the painted extra parts
const DEMON_SKIN = [248, 226, 214, 255];
const stripe = (c1, c2, period = 4) => (x, y) => ((y % period) < period / 2 ? c1 : c2);
const blackSock = (y, L) => [Math.round(14 + 14 * L), Math.round(12 + 12 * L), Math.round(20 + 16 * L), 255];

const CFG = {
  gaia: { parts: () => design('gaia')(), cfg: {} },
  jester: {
    parts: () => design('jester')(),
    cfg: {
      hair: [H('#6c6890'), H('#b8b4d0'), H('#ffffff')],
      eye: [H('#2a0408'), H('#ff3c4e'), H('#ffd0d0')],
      wing: [H('#1d0e3a'), H('#8a54e6'), H('#cfa8ff')],
      cloth: (x, y, L) => {   // bodice: purple on her left (+x) half, black on the right
        const f = faceOf(x, y, BODICE);
        if (!f) return null;
        const left = f.face === 'east' || ((f.face === 'north' || f.face === 'up' || f.face === 'down' || f.face === 'south') && f.i >= 0.5);
        if (!left) return null;
        const t = Math.min(1, L / 0.25);
        return [Math.round(21 + t * 85), Math.round(10 + t * 54), Math.round(46 + t * 146)];
      },
      overrides: [
        ...clearRects(GOWN_PANEL),
        // harlequin-striped stockings
        ...[LEG_UP, LEG_LO].map((rect) => ({ rect, fn: (x, y, c) => (c[3] === 0 ? null : ((Math.abs((x % 4) - 2) + Math.abs((y % 4) - 2)) < 2 ? [104, 62, 200, 255] : [20, 14, 36, 255])) })),
      ],
    },
  },
  demon: {
    parts: () => design('demon')(),
    cfg: {
      hair: [H('#0e0818'), H('#2c1a46'), H('#6a4a98')],
      eye: [H('#3a0a40'), H('#e040ff'), H('#ffd8ff')],
      trim: [H('#5a4010'), H('#e0b040'), H('#ffe8a0')],
      wing: [H('#0e0a14'), H('#241c34'), H('#4c3c6c')],
      cloth: (x, y, L) => (inR(x, y, CROP) ? rampAt([H('#26262e'), H('#4a4a54'), H('#767680')], L / 0.25) : null),
      skin: (c) => [Math.min(255, Math.round(c[0] * 1.0)), Math.round(c[1] * 0.97), Math.round(c[2] * 0.98)],
      overrides: [
        ...clearRects(GOWN_PANEL),
        // bare pale arms (the off-shoulder sleeve and opera glove become skin), with a dark wrist band
        { rect: [16, 12, 24, 26], fn: (x, y, c) => (c[3] === 0 ? null : DEMON_SKIN) },
        { rect: SLEEVE, fn: (x, y, c) => (c[3] === 0 ? null : DEMON_SKIN) },
        { rect: GLOVE, fn: (x, y, c) => (c[3] === 0 ? null : (y >= 13 ? [34, 32, 44, 255] : DEMON_SKIN)) },
      ],
    },
  },
  angel: {
    parts: () => design('angel')(),
    cfg: {
      hair: [H('#b8a878'), H('#ece2c8'), H('#ffffff')],
      trim: [H('#c09028'), H('#f0c850'), H('#fff2b8')],
      eye: [H('#4a3000'), H('#f0b830'), H('#fff0b0')],
      overrides: [
        ...clearRects(GOWN_PANEL),   // both legs the same size (Gaia's gown panel only covers the right one)
        // close Gaia's slit in the hip wrap (front-left and left side, bottom two rows) with the black cloth of her right side
        { rect: [72, 30, 79, 32], fn: (x, y, c, ctx) => ctx.get(70, y) },
        // thigh-high black stockings: a little bare thigh (Gaia's own skin) under the coat, a grey stocking top just above the knee (as in her reference)
        { rect: LEG_UP, fn: (x, y, c) => (c[3] === 0 ? null : y === 21 ? [74, 72, 90, 255] : (y < 15 && x >= 30) ? [18, 16, 24, 255] : null) },
        { rect: LEG_LO, fn: (x, y, c) => (c[3] === 0 ? null : [18, 16, 24, 255]) },
      ],
    },
  },
};

// ---- variants the user preferred in their earlier (deployed 2026-10-21) look: their skins and parts come verbatim from skin/legacy
// (a copy of design/backup/premium-before-2026-10-21), padded into the shared atlas. Only the nun uses the premium hd build.
// --clean-source: for preparing the public repo, paint every variant from the freshly repainted (GoG-free) base
// instead of reusing the old legacy/*.png snapshots (which still carry the Grimoire of Gaia derived pixels baked
// into them from before 2026-10-22). Does not affect the normal --install/--dev pipeline used for the live game.
const LEGACY = process.argv.includes('--clean-source') ? new Set() : new Set(['gaia', 'jester', 'demon']);
const LEGACY_PROPS = { jester: (n) => n.startsWith('x_ball') };
const LEGACY_DIR = path.join(__dirname, 'legacy');
const LEGACY_JSON = JSON.parse(fs.readFileSync(path.join(LEGACY_DIR, 'legacy_parts.json')));
function legacyVariant(id) {
  const blit = (file) => {
    const src = decodePng(fs.readFileSync(path.join(LEGACY_DIR, file)));
    const t = new Tex(TEXW, TEXH);
    for (let y = 0; y < Math.min(src.h, TEXH); y++) for (let x = 0; x < Math.min(src.w, TEXW); x++) { const i = (y * src.w + x) * 4; t.set(x, y, [src.d[i], src.d[i + 1], src.d[i + 2], src.d[i + 3]]); }
    return t;
  };
  // the old files reused the waist names x_trunk0..2 in every variant (one shared mesh drew only the last one): give each variant its own
  const parts = LEGACY_JSON.parts.filter((p) => p.variants.includes(id)).map((p) => ({ ...p, variants: [id], name: p.name.startsWith('x_trunk') ? p.name.replace('x_trunk', 'x_' + id + 'trunk') : p.name }));
  const tex = blit(`xl_${id}.png`), glow = blit(`xl_${id}_glow.png`);
  // a few props from the premium design that the old look lacks (the jester's juggling balls), painted into the free lower half without glow
  const want = LEGACY_PROPS[id];
  if (want) {
    const props = finalize(CFG[id].parts().parts).filter((p) => want(p.name));
    const cubes = [];
    for (const p of props) for (const c of p.cubes) { c.o = { ...(c.o || {}), glow: 0 }; cubes.push(c); }
    const uniq = pack(cubes, [{ x0: 0, y0: 129, x1: TEXW, y1: TEXH }]);
    paintCubes(tex, new Tex(TEXW, TEXH), uniq, id);
    parts.push(...props);
  }
  return { tex, glow, parts, hide: LEGACY_JSON.hide[id] || [], tweak: (LEGACY_JSON.tweak || {})[id] };
}

function paintCubes(tex, glow, uniq, id) {
  const pal = PALS[id];
  const painters = { ...makePainters(pal, {}), ...makeExtraPainters(pal), ...makeHD(pal).P };
  for (const c of uniq) {
    const [, , , w, h, d] = c.box;
    const painter = painters[c.mat];
    if (!painter) throw new Error('no painter ' + c.mat);
    for (const [face, [rx, ry, rw, rh]] of Object.entries(faceRects(c.uv[0], c.uv[1], w, h, d))) {
      if (rw <= 0 || rh <= 0) continue;
      painter(tex, { x: rx, y: ry, w: rw, h: rh }, face, { mat: c.mat, s: 1, cw: rw, ch: rh, o: c.o || {}, glow, pal });
    }
  }
}

// ---- box-UV layout of one cube at (u,v)
function faceRects(u, v, w, h, d) {
  return {
    west: [u, v + d, d, h], north: [u + d, v + d, w, h], east: [u + d + w, v + d, d, h], south: [u + 2 * d + w, v + d, w, h],
    up: [u + d, v, w, d], down: [u + d + w, v, w, d],
  };
}

function pack(cubes, regionsIn) {
  const uniq = [];
  const byKey = {};
  for (const c of cubes) {
    if (c.mat === 'raw') { c.uvOwner = c; continue; }   // samples Gaia's existing texture: keeps its own uv, nothing to paint
    if (c.share && byKey[c.share]) { c.uvOwner = byKey[c.share]; continue; }
    c.uvOwner = c; uniq.push(c);
    if (c.share) byKey[c.share] = c;
  }
  const regions = regionsIn || [{ x0: 128, y0: 0, x1: TEXW, y1: TEXH }, { x0: 0, y0: 64, x1: 128, y1: TEXH }];
  const sized = uniq.map((c) => { const [, , , w, h, d] = c.box; return { c, rw: 2 * d + 2 * w, rh: d + h }; }).sort((a, b) => b.rh - a.rh || b.rw - a.rw);
  let ri = 0, cx = regions[0].x0, cy = regions[0].y0, rowH = 0;
  for (const s of sized) {
    for (;;) {
      const Rg = regions[ri];
      if (cx + s.rw > Rg.x1) { cx = Rg.x0; cy += rowH + 1; rowH = 0; }
      if (cy + s.rh > Rg.y1) { ri++; if (ri >= regions.length) throw new Error('skin atlas full'); cx = regions[ri].x0; cy = regions[ri].y0; rowH = 0; continue; }
      break;
    }
    s.c.uv = [cx, cy];
    cx += s.rw + 1; rowH = Math.max(rowH, s.rh);
  }
  for (const c of cubes) if (c.mat !== 'raw') c.uv = c.uvOwner.uv;
  return uniq;
}

// pick the smallest atlas that holds all four variants (the Gaia 128x64 skin always stays top-left)
if (!process.argv.some((a) => a.startsWith('--only='))) {
  for (const [w, h] of [[256, 256], [384, 256], [512, 256], [384, 384], [512, 384], [512, 512]]) {
    TEXW = w; TEXH = h;
    try {
      for (const [id, def] of Object.entries(CFG)) { if (LEGACY.has(id)) continue; const spec = def.parts(); spec.parts = finalize(spec.parts); const cubes = []; for (const p of spec.parts) for (const c of p.cubes) cubes.push(c); pack(cubes); }
      console.log('atlas', w + 'x' + h); break;
    } catch (e) { if (!/atlas full/.test(e.message)) throw e; if (h === 512 && w === 512) throw e; }
  }
}
const allParts = [];
const prevPath = path.join(OUT, 'variant_parts.json');
const prev = (ONLY_EARLY().length && fs.existsSync(prevPath)) ? JSON.parse(fs.readFileSync(prevPath)) : null;
function ONLY_EARLY() { return (process.argv.find((a) => a.startsWith('--only=')) || '').slice(7).split(',').filter(Boolean); }
const hide = {}, tweak = {};
const ONLY = (process.argv.find((a) => a.startsWith('--only=')) || '').slice(7).split(',').filter(Boolean);
for (const [id, def] of Object.entries(CFG)) {
  if (ONLY.length && !ONLY.includes(id)) continue;
  if (LEGACY.has(id)) {
    const L = legacyVariant(id);
    L.tex.save(path.join(OUT, `xl_${id}.png`));
    L.glow.save(path.join(OUT, `xl_${id}_glow.png`));
    hide[id] = L.hide;
    if (L.tweak) tweak[id] = L.tweak;
    for (const p of L.parts) allParts.push(p);
    console.log(id.padEnd(7), 'parts', L.parts.length, '(legacy look)');
    continue;
  }
  const tex = new Tex(TEXW, TEXH), glow = new Tex(TEXW, TEXH);
  recolorInto(tex, GAIA, def.cfg);
  recolorInto(glow, GAIA_GLOW, { eye: def.cfg.eye, trim: def.cfg.trim });
  const spec = def.parts(); spec.parts = finalize(spec.parts);
  const cubes = [];
  for (const p of spec.parts) for (const c of p.cubes) cubes.push(c);
  const uniq = pack(cubes);
  paintCubes(tex, glow, uniq, id);
  tex.save(path.join(OUT, `xl_${id}.png`));
  glow.save(path.join(OUT, `xl_${id}_glow.png`));
  hide[id] = spec.hide;
  if (spec.tweak) tweak[id] = spec.tweak;
  for (const p of spec.parts) allParts.push(p);
  console.log(id.padEnd(7), 'parts', spec.parts.length, 'unique cubes', uniq.length);
}

if (prev) { for (const p of prev.parts) if (!ONLY_EARLY().includes(p.variants[0])) allParts.push(p); for (const k of Object.keys(prev.hide)) if (!ONLY_EARLY().includes(k)) hide[k] = prev.hide[k]; for (const k of Object.keys(prev.tweak || {})) if (!ONLY_EARLY().includes(k)) tweak[k] = prev.tweak[k]; }
{ const seen = new Map(); for (const p of allParts) { if (seen.has(p.name)) throw new Error('duplicate part name across variants: ' + p.name + ' (' + seen.get(p.name) + ' / ' + p.variants[0] + ')'); seen.set(p.name, p.variants[0]); } }
const json = {
  parts: allParts.map((p) => ({
    name: p.name, parent: p.parent, variants: p.variants, pivot: p.pivot, rot: p.rot.map((v) => +v.toFixed(4)),
    ...(p.follow ? { follow: p.follow } : {}), ...(p.followVisible ? { followVisible: true } : {}), ...(p.sway ? { sway: p.sway } : {}), ...(p.scale ? { scale: p.scale } : {}), ...(p.phys ? { phys: p.phys } : {}), ...(p.drive ? { drive: p.drive } : {}), ...(p.show ? { show: p.show } : {}), ...(p.group ? { group: p.group } : {}), ...(p.hidden ? { hidden: true } : {}), ...(p.ang !== undefined ? { ang: +p.ang.toFixed(4) } : {}),
    cubes: p.cubes.map((c) => ({ uv: c.uv, box: c.box.map((v) => +(+v).toFixed(3)), ...(c.inflate ? { inflate: c.inflate } : {}), ...(c.mirror ? { mirror: true } : {}) })),
    ...(p.followVisible ? { followVisible: true } : {}),
  })),
  tex: [TEXW, TEXH], hide, tweak,
};
fs.writeFileSync(path.join(OUT, 'variant_parts.json'), JSON.stringify(json));

function put(dest, src) { fs.mkdirSync(path.dirname(dest), { recursive: true }); fs.copyFileSync(src, dest); }
if (process.argv.includes('--install')) {
  for (const id of Object.keys(CFG).filter((k) => !ONLY_EARLY().length || ONLY_EARLY().includes(k))) {
    put(path.join(RES, 'textures', 'entity', `xl_${id}.png`), path.join(OUT, `xl_${id}.png`));
    put(path.join(RES, 'textures', 'entity', `xl_${id}_glow.png`), path.join(OUT, `xl_${id}_glow.png`));
  }
  put(path.join(RES, 'helper', 'variant_parts.json'), path.join(OUT, 'variant_parts.json'));
  console.log('installed into the mod resources');
}
if (process.argv.includes('--dev')) {
  const game = 'C:/Users/srafa/bd/game';
  put(path.join(game, 'config', 'blighted_helper-parts.json'), path.join(OUT, 'variant_parts.json'));
  const pack = path.join(game, 'resourcepacks', 'emily_dev', 'assets', 'blighted_helper', 'textures', 'entity');
  for (const id of Object.keys(CFG)) {
    put(path.join(pack, `xl_${id}.png`), path.join(OUT, `xl_${id}.png`));
    put(path.join(pack, `xl_${id}_glow.png`), path.join(OUT, `xl_${id}_glow.png`));
  }
  console.log('copied to the scratch game (config + emily_dev resource pack)');
}
