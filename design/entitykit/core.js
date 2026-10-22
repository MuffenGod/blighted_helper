// entitykit core: a tiny code-first "Blockbench" for GeckoLib (Bedrock 1.12) entities.
//   const kit = makeKit();           // bones + cubes DSL
//   kit.bone('root', null, [0,0,0]); kit.cube('root', origin, size, 'material', {opts});
//   kit.build({ id, painters, swatches, out })  // packs the atlas, paints, writes geo/anim/png/glowmask
// Materials are painter names; a painter fills one face rectangle (see paint.js).
const fs = require('fs');
const path = require('path');
const { Tex } = require('./raster');

const FACE_NAMES = ['north', 'south', 'east', 'west', 'up', 'down'];
const r4 = (v) => Math.round(v * 10000) / 10000;

function makeKit() {
  const bones = [], byName = {};
  const SIDES = [['l', 1], ['r', -1]];
  const kit = { bones, byName, SIDES, sway: [], meta: {} };

  kit.bone = (name, parent, rel, rot) => {
    if (byName[name]) throw new Error('dup bone ' + name);
    if (parent && !byName[parent]) throw new Error('missing parent ' + parent + ' for ' + name);
    const b = { name, parent, rel, rot: rot || null, cubes: [] };
    bones.push(b); byName[name] = b; return b;
  };
  kit.cube = (b, o, sz, mat, opts = {}) => {
    if (!byName[b]) throw new Error('no bone ' + b);
    byName[b].cubes.push({ o, sz, mat, opts: { s: 6, ...opts } });
  };
  kit.absPivot = (n) => {
    const b = byName[n];
    const p = b.parent ? kit.absPivot(b.parent) : [0, 0, 0];
    return [p[0] + b.rel[0], p[1] + b.rel[1], p[2] + b.rel[2]];
  };
  // mirror helpers: left side is +X, right side is -X
  kit.mx = (sg, o, sz) => (sg > 0 ? o : [-o[0] - sz[0], o[1], o[2]]);
  kit.mr = (sg, r) => (sg > 0 ? r : [r[0], -r[1], -r[2]]);
  kit.mrel = (sg, p) => [p[0] * sg, p[1], p[2]];
  kit.pair = (base, parent, rel, rot, build) => {
    for (const [n, sg] of SIDES) {
      const nm = base + '_' + n;
      kit.bone(nm, parent, kit.mrel(sg, rel), rot ? kit.mr(sg, rot) : null);
      build(nm, sg, n);
    }
  };
  // mirrored cube
  kit.cm = (nm, sg, o, sz, mat, opts = {}) => {
    const o2 = { ...opts };
    if (o2.rot) o2.rot = kit.mr(sg, o2.rot);
    kit.cube(nm, kit.mx(sg, o, sz), sz, mat, o2);
  };
  // a chain of bones hanging from `parent`: seg(k) -> { rel, rot, cubes:[{o,sz,mat,opts}] }; returns the bone names
  kit.chain = (prefix, parent, count, seg) => {
    const names = [];
    let p = parent;
    for (let k = 0; k < count; k++) {
      const s = seg(k);
      const nm = prefix + k;
      kit.bone(nm, p, s.rel, s.rot);
      for (const c of s.cubes) kit.cube(nm, c.o, c.sz, c.mat, c.opts || {});
      names.push(nm); p = nm;
    }
    return names;
  };
  // n bones evenly around a vertical axis (collars, halos, spike crowns): each bone faces outward, cubeFn(i, bone) adds its cubes
  kit.ring = (prefix, parent, { n, R, Rz, y, off = 0, cx = 0, cz = 0, cubeFn }) => {
    const names = [];
    for (let i = 0; i < n; i++) {
      const phi = (360 * i) / n + off, rd = (phi * Math.PI) / 180;
      const nm = prefix + i;
      kit.bone(nm, parent, [cx + R * Math.sin(rd), y, cz - (Rz || R) * Math.cos(rd)], [0, +phi.toFixed(3), 0]);
      cubeFn(nm, i, phi);
      names.push(nm);
    }
    return names;
  };
  // secondary-motion bones driven by the always-running 'sway' animation: {bones, rot:[x,y,z], pos:[x,y,z], phase, step, walk}
  kit.addSway = (spec) => kit.sway.push(spec);

  kit.build = ({ id, painters, swatches = {}, out, maxH = 2048, bounds = { w: 8, h: 5, off: [0, 1.5, 0] }, animFactory }) => {
    fs.mkdirSync(out, { recursive: true });
    // ---- atlas
    const faces = [];
    const sw = {};
    const swatchFor = (mat) => sw[mat] || (sw[mat] = { mat, w: 2, h: 2, swatch: true });
    for (const b of bones) {
      b.cubes.forEach((c) => {
        const [sx, sy, sz] = c.sz;
        const dims = { north: [sx, sy], south: [sx, sy], east: [sz, sy], west: [sz, sy], up: [sx, sz], down: [sx, sz] };
        c.faces = {};
        for (const f of FACE_NAMES) {
          if ((c.opts.skip || []).includes(f)) { c.faces[f] = swatchFor(c.mat); continue; }
          const w = Math.max(2, Math.round(dims[f][0] * c.opts.s));
          const h = Math.max(2, Math.round(dims[f][1] * c.opts.s));
          const rec = { cube: c, face: f, w, h, cw: dims[f][0], ch: dims[f][1] };
          faces.push(rec); c.faces[f] = rec;
        }
      });
    }
    const all = faces.concat(Object.values(sw));
    const AW = 512, GUT = 2;
    all.sort((a, b) => b.h - a.h || b.w - a.w);
    let cx = 1, cy = 1, rowH = 0;
    for (const f of all) {
      if (cx + f.w + GUT > AW - 1) { cx = 1; cy += rowH + GUT; rowH = 0; }
      f.x = cx; f.y = cy; cx += f.w + GUT; rowH = Math.max(rowH, f.h);
    }
    const used = cy + rowH + 2;
    let AH = 256; while (AH < used) AH *= 2;
    if (AH > maxH) throw new Error('atlas too tall: ' + used);

    // ---- paint
    const tex = new Tex(AW, AH), glow = new Tex(AW, AH);
    for (const f of faces) {
      const painter = painters[f.cube.mat];
      if (!painter) throw new Error('no painter ' + f.cube.mat);
      painter(tex, { x: f.x, y: f.y, w: f.w, h: f.h }, f.face, { mat: f.cube.mat, s: f.cube.opts.s, cw: f.cw, ch: f.ch, o: f.cube.opts, glow });
    }
    for (const s of Object.values(sw)) {
      const col = swatches[s.mat] || [0, 0, 0, 0];
      for (let dy = 0; dy < s.h; dy++) for (let dx = 0; dx < s.w; dx++) tex.set(s.x + dx, s.y + dy, col);
    }
    for (const f of faces) {   // extrude each face edge by 1px into the gutter so filtering never bleeds a neighbour
      for (let x = 0; x < f.w; x++) { tex.set(f.x + x, f.y - 1, tex.get(f.x + x, f.y)); tex.set(f.x + x, f.y + f.h, tex.get(f.x + x, f.y + f.h - 1)); }
      for (let y = 0; y < f.h; y++) { tex.set(f.x - 1, f.y + y, tex.get(f.x, f.y + y)); tex.set(f.x + f.w, f.y + y, tex.get(f.x + f.w - 1, f.y + y)); }
    }
    tex.save(path.join(out, id + '.png'));
    glow.save(path.join(out, id + '_glowmask.png'));

    // ---- geometry
    const geoBones = bones.map((b) => {
      const piv = kit.absPivot(b.name);
      const o = { name: b.name, pivot: piv.map(r4) };
      if (b.parent) o.parent = b.parent;
      if (b.rot) o.rotation = b.rot;
      if (b.cubes.length) {
        o.cubes = b.cubes.map((c) => {
          const uv = {};
          for (const f of FACE_NAMES) uv[f] = { uv: [c.faces[f].x, c.faces[f].y], uv_size: [c.faces[f].w, c.faces[f].h] };
          const origin = [piv[0] + c.o[0], piv[1] + c.o[1], piv[2] + c.o[2]].map(r4);
          const cubeObj = { origin, size: c.sz.map(r4), uv };
          if (c.opts.inflate) cubeObj.inflate = c.opts.inflate;
          if (c.opts.rot) {
            cubeObj.pivot = [origin[0] + c.sz[0] / 2, origin[1] + (c.opts.pivotTop ? c.sz[1] : c.sz[1] / 2), origin[2] + c.sz[2] / 2].map(r4);
            cubeObj.rotation = c.opts.rot;
          }
          return cubeObj;
        });
      }
      return o;
    });
    const geo = {
      format_version: '1.12.0',
      'minecraft:geometry': [{
        description: { identifier: 'geometry.emily', texture_width: AW, texture_height: AH, visible_bounds_width: bounds.w, visible_bounds_height: bounds.h, visible_bounds_offset: bounds.off },
        bones: geoBones,
      }],
    };
    fs.writeFileSync(path.join(out, id + '.geo.json'), JSON.stringify(geo));

    // ---- animations: shared clip set + this entity's extra sway bones
    const anims = animFactory({ bones: bones.map((b) => b.name) });
    addSwayBones(anims, kit.sway, new Set(bones.map((b) => b.name)));
    fs.writeFileSync(path.join(out, id + '.animation.json'), JSON.stringify(anims, null, 1));

    const ncubes = bones.reduce((a, b) => a + b.cubes.length, 0);
    return { bones: bones.length, cubes: ncubes, atlas: AW + 'x' + AH, used };
  };
  return kit;
}

