// One-off patch: adds the 'panel' painter and rewrites batWing so a membrane panel joins the arm to her back (run once, safe to re-run).
const fs = require('fs');
const path = require('path');
const here = __dirname;

// ---- painter: flat polygon of wing membrane (the skin between the arm and her back)
let m = fs.readFileSync(path.join(here, 'hd_paint_more.js'), 'utf8');
if (!m.includes('P.panel')) {
  const painter = [
    "  // ------------------------------------------------------------------ panel: a flat polygon of wing membrane (the skin between the arm and her back)",
    "  // o: ramp, box [minx, miny, w, h] of the cube in local units, poly [[x,y]...] (convex), apex [x,y] (the wrist: colours fade away from it), reach (units)",
    "  P.panel = (t, r, f, ctx) => {",
    "    if (f !== 'north' && f !== 'south') return clear(t, r);",
    "    const o = ctx.o || {}, Rm = R(o.ramp || 'wingweb'), W = r.w, Hh = r.h, [bx, by, bw, bh] = o.box, poly = o.poly, back = f === 'south';",
    "    const ap = o.apex || poly[0], reach = o.reach || 22;",
    "    let area = 0; for (let i = 0; i < poly.length; i++) { const a = poly[i], b = poly[(i + 1) % poly.length]; area += a[0] * b[1] - b[0] * a[1]; }",
    "    const sgn = area > 0 ? 1 : -1;",
    "    fill(t, r, (x, y) => {",
    "      const lx = bx + ((back ? W - 1 - x : x) + 0.5) / W * bw, ly = by + (y + 0.5) / Hh * bh;",
    "      let dmin = 1e9;",
    "      for (let i = 0; i < poly.length; i++) {",
    "        const a = poly[i], b = poly[(i + 1) % poly.length], ex = b[0] - a[0], ey = b[1] - a[1], len = Math.hypot(ex, ey) || 1;",
    "        dmin = Math.min(dmin, sgn * (ex * (ly - a[1]) - ey * (lx - a[0])) / len);",
    "      }",
    "      if (dmin < -0.3) return null;",
    "      const rad = Math.hypot(lx - ap[0], ly - ap[1]), e = Math.min(1, rad / reach), ang = Math.atan2(lx - ap[0], ly - ap[1]);",
    "      let sh = 0.5 + 0.34 * (1 - e) + 0.14 * Math.cos(ang * 9);",
    "      const vp = ((ang * 7.5) % 1 + 1) % 1; if (Math.abs(vp - 0.5) < 0.07 + 0.3 / (rad * (W / bw) + 2) && e > 0.12) sh -= 0.2;",
    "      return mulc(ramp4(Rm, sh, x, y, 0.45), FACE_K[f] || 1);",
    "    });",
    "  };",
    "",
    "  // ------------------------------------------------------------------ spade:",
  ].join('\n');
  const marker = '  // ------------------------------------------------------------------ spade:';
  if (!m.includes(marker)) throw new Error('spade marker not found');
  m = m.replace(marker, painter);
  fs.writeFileSync(path.join(here, 'hd_paint_more.js'), m);
  console.log('panel painter added');
}

// ---- builder
let w = fs.readFileSync(path.join(here, 'lib_wings.js'), 'utf8');
const i0 = w.indexOf('function batWing(V, s, o = {}) {');
const i1 = w.indexOf('module.exports.batWing');
if (i0 < 0 || i1 < 0) throw new Error('batWing not found');
const code = fs.readFileSync(path.join(here, 'batwing_new.txt'), 'utf8');
w = w.slice(0, i0) + code + '\n' + w.slice(i1);
fs.writeFileSync(path.join(here, 'lib_wings.js'), w);
console.log('batWing rewritten');
