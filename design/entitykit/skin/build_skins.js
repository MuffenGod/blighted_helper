// node skin/build_skins.js [--install] [--dev]
// Builds the 256x128 skins (Emily's Gaia skin recoloured in the top-left 128x64, plus painted extra parts) and variant_parts.json
// for the jester / infernal / holy variants of the Gaia mob. The Gaia mob's own skin and model are never touched.
const fs = require('fs');
const path = require('path');
const { Tex } = require('../raster');
const { decodePng } = require('../preview');
const { makePainters, hex } = require('../paint');
const { makeExtraPainters } = require('./painters_extra');
const { recolorInto, faceOf, BODICE, rampAt } = require('./recolor');
const sk = require('./variants_skin');

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
const PALS = {
  gaia: Emily,
  jester: require('../variants/jester').PAL,
  demon: {
    ...Emily,
    clothHi: H('#2c2c3c'), clothMid: H('#15151f'), clothDark: H('#0d0d15'), clothDeep: H('#06060a'),
    navyHi: H('#3c4c7a'), navyMid: H('#24324f'), navyDark: H('#151d36'), navyDeep: H('#0a1020'),
    greydHi: H('#5c5c68'), greydMid: H('#3c3c46'), greydDark: H('#26262e'), greydDeep: H('#14141a'),
    greybHi: H('#b4b4c0'), greybMid: H('#80808c'), greybDark: H('#52525c'), greybDeep: H('#2a2a32'),
    hornHi: H('#e2e2e6'), hornMid: H('#a8a8b0'), hornDark: H('#70707a'), hornDeep: H('#3a3a42'),
    scaleHi: H('#a8a8b0'), scaleMid: H('#70707a'), scaleDark: H('#3a3a42'), scaleDeep: H('#1e1e24'),
    goldHi: H('#ffe8a0'), goldMid: H('#e0b040'), goldShad: H('#a07820'), goldDeep: H('#5a4010'),
    wingBone: H('#0a080e'), wingDeep: H('#16101f'), wingMid: H('#382a50'), wingDark: H('#18121f'), wingHi: H('#6a5496'), wingHot: H('#8a6ab8'),
    ember: H('#b050ff'),
  },
  angel: {
    ...Emily,
    hairHi: H('#ffffff'), hairLit: H('#f8f2e0'), hairMid: H('#ece2c8'), hairShad: H('#cdbf98'), hairDeep: H('#a89868'),
    ruffHi: H('#fffdf6'), ruffMid: H('#f6f0e0'), ruffDark: H('#d8d0b8'), ruffDeep: H('#a89f84'),
    azureHi: H('#bfe4ff'), azureMid: H('#4f93d0'), azureDark: H('#2a5a9a'), azureDeep: H('#16305c'),
    goldHi: H('#fff2b8'), goldMid: H('#f0c850'), goldShad: H('#c09028'), goldDeep: H('#7a5a14'),
    featHi: H('#ffffff'), featMid: H('#fbf6ea'), featDark: H('#e6dabc'), featDeep: H('#c2b286'), featTip: H('#f0d070'),
    wingBone: H('#e8dcc0'), wingDark: H('#d8c9a0'), wingMid: H('#f4ead0'), wingHi: H('#fffaf0'), wingDeep: H('#a89868'),
  },
};

const DEMON_SKIN = [248, 226, 214, 255];
const stripe = (c1, c2, period = 4) => (x, y) => ((y % period) < period / 2 ? c1 : c2);
const blackSock = (y, L) => [Math.round(14 + 14 * L), Math.round(12 + 12 * L), Math.round(20 + 16 * L), 255];

const CFG = {
  gaia: { parts: sk.gaia, cfg: {} },
  jester: {
    parts: sk.jester,
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
        ...[LEG_UP, LEG_LO].map((rect) => ({ rect, fn: (x, y, c) => (c[3] === 0 ? null : (y % 4 < 2 ? [88, 52, 170, 255] : [20, 14, 34, 255])) })),
      ],
    },
  },
  demon: {
    parts: sk.demon,
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
    parts: sk.angel,
    cfg: {
      hair: [H('#b8a878'), H('#ece2c8'), H('#ffffff')],
      trim: [H('#c09028'), H('#f0c850'), H('#fff2b8')],
      eye: [H('#4a3000'), H('#f0b830'), H('#fff0b0')],
      overrides: [
        ...clearRects(GOWN_PANEL),   // both legs the same size (Gaia's gown panel only covers the right one)
        // black stockings, white shoes
        ...[LEG_UP, LEG_LO].map((rect) => ({ rect, fn: (x, y, c) => (c[3] === 0 ? null : (rect === LEG_LO && y >= 30 ? [240, 236, 224, 255] : [18, 16, 24, 255])) })),
      ],
    },
  },
};

