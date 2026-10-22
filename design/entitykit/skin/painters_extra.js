// Low-resolution (1 px per unit) wing painters for the Gaia-model variants. Both paint a plane's west/east faces.
// The plane is rw (depth, root at u=0 unless flipU) by rh (height, top edge at v=0).
const { mixc, clamp } = require('../paint');
const { hash } = require('../raster');

function segDist(x, y, x1, y1, x2, y2) {
  const vx = x2 - x1, vy = y2 - y1, wx = x - x1, wy = y - y1;
  const t = clamp((wx * vx + wy * vy) / (vx * vx + vy * vy || 1), 0, 1);
  return Math.hypot(x - (x1 + vx * t), y - (y1 + vy * t));
}

function makeExtraPainters(PAL) {
  const P = {};
  const clear = (t, r) => { for (let y = 0; y < r.h; y++) for (let x = 0; x < r.w; x++) t.set(r.x + x, r.y + y, [0, 0, 0, 0]); };

  // bat wing: an arm along the top and four fingers from the root, scalloped membrane between them
  P.batwing = (t, r, f, ctx) => {
    if (f !== 'west' && f !== 'east') return clear(t, r);
    const o = ctx.o || {};
    const W = r.w, H = r.h;
    const root = [o.flipU ? W - 1 : 0, 1.5];
    const tips = [[1.0, 0.10], [0.94, 0.46], [0.68, 0.86], [0.30, 1.0]].map(([u, v]) => [o.flipU ? (1 - u) * (W - 1) : u * (W - 1), v * (H - 1)]);
    const ang = (p) => Math.atan2(p[1] - root[1], (p[0] - root[0]) * (o.flipU ? -1 : 1));
    const angs = tips.map(ang), lens = tips.map((p) => Math.hypot(p[0] - root[0], p[1] - root[1]));
    for (let y = 0; y < H; y++) for (let x = 0; x < W; x++) {
      const dx = (x + 0.5 - root[0]) * (o.flipU ? -1 : 1), dy = y + 0.5 - root[1];
      const a = Math.atan2(dy, dx), d = Math.hypot(dx, dy);
      let col = null, glow = false;
      // finger bones and the arm
      let onBone = false;
      for (let k = 0; k < tips.length; k++) if (segDist(x + 0.5, y + 0.5, root[0], root[1], tips[k][0], tips[k][1]) < (k === 0 ? 0.95 : 0.55)) onBone = true;
      if (onBone) col = mixc([12, 8, 14], PAL.wingBone, hash(x, y, 5) * 0.5);
      else if (a >= angs[0] && a <= angs[angs.length - 1]) {
        // which gap, and how far out the membrane reaches there (concave scallop between finger tips)
        let k = 0; while (k < angs.length - 2 && a > angs[k + 1]) k++;
        const t01 = (a - angs[k]) / ((angs[k + 1] - angs[k]) || 1);
        const lim = (lens[k] * (1 - t01) + lens[k + 1] * t01) * (1 - (o.concave === undefined ? 0.62 : o.concave) * Math.sin(t01 * Math.PI));
        if (d < lim) {
          const e = d / lim;
          col = mixc(PAL.wingMid, PAL.wingHi, 0.25 + 0.6 * e + hash(x, y, 9) * 0.12);
          if (e > 0.9) col = mixc(col, PAL.wingDeep, (e - 0.9) * 6);
          if (o.glowVeins && e > 0.55 && hash(x, y, 3) > 0.7 && ctx.glow) glow = true;
        }
      }
      if (col) {
        t.set(r.x + x, r.y + y, [...col, 255]);
        if (glow) ctx.glow.set(r.x + x, r.y + y, [...mixc(col, PAL.ember || col, 0.6), 255]);
      } else t.set(r.x + x, r.y + y, [0, 0, 0, 0]);
    }
  };

  // feathered wing: rows of overlapping feathers, short coverts at the root, long primaries toward the tip
  P.featherwing = (t, r, f, ctx) => {
    if (f !== 'west' && f !== 'east') return clear(t, r);
    const o = ctx.o || {};
    const W = r.w, H = r.h, NFEA = o.n || 6;
    for (let y = 0; y < H; y++) for (let x = 0; x < W; x++) {
      const u0 = (x + 0.5) / W, u = o.flipU ? 1 - u0 : u0, v = (y + 0.5) / H;
      const top = 0.10 * (1 - u) + 0.02;
      const fe = u * NFEA, k = Math.floor(fe), fr = fe - k;
      const reach = 0.30 + 0.70 * Math.pow(u, 0.8);                       // longer toward the tip
      const tipv = reach - 0.07 * (1 - Math.pow(2 * fr - 1, 2)) * (1 + u) + 0.02;
      if (v < top || v > tipv || u < 0.02) { t.set(r.x + x, r.y + y, [0, 0, 0, 0]); continue; }
      // coverts (top third) vs flight feathers
      const row = v < reach * 0.38 ? 0 : 1;
      let tone = 0.74 + 0.2 * (1 - v / tipv) + (row ? 0 : 0.06);
      let col = mixc([PAL.featDeep, PAL.featDark, PAL.featMid, PAL.featHi][Math.min(3, Math.floor(tone * 4))], PAL.featHi, clamp((tone - 0.75) * 2, 0, 1));
      if (fr < 0.10 || fr > 0.94) col = mixc(col, PAL.featDark, 0.6);                    // gap between feathers
      if (Math.abs(fr - 0.5) < 0.07 && v > top + 0.05) col = mixc(col, PAL.featDeep, 0.35);   // shaft
      if (row && v > tipv - 0.10) col = mixc(col, PAL.featTip, 0.35);
      if (v < top + 0.06) col = mixc(col, PAL.goldMid, 0.5);                                 // gilded leading edge
      t.set(r.x + x, r.y + y, [...col, 255]);
    }
  };
  return P;
}

module.exports = { makeExtraPainters };