// add secondary motion for extra chain bones to the 'sway' (idle strength) and 'sway_walk' (strong) loops
function addSwayBones(anims, specs, have) {
  const N = 8;
  const keys = (arr) => { const o = {}; for (const [t, v] of arr) o[String(+t.toFixed(4))] = v; return o; };
  const sine = (len, base, amp, phase) => {
    const out = [];
    for (let i = 0; i <= N; i++) { const s = Math.sin((i / N) * Math.PI * 2 + phase); out.push([(len * i) / N, base.map((b, k) => +(b + amp[k] * s).toFixed(3))]); }
    return keys(out);
  };
  const clips = [['animation.emily.sway', 1], ['animation.emily.sway_walk', 3.2]];
  for (const [name, k] of clips) {
    const a = anims.animations[name];
    for (const s of specs) {
      s.bones.forEach((b, i) => {
        if (!have.has(b)) return;
        const grow = 1 + i * (s.grow === undefined ? 0.25 : s.grow);
        const kk = (name.endsWith('walk') ? (s.walk || 3.2) : 1);
        const ph = (s.phase || 0) + i * (s.step === undefined ? 0.55 : s.step);
        const e = (a.bones[b] = a.bones[b] || {});
        if (s.rot) e.rotation = sine(a.animation_length, [0, 0, 0], s.rot.map((v) => v * grow * kk), ph);
        if (s.pos) e.position = sine(a.animation_length, [0, 0, 0], s.pos.map((v) => v * (name.endsWith('walk') ? 1.5 : 1)), ph);
      });
    }
  }
}

module.exports = { makeKit, FACE_NAMES };
