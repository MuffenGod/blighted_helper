// More high-density painters (fabric, metal, horn, frill, gem, orb, strand, chain, web, spade). add(P, H, PAL) registers them on the painter map P.
const { hash } = require('../raster');
const { mixc, clamp } = require('../paint');
const { smooth, mulc } = require('./hd_paint_util');

function add(P, H, PAL) {
  const { R, ramp4, clear, fill, FACE_K } = H;

  // ------------------------------------------------------------------ fabric: draped cloth with fold shading, patterns, trims and embroidery
  // o: ramp, ramp2 (second colour for patterns), pat ('plain'|'diamond'|'stripe'|'check'|'dots'), pw (pattern cell, texels), folds, foldAmt,
  //    hem (fraction), hemRamp, hemStyle ('band'|'scallop'|'lace'), top (fraction), topRamp, edge (fraction of selvedge left/right), edgeRamp,
  //    band (centre embroidered band fraction), bandRamp, motif ('cross'|'chevron'|'vine'), outline, glowTrim, tint ([r,g,b])
  P.fabric = (t, r, f, ctx) => {
    const o = ctx.o || {}, Rm = R(o.ramp || 'cloth'), R2 = o.ramp2 ? R(o.ramp2) : null, Hm = R(o.hemRamp || 'gold'), Tm = R(o.topRamp || o.hemRamp || 'gold');
    const Em = R(o.edgeRamp || o.hemRamp || 'gold'), Bm = R(o.bandRamp || o.hemRamp || 'gold');
    const W = r.w, side = (f === 'east' || f === 'west'), fk = FACE_K[f] || 1;
    const folds = o.folds === undefined ? Math.max(2, Math.round(W / 5)) : o.folds, amt = o.foldAmt === undefined ? 0.6 : o.foldAmt, dth = o.dither === undefined ? 0.16 : o.dither;
    const pw = o.pw || 6;
    fill(t, r, (x, y, u, v) => {
      let Rc = Rm, glowK = 0;
      const ph = folds * u + (o.foldPhase || 0.12) + 0.05 * Math.sin(v * 5), tri = 1 - Math.abs(2 * (ph - Math.floor(ph)) - 1), fold = tri * tri * (3 - 2 * tri);
      let sh = 0.5 + (o.lift || 0) + amt * (fold - 0.5) * (0.5 + 0.8 * v) + 0.16 * (1 - v);
      if (o.pat === 'diamond' && R2) {
        const px = ((x % (2 * pw)) + 2 * pw) % (2 * pw), py = ((y % (2 * pw)) + 2 * pw) % (2 * pw);
        const dd = Math.abs(px - pw) + Math.abs(py - pw);
        if (dd < pw) Rc = R2;
        if (o.diamondLine !== false && Math.abs(dd - pw) < 0.9) sh -= 0.22;
      } else if (o.pat === 'stripe' && R2) { if (Math.floor(y / pw) % 2) Rc = R2; }
      else if (o.pat === 'check' && R2) { if ((Math.floor(x / pw) + Math.floor(y / pw)) % 2) Rc = R2; }
      else if (o.pat === 'dots' && R2) { const px = x % pw, py = (y + ((Math.floor(x / pw) % 2) * pw / 2)) % pw; if (Math.hypot(px - pw / 2 + 0.5, py - pw / 2 + 0.5) < pw * 0.3) Rc = R2; }
      let col = ramp4(Rc, sh, x, y, dth);
      if (o.tint) col = mixc(col, o.tint, 0.15);
      if (o.edge && (u < o.edge || u > 1 - o.edge) && !side) { col = ramp4(Em, 0.62 + 0.3 * Math.cos(v * 9) - 0.2 * (v > 0.5), x, y, 0.4); glowK = o.glowTrim || 0; }
      if (o.band && !side && Math.abs(u - 0.5) < o.band / 2) {
        const bu = (u - 0.5) / o.band + 0.5;
        const bs = 0.5 + 0.3 * (0.5 - Math.abs(bu - 0.5)) * 2 + 0.12 * Math.cos(v * 40);
        col = ramp4(Bm, bs, x, y, 0.35); glowK = o.glowTrim || 0;
        if (o.motif === 'chevron') { const cv = ((y + Math.abs(x - W / 2) * 1.2) % 8); if (cv < 1.4) col = Bm.deep; else if (cv < 2.6) col = Bm.hi; }
        else if (o.motif === 'cross') { const cy = y % 14, cxx = Math.abs(x - (W - 1) / 2); if ((cxx < 0.9 && cy > 2 && cy < 11) || (Math.abs(cy - 5) < 0.9 && cxx < 2.9)) col = Bm.deep; }
        else if (o.motif === 'vine') { const cx = (W - 1) / 2 + Math.sin(y * 0.55) * (o.band * W * 0.28); if (Math.abs(x - cx) < 0.9) col = Bm.deep; }
        if (Math.abs(bu - 0.5) > 0.44) col = Bm.deep;
      }
      if (o.top && v < o.top) { col = ramp4(Tm, 0.65 + 0.3 * Math.sin(u * Math.PI) - 0.2 * (v / o.top), x, y, 0.35); glowK = o.glowTrim || 0; }
      if (o.hem && v > 1 - o.hem) {
        const hv = (v - (1 - o.hem)) / o.hem;
        let skip = false;
        if (o.hemStyle === 'scallop') { const c = 0.5 + 0.5 * Math.cos(2 * Math.PI * (Math.max(2, Math.round(W / 4)) * u)); if (hv > 0.5 + 0.5 * (1 - c) * 0.8) { col = ramp4(Rc, sh - 0.15, x, y); skip = true; } }
        if (!skip) { col = ramp4(Hm, 0.45 + 0.5 * (1 - hv) + 0.15 * Math.cos(u * W * 1.4), x, y, 0.4); glowK = o.glowTrim || 0; }
        if (o.hemStyle === 'lace' && hv > 0.55 && ((x + y) % 3 === 0)) col = null;
        if (hv > 0.9 && col) col = mixc(col, Hm.deep, 0.5);
      }
      if (!col) return null;
      if (o.outline && !side && (x === 0 || x === W - 1)) col = mixc(col, Rc.deep, 0.65);
      if (ctx.glow && glowK) ctx.glow.set(r.x + x, r.y + y, [...mulc(Hm.hi, glowK), 255]);
      if (o.glow && ctx.glow && o.glowPat && o.glowPat(x, y, u, v)) ctx.glow.set(r.x + x, r.y + y, [...mulc(o.glow, 0.85), 255]);
      return mulc(col, fk);
    });
  };

  // ------------------------------------------------------------------ metal: bevelled plate / rod with rivets and engraving
  // o: ramp, bevel (texels), rivets, line ('h'|'v'), spec, glow (0..1 self-glow)
  P.metal = (t, r, f, ctx) => {
    const o = ctx.o || {}, Rm = R(o.ramp || 'gold'), bev = o.bevel === undefined ? 1 : o.bevel, W = r.w, Hh = r.h, fk = FACE_K[f] || 1;
    fill(t, r, (x, y, u, v) => {
      let sh = 0.5 + 0.28 * (1 - v) + 0.14 * (1 - u) - 0.1 * Math.abs(u - 0.5);
      if (bev > 0) { if (x < bev || y < bev) sh += 0.32; if (x >= W - bev || y >= Hh - bev) sh -= 0.34; }
      if (o.line === 'h' && y % 6 === 3 && x > 0 && x < W - 1) sh -= 0.25;
      if (o.line === 'v' && x % 5 === 2 && y > 0 && y < Hh - 1) sh -= 0.25;
      if (o.rivets && x % 6 === 2 && (y === 2 || y === Hh - 3)) sh += 0.4;
      if (o.spec && Math.abs((x - y * 0.5) % 9) < 1) sh += 0.18;
      if (o.inlay !== undefined) { const b = o.inlay; const edge = (x === b || y === b || x === W - 1 - b || y === Hh - 1 - b) && x >= b && y >= b && x <= W - 1 - b && y <= Hh - 1 - b; if (edge) sh -= 0.34; else if (x === b + 1 && y > b && y < Hh - b) sh += 0.1; }
      if (o.scroll) { const cx = (W - 1) / 2 + Math.sin(y * 0.55) * W * 0.2, cx2 = (W - 1) / 2 - Math.sin(y * 0.55) * W * 0.2; if ((Math.abs(x - cx) < 0.6 || Math.abs(x - cx2) < 0.6) && y > 2 && y < Hh - 3 && x > 1 && x < W - 2) sh -= 0.3; if (y % 9 === 4 && Math.abs(x - (W - 1) / 2) < 1) sh += 0.35; }
      if (o.ridge) { const d = Math.abs(u - 0.5); if (d < 0.07) sh += 0.3; else if (d < 0.2) sh += 0.08; else sh -= 0.1 * (d - 0.2) * 4; }
      if (o.hatch && ((x + y) % 5 === 0) && x > 1 && y > 1 && x < W - 2 && y < Hh - 2) sh -= 0.16;
      const col = ramp4(Rm, sh, x, y, 0.3);
      if (o.glow && ctx.glow) ctx.glow.set(r.x + x, r.y + y, [...mulc(Rm.hi, o.glow), 255]);
      return mulc(col, fk);
    });
  };

  // ------------------------------------------------------------------ horn: ridged, tapering, lit left edge, optional glowing tip
  // o: ramp, ring (texels per ridge), dir (+1 root at the top of the face), tipGlow [r,g,b], tipFrac, crack
  P.horn2 = (t, r, f, ctx) => {
    const o = ctx.o || {}, Rm = R(o.ramp || 'horn'), Hh = r.h, dir = o.dir === undefined ? 1 : o.dir, ring = o.ring || 5, fk = FACE_K[f] || 1;
    fill(t, r, (x, y, u, v) => {
      const a = dir > 0 ? v : 1 - v, along = dir > 0 ? y : Hh - 1 - y;
      const cyl = 1 - Math.pow(Math.abs(u - 0.38) * 1.7, 1.6);
      let sh = 0.3 + 0.5 * clamp(cyl, 0, 1) + 0.2 * a;
      const ry = along % ring;
      if (ry === 0) sh -= 0.34; else if (ry === 1) sh += 0.16;
      if (o.crack && hash(Math.floor(x / 2), Math.floor(along / 3), 4) > 0.9) sh -= 0.25;
      let col = ramp4(Rm, sh, x, y, 0.3);
      if (o.tipGlow && a > (o.tipFrac || 0.82)) { const k = smooth(o.tipFrac || 0.82, 1, a); col = mixc(col, o.tipGlow, k * 0.8); if (ctx.glow) ctx.glow.set(r.x + x, r.y + y, [...mulc(o.tipGlow, 0.9 * k), 255]); }
      return mulc(col, fk);
    });
  };

  // ------------------------------------------------------------------ frill: pleated lace / ruff, scalloped lower edge
  P.frill = (t, r, f, ctx) => {
    const o = ctx.o || {}, Rm = R(o.ramp || 'ruff'), Tm = o.trim ? R(o.trim) : null, pleat = o.pleat || 3, fk = FACE_K[f] || 1;
    fill(t, r, (x, y, u, v) => {
      const sh = 0.92 - 0.45 * Math.abs(Math.cos(Math.PI * (x / pleat))) * (0.5 + 0.7 * v);
      const cut = 1 - 0.2 * (0.4 + 0.6 * Math.abs(Math.cos(Math.PI * x / pleat)));
      if (v > 0.8 && v > cut && ((x + Math.floor(y / 2)) % 2 === 0)) return null;
      let col = ramp4(Rm, sh, x, y, 0.4);
      if (Tm && v > 0.78) col = ramp4(Tm, 0.7 - (v - 0.78), x, y, 0.4);
      if (o.lace && v > 0.55 && v < 0.78 && ((x + y) % 4 === 0)) col = Rm.dark;
      return mulc(col, fk);
    });
  };

  // ------------------------------------------------------------------ gem: faceted stone with its own glow
  P.gem = (t, r, f, ctx) => {
    const o = ctx.o || {}, Rm = R(o.ramp || 'azure'), W = r.w;
    fill(t, r, (x, y, u, v) => {
      const d = Math.max(Math.abs(u - 0.5), Math.abs(v - 0.5)) * 2;
      let sh = 0.95 - d * 0.55 + (u < 0.5 && v < 0.5 ? 0.15 : 0) - (u > 0.5 && v > 0.5 ? 0.15 : 0);
      if ((x + y) % Math.max(3, Math.round(W / 2)) === 0) sh += 0.12;
      const col = ramp4(Rm, sh, x, y, 0.2);
      if (ctx.glow) ctx.glow.set(r.x + x, r.y + y, [...mulc(mixc(Rm.mid, Rm.hi, 0.5), o.glow === undefined ? 0.85 : o.glow), 255]);
      return col;
    });
  };

  // ------------------------------------------------------------------ orb: round ball (bell, bead): corners cut on every face
  P.orb = (t, r, f, ctx) => {
    const o = ctx.o || {}, Rm = R(o.ramp || 'gold');
    fill(t, r, (x, y, u, v) => {
      const dx = (u - 0.5) * 2, dy = (v - 0.5) * 2, d = Math.hypot(dx, dy);
      if (d > 1.02) return null;
      const sh = 0.72 - 0.5 * d + 0.35 * clamp(1 - Math.hypot(dx + 0.4, dy + 0.45) * 1.5, 0, 1);
      let col = ramp4(Rm, sh, x, y, 0.3);
      if (o.slit && Math.abs(dy - 0.25) < 0.14 && Math.abs(dx) < 0.75) col = Rm.deep;
      if (ctx.glow && o.glow) ctx.glow.set(r.x + x, r.y + y, [...mulc(Rm.hi, o.glow * (1 - d * 0.5)), 255]);
      return col;
    });
  };

  // ------------------------------------------------------------------ strand: a lock of hair, lit left, highlight band, darker tip
  P.strand = (t, r, f, ctx) => {
    const o = ctx.o || {}, Rm = R(o.ramp || 'hair'), W = r.w, dir = o.dir === undefined ? 1 : o.dir, fk = FACE_K[f] || 1;
    const cell = Math.max(1, Math.round(W / 4));
    fill(t, r, (x, y, u, v) => {
      const a = dir > 0 ? v : 1 - v;
      const lim = 1 - Math.pow(smooth(0.7, 1.0, a), 1.2) * (o.taper === undefined ? 0.9 : o.taper);
      if (Math.abs(u - 0.5) * 2 > lim + 0.02 && W > 2) return null;
      const streak = (hash(Math.floor(x / cell), 3, 7) - 0.5) * 0.26;
      let sh = 0.5 + 0.2 * (1 - Math.abs(u - 0.3) * 1.6) + streak + 0.1 * Math.sin(a * Math.PI) - 0.1 * smooth(0.85, 1, a);
      if (Math.abs(a - 0.32 - 0.07 * Math.sin(u * 5 + (o.seed || 0))) < 0.035) sh += 0.28;
      if (hash(x, Math.floor(y / 3), 5) > 0.94) sh -= 0.16;
      let col = ramp4(Rm, sh, x, y, 0.2);
      if (o.tipRamp && a > 0.6) col = mixc(col, ramp4(R(o.tipRamp), sh, x, y, 0.2), smooth(0.6, 0.95, a));
      return mulc(col, fk);
    });
  };

  // ------------------------------------------------------------------ chain: alternating links
  P.chain = (t, r, f, ctx) => {
    const o = ctx.o || {}, Rm = R(o.ramp || 'gold');
    fill(t, r, (x, y) => { const k = (y % 4); return (k === 0 || k === 3) ? Rm.deep : (k === 1 ? Rm.hi : Rm.mid); });
  };

  // ------------------------------------------------------------------ web: a flat membrane sector between two wing fingers, veins radiating from the wrist, scalloped rim
  // o: ramp, bone, L (finger length, real units), sx (+1 sector opens toward +x of the finger frame, -1 toward -x), delta (sector angle, rad),
  //    scal (rim scallop depth), vein (count), glowVein [r,g,b]
  P.web = (t, r, f, ctx) => {
    if (f !== 'north' && f !== 'south') return clear(t, r);
    const o = ctx.o || {}, Rm = R(o.ramp || 'wingweb'), Bm = R(o.bone || 'wingbone'), W = r.w, Hh = r.h, L = o.L || 10, sx = o.sx || 1, delta = o.delta || 0.4;
    const unit = Hh / L, back = f === 'south', nv = o.vein || 3;
    fill(t, r, (x, y) => {
      let xr = back ? (W - 1 - x) : x; if (sx < 0) xr = W - 1 - xr;
      const px = (xr + 0.5) / unit, py = (y + 0.5) / unit;
      const ang = Math.atan2(px, py), rad = Math.hypot(px, py);
      if (ang < -0.03 || ang > delta) return null;
      const tt = clamp(ang / delta, 0, 1), scal = o.scal === undefined ? 0.28 : o.scal, sn = Math.sin(tt * Math.PI);
      const reach = L * (1 - scal * sn * (0.35 + 0.65 * sn)) * (1 + ((o.lerpTo === undefined ? 1 : o.lerpTo) - 1) * tt);
      if (rad > reach) return null;
      const e = rad / reach;
      let sh = 0.5 + 0.34 * (1 - e) + 0.14 * Math.cos(tt * Math.PI * nv);
      const veinPos = ((ang / delta) * nv) % 1, vein = Math.abs(veinPos) < 0.06 + 0.5 / (rad * unit + 1) && e > 0.12;
      if (vein) sh -= 0.2;
      if (e > 0.9) sh -= (e - 0.9) * 2.2;
      let col = ramp4(Rm, sh, x, y, 0.45);
      if (!o.noBone && ang < 0.02 + 0.3 / (rad * unit + 3)) col = ramp4(Bm, 0.55 + 0.2 * Math.cos(y * 0.9), x, y, 0.2);
      if (e > 0.955) col = mixc(col, Bm.mid, 0.65);
      if (o.glowVein && ctx.glow) {
        if (vein && e > 0.3) ctx.glow.set(r.x + x, r.y + y, [...mulc(o.glowVein, 0.5 * (0.4 + 0.6 * e)), 255]);
        else if (e > 0.945 && e <= 1) ctx.glow.set(r.x + x, r.y + y, [...mulc(o.glowVein, 0.55), 255]);
      }
      return mulc(col, FACE_K[f] || 1);
    });
  };

  // ------------------------------------------------------------------ panel: a flat polygon of wing membrane (the skin between the arm and her back)
  // o: ramp, box [minx, miny, w, h] of the cube in local units, poly [[x,y]...] (convex), apex [x,y] (the wrist: colours fade away from it), reach (units)
  P.panel = (t, r, f, ctx) => {
    if (f !== 'north' && f !== 'south') return clear(t, r);
    const o = ctx.o || {}, Rm = R(o.ramp || 'wingweb'), W = r.w, Hh = r.h, [bx, by, bw, bh] = o.box, poly = o.poly, back = f === 'south';
    const ap = o.apex || poly[0], reach = o.reach || 22;
    let area = 0; for (let i = 0; i < poly.length; i++) { const a = poly[i], b = poly[(i + 1) % poly.length]; area += a[0] * b[1] - b[0] * a[1]; }
    const sgn = area > 0 ? 1 : -1;
    fill(t, r, (x, y) => {
      const lx = bx + ((back ? W - 1 - x : x) + 0.5) / W * bw, ly = by + (y + 0.5) / Hh * bh;
      let dmin = 1e9;
      for (let i = 0; i < poly.length; i++) {
        const a = poly[i], b = poly[(i + 1) % poly.length], ex = b[0] - a[0], ey = b[1] - a[1], len = Math.hypot(ex, ey) || 1;
        dmin = Math.min(dmin, sgn * (ex * (ly - a[1]) - ey * (lx - a[0])) / len);
      }
      if (dmin < -0.3) return null;
      const rad = Math.hypot(lx - ap[0], ly - ap[1]), e = Math.min(1, rad / reach), ang = Math.atan2(lx - ap[0], ly - ap[1]);
      let sh = 0.5 + 0.34 * (1 - e) + 0.14 * Math.cos(ang * 9);
      const vp = ((ang * 7.5) % 1 + 1) % 1; if (Math.abs(vp - 0.5) < 0.07 + 0.3 / (rad * (W / bw) + 2) && e > 0.12) sh -= 0.2;
      return mulc(ramp4(Rm, sh, x, y, 0.45), FACE_K[f] || 1);
    });
  };

  // ------------------------------------------------------------------ spade: a flat tail tip / spike (diamond outline) with a glowing core
  P.spade = (t, r, f, ctx) => {
    if (f !== 'north' && f !== 'south' && f !== 'east' && f !== 'west') return clear(t, r);
    const o = ctx.o || {}, Rm = R(o.ramp || 'wingbone'), W = r.w;
    fill(t, r, (x, y, u, v) => {
      const lim = Math.min(1, v / (o.taperTop || 0.4)) * (1 - Math.pow(smooth(o.split || 0.62, 1.0, v), 1.4));
      if (Math.abs(u - 0.5) * 2 > lim) return null;
      let sh = 0.4 + 0.35 * (1 - Math.abs(u - 0.35) * 1.8) + 0.12 * Math.cos(v * 9);
      if (Math.abs(u - 0.5) < 0.5 / W + 0.04) sh += 0.3;
      let col = ramp4(Rm, sh, x, y, 0.3);
      if (o.glow && ctx.glow) { const k = (1 - Math.abs(u - 0.5) * 2) * 0.9; if (k > 0.25 && Math.abs(v - 0.55) < 0.2) { col = mixc(col, o.glow, 0.5); ctx.glow.set(r.x + x, r.y + y, [...mulc(o.glow, 0.7 * k), 255]); } }
      return mulc(col, FACE_K[f] || 1);
    });
  };
}

module.exports = { add };