// ---- box-UV layout of one cube at (u,v)
function faceRects(u, v, w, h, d) {
  return {
    west: [u, v + d, d, h], north: [u + d, v + d, w, h], east: [u + d + w, v + d, d, h], south: [u + 2 * d + w, v + d, w, h],
    up: [u + d, v, w, d], down: [u + d + w, v, w, d],
  };
}

function pack(cubes) {
  const uniq = [];
  const byKey = {};
  for (const c of cubes) {
    if (c.mat === 'raw') { c.uvOwner = c; continue; }   // samples Gaia's existing texture: keeps its own uv, nothing to paint
    if (c.share && byKey[c.share]) { c.uvOwner = byKey[c.share]; continue; }
    c.uvOwner = c; uniq.push(c);
    if (c.share) byKey[c.share] = c;
  }
  const regions = [{ x0: 128, y0: 0, x1: 256, y1: 128 }, { x0: 0, y0: 64, x1: 128, y1: 128 }];
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

const allParts = [];
const hide = {}, tweak = {};
for (const [id, def] of Object.entries(CFG)) {
  const tex = new Tex(256, 128), glow = new Tex(256, 128);
  recolorInto(tex, GAIA, def.cfg);
  recolorInto(glow, GAIA_GLOW, { eye: def.cfg.eye, trim: def.cfg.trim });
  const spec = def.parts();
  const cubes = [];
  for (const p of spec.parts) for (const c of p.cubes) cubes.push(c);
  const uniq = pack(cubes);
  const pal = PALS[id];
  const painters = { ...makePainters(pal, {}), ...makeExtraPainters(pal) };
  for (const c of uniq) {
    const [, , , w, h, d] = c.box;
    const painter = painters[c.mat];
    if (!painter) throw new Error('no painter ' + c.mat);
    for (const [face, [rx, ry, rw, rh]] of Object.entries(faceRects(c.uv[0], c.uv[1], w, h, d))) {
      if (rw <= 0 || rh <= 0) continue;
      painter(tex, { x: rx, y: ry, w: rw, h: rh }, face, { mat: c.mat, s: 1, cw: rw, ch: rh, o: c.o || {}, glow });
    }
  }
  tex.save(path.join(OUT, `xl_${id}.png`));
  glow.save(path.join(OUT, `xl_${id}_glow.png`));
  hide[id] = spec.hide;
  if (spec.tweak) tweak[id] = spec.tweak;
  for (const p of spec.parts) allParts.push(p);
  console.log(id.padEnd(7), 'parts', spec.parts.length, 'unique cubes', uniq.length);
}

const json = {
  parts: allParts.map((p) => ({
    name: p.name, parent: p.parent, variants: p.variants, pivot: p.pivot, rot: p.rot.map((v) => +v.toFixed(4)),
    ...(p.follow ? { follow: p.follow } : {}), ...(p.followVisible ? { followVisible: true } : {}), ...(p.sway ? { sway: p.sway } : {}),
    cubes: p.cubes.map((c) => ({ uv: c.uv, box: c.box.map((v) => +(+v).toFixed(3)), ...(c.inflate ? { inflate: c.inflate } : {}), ...(c.mirror ? { mirror: true } : {}) })),
    ...(p.followVisible ? { followVisible: true } : {}),
  })),
  hide, tweak,
};
fs.writeFileSync(path.join(OUT, 'variant_parts.json'), JSON.stringify(json));

function put(dest, src) { fs.mkdirSync(path.dirname(dest), { recursive: true }); fs.copyFileSync(src, dest); }
if (process.argv.includes('--install')) {
  for (const id of Object.keys(CFG)) {
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
