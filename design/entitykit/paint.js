// entitykit painters: procedural face painters, driven by a palette (PAL) and per-variant options (V.face).
// A painter fills one face rectangle:  painter(tex, {x,y,w,h}, face, {mat, s, cw, ch, o (cube opts), glow (Tex)})
// A ramp is four colours named <ramp>Hi, <ramp>Mid, <ramp>Dark (or Shad), <ramp>Deep in the palette.
const { hash } = require('./raster');

const hex = (h) => [parseInt(h.slice(1, 3), 16), parseInt(h.slice(3, 5), 16), parseInt(h.slice(5, 7), 16)];
const clamp = (v, a, b) => Math.max(a, Math.min(b, v));
const mixc = (a, b, t) => [Math.round(a[0] + (b[0] - a[0]) * t), Math.round(a[1] + (b[1] - a[1]) * t), Math.round(a[2] + (b[2] - a[2]) * t)];
const FACE_SHADE = { up: 1.05, north: 1.0, east: 0.92, west: 0.92, south: 0.86, down: 0.72 };
const mul = (c, k) => [clamp(Math.round(c[0] * k), 0, 255), clamp(Math.round(c[1] * k), 0, 255), clamp(Math.round(c[2] * k), 0, 255)];

function makePainters(PAL, V = {}) {
  const FACE = V.face || {};
  const ramp = (n) => ({
    hi: PAL[n + 'Hi'], mid: PAL[n + 'Mid'], dark: PAL[n + 'Dark'] || PAL[n + 'Shad'], deep: PAL[n + 'Deep'],
  });
  const clear = (t, r) => { for (let y = 0; y < r.h; y++) for (let x = 0; x < r.w; x++) t.set(r.x + x, r.y + y, [0, 0, 0, 0]); };
  const P = {};

  P.skin = (t, r, f, ctx) => {
    const fs = FACE_SHADE[f] || 1.0;
    for (let y = 0; y < r.h; y++) {
      const v = (y + 0.5) / r.h;
      for (let x = 0; x < r.w; x++) {
        const u = (x + 0.5) / r.w;
        const center = 1.0 - Math.abs(u - 0.5) * 1.3;
        let col = mixc(PAL.skinMid, PAL.skinHi, clamp(center * 0.45 + (1 - v) * 0.35, 0, 1));
        if (v > 0.75) col = mixc(col, PAL.skinShad, (v - 0.75) * 1.6);
        if (ctx.o.blushSpot && f === 'north') {
          const d = Math.hypot(u - 0.5, (v - 0.48) * 1.4);
          if (d < 0.45) col = mixc(col, PAL.blush, (0.45 - d) * 0.5);
        }
        if (ctx.o.cleavage && (f === 'north' || f === 'up')) {
          const cx = Math.abs(u - 0.5);
          if (cx < 0.09 && v > 0.20) col = mixc(col, PAL.skinDeep, (1.0 - cx / 0.09) * 0.85);
          else if (cx >= 0.09 && cx < 0.36 && v > 0.25) col = mixc(col, PAL.skinHi, Math.sin(((cx - 0.09) / 0.27) * Math.PI) * 0.55);
        }
        if (ctx.o.abs && (f === 'north')) {      // soft abdominal line + navel
          const cx = Math.abs(u - 0.5);
          if (cx < 0.045 && v > 0.12 && v < 0.88) col = mixc(col, PAL.skinDeep, 0.45);
          if (Math.hypot((u - 0.5) / 0.07, (v - 0.78) / 0.07) < 1) col = mixc(col, PAL.skinDeep, 0.7);
          for (const yy of [0.33, 0.55]) if (Math.abs(v - yy) < 0.02 && cx > 0.045 && cx < 0.32) col = mixc(col, PAL.skinShad, 0.5);
        }
        t.set(r.x + x, r.y + y, mul(col, fs));
      }
    }
  };

  P.hair = (t, r, f, ctx) => {
    const fs = FACE_SHADE[f] || 1.0;
    const isDark = !!ctx.o.dark, isCrown = !!ctx.o.crown, isTip = !!ctx.o.tip;
    const streak = PAL.hairStreak;                // optional accent streaks (ember red for the demon)
    for (let y = 0; y < r.h; y++) {
      const v = (y + 0.5) / r.h;
      for (let x = 0; x < r.w; x++) {
        const u = (x + 0.5) / r.w;
        const wave = Math.sin(u * Math.PI * 3.0) * 0.5 + 0.5;
        const fine = Math.sin(u * Math.PI * 7.0 + v * 1.2) * 0.5 + 0.5;
        let base = isDark ? mixc(PAL.hairDeep, PAL.hairShad, wave * 0.65 + fine * 0.2)
          : mixc(PAL.hairShad, PAL.hairLit, wave * 0.55 + fine * 0.2 + (1 - v) * 0.25);
        if (isCrown && f !== 'down') base = mixc(base, PAL.hairHi, Math.exp(-Math.pow((v - 0.36) / 0.14, 2)) * 0.65);
        else if (!isDark && f === 'north') base = mixc(base, PAL.hairHi, Math.exp(-Math.pow((v - 0.30) / 0.18, 2)) * 0.45);
        if (isTip && v > 0.65) base = mixc(base, PAL.hairShad, (v - 0.65) * 1.1);
        if (streak && (isDark || PAL.hairStreakAll) && hash(Math.floor(x / 2), 3, 41) > (PAL.streakT || 0.84) + (isTip ? -0.1 : 0) && v > 0.3) base = mixc(base, streak, 0.85);
        if (ctx.o.tipColor && v > 0.7) base = mixc(base, ctx.o.tipColor, clamp((v - 0.7) * 3.3, 0, 1) * 0.9);
        t.set(r.x + x, r.y + y, mul(base, fs));
      }
    }
  };

  // generic painted cloth: ramp (body colour) + trim (edge colour), optional hem/top/side bands and a vine of embroidery
  P.cloth = (t, r, f, ctx) => {
    const fs = FACE_SHADE[f] || 1.0;
    const o = ctx.o || {};
    const R = ramp(o.ramp || 'cloth'), T = ramp(o.trim || 'gold');
    for (let y = 0; y < r.h; y++) {
      const v = (y + 0.5) / r.h;
      for (let x = 0; x < r.w; x++) {
        const u = (x + 0.5) / r.w;
        if (o.goldTop && v < o.goldTop && (f === 'north' || f === 'up' || f === 'east' || f === 'west')) {
          t.set(r.x + x, r.y + y, mul(mixc(T.dark, T.hi, Math.sin(u * Math.PI)), fs)); continue;
        }
        if (o.goldHem && v > 1.0 - o.goldHem) {
          t.set(r.x + x, r.y + y, mul(mixc(T.dark, T.hi, 0.5 + 0.5 * Math.sin(u * Math.PI * (o.hemWaves || 4))), fs)); continue;
        }
        if (o.goldLeft && u < o.goldLeft && f === 'north') { t.set(r.x + x, r.y + y, mul(T.mid, fs)); continue; }
        if (o.goldRight && u > 1.0 - o.goldRight && f === 'north') { t.set(r.x + x, r.y + y, mul(T.mid, fs)); continue; }
        const sheen = Math.pow(Math.sin(u * Math.PI), 2) * 0.42;
        let col = mixc(R.deep, R.mid, clamp(sheen + (1 - v) * 0.18, 0, 1));
        if (o.fade) col = mixc(col, R.hi, clamp(o.fade * v, 0, 1));
        if (o.emb && f === 'north') {
          const curve = 0.5 + 0.22 * Math.sin(v * Math.PI * 2.0);
          if (Math.abs(u - curve) < 0.042 && v > 0.2 && v < 0.88) col = mixc(T.deep, T.mid, 1.0 - Math.abs(v - 0.55));
        }
        t.set(r.x + x, r.y + y, mul(col, fs));
      }
    }
  };

  P.scale = (t, r, f, ctx) => {
    const fs = FACE_SHADE[f] || 1.0;
    const R = ramp(ctx.o.ramp || 'scale');
    const S = Math.max(3.0, (ctx.s || 6) * 0.85);
    for (let y = 0; y < r.h; y++) {
      for (let x = 0; x < r.w; x++) {
        const row = Math.floor(y / (S * 0.7));
        const off = (row % 2) * (S * 0.5);
        const cx = (Math.floor((x + off) / S) + 0.5) * S - off;
        const cy = (row + 0.5) * (S * 0.7);
        const d = Math.hypot((x - cx) / (S * 0.52), (y - cy) / (S * 0.42));
        const dy = (y - cy) / (S * 0.42);
        let col;
        if (d > 0.86) col = R.deep;
        else if (dy > 0.25 && d > 0.55) col = R.hi;
        else col = mixc(R.dark, R.mid, 1.0 - d * 0.6);
        t.set(r.x + x, r.y + y, mul(col, fs));
      }
    }
  };

  P.horn = (t, r, f, ctx) => {
    const fs = FACE_SHADE[f] || 1.0;
    const R = ramp(ctx.o.ramp || 'scale'), T = ramp(ctx.o.trim || 'gold');
    for (let y = 0; y < r.h; y++) {
      const v = (y + 0.5) / r.h;
      for (let x = 0; x < r.w; x++) {
        const u = (x + 0.5) / r.w;
        const spec = Math.exp(-Math.pow((u - 0.35) / 0.18, 2));
        let col = mixc(R.deep, R.mid, spec * 0.85);
        if (spec > 0.75) col = mixc(col, R.hi, (spec - 0.75) * 2.2);
        if (ctx.o.goldInlay && f === 'north') {
          const veinU = 0.5 + 0.14 * Math.sin(v * Math.PI * 1.5);
          if (Math.abs(u - veinU) < 0.065) col = mixc(T.deep, T.mid, 1.0 - v * 0.4);
        }
        t.set(r.x + x, r.y + y, mul(col, fs));
      }
    }
  };

  // ridged ram/demon horn: bone-coloured rings, dark crack lines, glowing ember tip optional
  P.ramhorn = (t, r, f, ctx) => {
    const fs = FACE_SHADE[f] || 1.0;
    const R = ramp(ctx.o.ramp || 'horn');
    const ring = ctx.o.ring || 2.3;
    for (let y = 0; y < r.h; y++) {
      const v = (y + 0.5) / r.h;
      for (let x = 0; x < r.w; x++) {
        const u = (x + 0.5) / r.w;
        const spec = Math.exp(-Math.pow((u - 0.32) / 0.2, 2));
        let col = mixc(R.dark, R.mid, 0.35 + spec * 0.65);
        if (spec > 0.7) col = mixc(col, R.hi, (spec - 0.7) * 2.2);
        const rg = (y / (ctx.s || 6)) / ring;
        const fr = rg - Math.floor(rg);
        if (fr < 0.16) col = mixc(col, R.deep, 0.85);            // groove between ridges
        else if (fr < 0.26) col = mixc(col, R.hi, 0.18);         // lip of the ridge catching light
        if (hash(Math.floor(x / 2), Math.floor(y / 3), 77) > 0.93) col = mixc(col, R.deep, 0.5);   // pits
        if (ctx.o.tipGlow && v > 1 - ctx.o.tipGlow) {
          const k = (v - (1 - ctx.o.tipGlow)) / ctx.o.tipGlow;
          col = mixc(col, PAL.ember || [255, 120, 30], clamp(k * 1.3, 0, 1));
          if (ctx.glow && k > 0.3) ctx.glow.set(r.x + x, r.y + y, mixc(col, [0, 0, 0], 0.2));
        }
        if (ctx.o.ridge) col = mixc(col, R.deep, 0.0);
        t.set(r.x + x, r.y + y, mul(col, fs));
      }
    }
  };

  P.wingBone = (t, r, f, ctx) => {
    const fs = FACE_SHADE[f] || 1.0;
    const R = ramp((ctx && ctx.o && ctx.o.ramp) || 'scale');
    for (let y = 0; y < r.h; y++) for (let x = 0; x < r.w; x++) {
      const u = (x + 0.5) / r.w;
      t.set(r.x + x, r.y + y, mul(mixc(R.deep, R.mid, Math.exp(-Math.pow((u - 0.4) / 0.22, 2)) * 0.7), fs));
    }
  };

  P.gold = (t, r, f, ctx) => {
    const fs = FACE_SHADE[f] || 1.0;
    const T = ramp((ctx && ctx.o && ctx.o.ramp) || 'gold');
    const gk = ctx && ctx.o && ctx.o.glowK;
    for (let y = 0; y < r.h; y++) {
      const v = (y + 0.5) / r.h;
      for (let x = 0; x < r.w; x++) {
        const u = (x + 0.5) / r.w;
        const spec = Math.pow(Math.sin(u * Math.PI), 1.5) * 0.7 + (1 - v) * 0.3;
        const col = mul(mixc(T.dark, T.hi, clamp(spec, 0, 1)), fs);
        t.set(r.x + x, r.y + y, col);
        if (gk && ctx.glow) ctx.glow.set(r.x + x, r.y + y, mul(col, gk));
      }
    }
  };

  P.gem = (t, r, f, ctx) => {
    for (let y = 0; y < r.h; y++) {
      const v = (y + 0.5) / r.h;
      for (let x = 0; x < r.w; x++) {
        const u = (x + 0.5) / r.w;
        const d = Math.hypot(u - 0.5, v - 0.5) * 1.8;
        let col = mixc(PAL.gemHi, PAL.gemDeep, clamp(d, 0, 1));
        if (u < 0.45 && v < 0.45) col = PAL.gemHi;
        t.set(r.x + x, r.y + y, col);
        if (ctx.glow) ctx.glow.set(r.x + x, r.y + y, col);
      }
    }
  };

  P.glove = (t, r, f, ctx) => {
    const fs = FACE_SHADE[f] || 1.0;
    const R = ramp(ctx.o.ramp || 'cloth'), T = ramp(ctx.o.trim || 'gold');
    for (let y = 0; y < r.h; y++) {
      const v = (y + 0.5) / r.h;
      for (let x = 0; x < r.w; x++) {
        const u = (x + 0.5) / r.w;
        if (ctx.o.goldCuff && v < (ctx.o.cuffH || 0.12)) { t.set(r.x + x, r.y + y, mul(T.mid, fs)); continue; }
        const spec = Math.exp(-Math.pow((u - 0.38) / 0.18, 2));
        t.set(r.x + x, r.y + y, mul(mixc(R.deep, R.hi, spec * 0.65), fs));
      }
    }
  };

  P.shoe = (t, r, f, ctx) => {
    const fs = FACE_SHADE[f] || 1.0;
    const R = ramp(ctx.o.ramp || 'cloth'), T = ramp(ctx.o.trim || 'gold');
    for (let y = 0; y < r.h; y++) {
      const v = (y + 0.5) / r.h;
      for (let x = 0; x < r.w; x++) {
        const u = (x + 0.5) / r.w;
        let col = mixc(R.deep, R.hi, Math.exp(-Math.pow((u - 0.4) / 0.2, 2)) * 0.6);
        if (ctx.o.goldSole && v > 0.82) col = T.mid;
        t.set(r.x + x, r.y + y, mul(col, fs));
      }
    }
  };

  P.lash = (t, r) => { for (let y = 0; y < r.h; y++) for (let x = 0; x < r.w; x++) t.set(r.x + x, r.y + y, PAL.lash); };

  // bat / dragon wing membrane card: scalloped bottom edge, veins, optional glowing veins
  P.wingMemb = (t, r, f, ctx) => {
    if (f !== 'north' && f !== 'south') return clear(t, r);
    const o = ctx.o || {};
    const scallop = o.scallop === undefined ? 0.26 : o.scallop;
    for (let y = 0; y < r.h; y++) {
      const v = (y + 0.5) / r.h;
      for (let x = 0; x < r.w; x++) {
        const u = (x + 0.5) / r.w;
        if (o.wedge) {   // triangular card hugging its finger: the inner edge follows the next finger
          let ux = f === 'south' ? 1 - u : u; if (o.side < 0) ux = 1 - ux;
          if (ux < 1 - v * o.wedge - 0.03) { t.set(r.x + x, r.y + y, [0, 0, 0, 0]); continue; }
        }
        const arch = 0.94 - scallop * Math.sin(u * Math.PI);
        if (v > arch) { t.set(r.x + x, r.y + y, [0, 0, 0, 0]); continue; }
        if (v < 0.07) { t.set(r.x + x, r.y + y, [...PAL.wingBone, 255]); continue; }
        const vein1 = Math.pow(0.5 + 0.5 * Math.sin(u * 22.0 + v * 8.0 * Math.sin(u * 6.0)), 7);
        const vein2 = Math.pow(0.5 + 0.5 * Math.cos(u * 17.0 - v * 12.0), 9);
        const vein = clamp(vein1 + vein2, 0, 1);
        const rim = Math.exp(-Math.pow((v - arch) / 0.04, 2));
        let col = mixc(PAL.wingDeep, PAL.wingMid, Math.sin(u * Math.PI) * 0.8 + (1 - v) * 0.2);
        col = mixc(col, PAL.wingHi, vein * 0.8);
        col = mixc(col, PAL.wingDeep, rim * 0.9);
        if (o.fireGrad) col = mixc(col, PAL.wingHot || PAL.wingHi, clamp((v - 0.35) * 1.4, 0, 1) * 0.55);
        t.set(r.x + x, r.y + y, [...col, 255]);
        if (o.glowVeins && ctx.glow && vein > 0.55) ctx.glow.set(r.x + x, r.y + y, mixc(col, PAL.ember || col, 0.5));
      }
    }
  };

  // a single feather card (angel wings): rounded tip, shaft, barbs, tip tint
  P.feather = (t, r, f, ctx) => {
    if (f !== 'north' && f !== 'south') return clear(t, r);
    const o = ctx.o || {};
    const R = ramp('feat');
    const tip = o.tip === undefined ? 0.2 : o.tip;
    for (let y = 0; y < r.h; y++) {
      const v = (y + 0.5) / r.h;                      // 0 at the quill base, 1 at the tip
      for (let x = 0; x < r.w; x++) {
        const u = (x + 0.5) / r.w;
        const du = Math.abs(u - 0.5);
        let hw;
        if (v < 0.12) hw = 0.14 + 0.36 * (v / 0.12);
        else hw = 0.5 * Math.sqrt(Math.max(0, 1 - Math.pow((v - 0.12) / 0.88, 3.0))) ;
        if (du > hw) { t.set(r.x + x, r.y + y, [0, 0, 0, 0]); continue; }
        let col = mixc(R.dark, R.hi, clamp(0.35 + (1 - v) * 0.25 + (0.5 - du) * 0.5, 0, 1));
        const barb = Math.sin(((u - 0.5) * 26 + v * (r.h / 3.2)) * (u > 0.5 ? 1 : -1));
        if (barb > 0.82) col = mixc(col, R.dark, 0.5);
        if (du < 0.028) col = mixc(R.mid, R.deep, 0.35);                   // shaft
        if (du > hw - 0.05) col = mixc(col, R.hi, 0.5);                    // rim light
        if (v < 0.35) col = mixc(col, R.dark, (0.35 - v) * 1.1);           // shaded base under the covering feathers
        if (v > 1 - tip) col = mixc(col, PAL.featTip, clamp((v - (1 - tip)) / tip, 0, 1) * 0.8);
        t.set(r.x + x, r.y + y, [...col, 255]);
        if (o.glowTip && ctx.glow && v > 1 - tip * 0.8) ctx.glow.set(r.x + x, r.y + y, mixc(col, PAL.featTip, 0.5));
      }
    }
  };

  // plate armour: gunmetal ramp, bevel, rivets, optional coloured edge line and glowing runes
  P.armor = (t, r, f, ctx) => {
    const fs = FACE_SHADE[f] || 1.0;
    const o = ctx.o || {};
    const R = ramp(o.ramp || 'armor'), E = o.edge ? ramp(o.edge) : null;
    for (let y = 0; y < r.h; y++) {
      const v = (y + 0.5) / r.h;
      for (let x = 0; x < r.w; x++) {
        const u = (x + 0.5) / r.w;
        let col = mixc(R.deep, R.mid, clamp(0.25 + Math.sin(u * Math.PI) * 0.5 + (1 - v) * 0.2, 0, 1));
        const brush = hash(0, y, 9) * 0.12;
        col = mixc(col, R.hi, brush);
        if (x < 1 || y < 1) col = mixc(col, R.hi, 0.55);                          // top/left bevel
        if (x >= r.w - 1 || y >= r.h - 1) col = mixc(col, R.deep, 0.7);            // bottom/right bevel
        if (E && (o.edgeAt === 'top' ? v < 0.14 : o.edgeAt === 'bottom' ? v > 0.86 : (x < 2 || y < 2 || x >= r.w - 2 || y >= r.h - 2))) col = mixc(E.dark, E.hi, 0.5 + 0.5 * Math.sin(u * 9));
        if (o.rivets && ((x === 2 || x === r.w - 3) && (y === 2 || y === r.h - 3))) col = R.hi;
        if (o.rune && f === 'north' && Math.abs(u - 0.5) < 0.03 && v > 0.15 && v < 0.85) {
          col = PAL.ember || col;
          if (ctx.glow) ctx.glow.set(r.x + x, r.y + y, PAL.ember || col);
        }
        t.set(r.x + x, r.y + y, mul(col, fs));
      }
    }
  };

  // two-colour cloth (hat halves, motley dress). a, b = ramp names. axis u splits left/right, v top/bottom
  P.split = (t, r, f, ctx) => {
    const fs = FACE_SHADE[f] || 1.0;
    const o = ctx.o || {};
    const A = ramp(o.a), B = ramp(o.b), T = ramp(o.trim || 'gold');
    const band = o.band !== undefined ? o.band : o.goldHem, bandTop = o.bandTop !== undefined ? o.bandTop : o.goldTop;
    for (let y = 0; y < r.h; y++) {
      const v = (y + 0.5) / r.h;
      for (let x = 0; x < r.w; x++) {
        const u0 = (x + 0.5) / r.w, u = f === 'south' ? 1 - u0 : u0;
        let pick, local;
        if (f === 'east') { pick = o.eastB ? B : A; local = u; }
        else if (f === 'west') { pick = o.eastB ? A : B; local = u; }
        else if (o.axis === 'v') { pick = v < 0.5 ? A : B; local = u; }
        else { pick = u < 0.5 ? A : B; local = u < 0.5 ? u * 2 : (u - 0.5) * 2; }
        const sheen = Math.pow(Math.sin(local * Math.PI), 2) * 0.55;
        let col = mixc(pick.deep, pick.mid, clamp(sheen + (1 - v) * 0.15, 0, 1));
        if (sheen > 0.38) col = mixc(col, pick.hi, (sheen - 0.38) * 1.1);
        if (o.stitch && Math.abs(u - 0.5) < 0.012 && f !== 'east' && f !== 'west') col = pick.deep;   // seam between the halves
        if (band && v > 1 - band) col = mixc(T.dark, T.hi, 0.5 + 0.5 * Math.sin(u * Math.PI * 6));
        if (bandTop && v < bandTop) col = mixc(T.dark, T.hi, 0.5 + 0.5 * Math.sin(u * Math.PI * 6));
        t.set(r.x + x, r.y + y, mul(col, fs));
      }
    }
  };

  // harlequin diamonds in two ramps, with a dark outline between cells
  P.checker = (t, r, f, ctx) => {
    const fs = FACE_SHADE[f] || 1.0;
    const o = ctx.o || {};
    const A = ramp(o.a), B = ramp(o.b), T = ramp(o.trim || 'gold');
    const ck = o.ck || 5;
    for (let y = 0; y < r.h; y++) {
      const v = (y + 0.5) / r.h;
      for (let x = 0; x < r.w; x++) {
        const u = (x + 0.5) / r.w;
        const px = x + (o.ox || 0), py = y + (o.oy || 0);
        const cx = Math.floor((px + py) / ck), cy = Math.floor((px - py) / ck);
        const odd = (cx + cy) & 1;
        const pick = odd ? B : A;
        const lx = ((px + py) % ck + ck) % ck, ly = ((px - py) % ck + ck) % ck;
        const edge = Math.min(lx, ck - 1 - lx, ly, ck - 1 - ly);
        const tone = clamp(0.35 + Math.sin(u * Math.PI) * 0.35 + (1 - v) * 0.12 + (edge === 1 ? 0.1 : 0), 0, 1);
        let col = mixc(pick.deep, pick.mid, tone);
        if (tone > 0.62) col = mixc(col, pick.hi, (tone - 0.62) * 1.3);
        if (edge === 0) col = mixc(col, [10, 8, 16], 0.6);
        if (o.band && v > 1 - o.band) col = mixc(T.dark, T.hi, 0.5 + 0.5 * Math.sin(u * Math.PI * 6));
        if (o.bandTop && v < o.bandTop) col = mixc(T.dark, T.hi, 0.5 + 0.5 * Math.sin(u * Math.PI * 6));
        t.set(r.x + x, r.y + y, mul(col, fs));
      }
    }
  };

  // ruffled collar / cuff: cream with soft lavender folds and a frayed lower edge
  P.ruff = (t, r, f, ctx) => {
    const fs = FACE_SHADE[f] || 1.0;
    const R = ramp(ctx.o.ramp || 'ruff');
    for (let y = 0; y < r.h; y++) {
      const v = (y + 0.5) / r.h;
      for (let x = 0; x < r.w; x++) {
        const u = (x + 0.5) / r.w;
        const fold = Math.sin(u * Math.PI * (ctx.o.folds || 5) + v * 1.5);
        let col = mixc(R.dark, R.hi, clamp(0.55 + fold * 0.3 + (1 - v) * 0.12, 0, 1));
        if (fold < -0.82) col = mixc(col, R.deep, 0.5);
        if (v > 0.9) col = mixc(col, R.dark, (v - 0.9) * 5);
        t.set(r.x + x, r.y + y, mul(col, fs));
      }
    }
  };

  // ---------------------------------------------------------------------------- face
  function sampleFace(u, v) {
    let col = mixc(PAL.skinHi, PAL.skinMid, clamp(v * 0.7, 0, 1));
    const edgeX = Math.max(0, Math.abs(u - 0.5) * 2.0 - 0.72) / 0.28;
    if (edgeX > 0) col = mixc(col, PAL.skinShad, edgeX * 0.4);
    if (v > 0.88) col = mixc(col, PAL.skinShad, (v - 0.88) / 0.12 * 0.4);
    if (v < 0.34) col = mixc(col, PAL.skinShad, (0.34 - v) / 0.34 * 0.35);
    let glow = false;

    for (const bx of [0.20, 0.80]) {
      const d = Math.hypot((u - bx) / 0.13, (v - 0.74) / 0.06);
      if (d < 1.0) col = mixc(col, PAL.blush, (1.0 - d) * (FACE.blush === undefined ? 0.52 : FACE.blush));
    }
    for (const side of [-1, 1]) {
      const ex = 0.5 + side * 0.235, ey = 0.585, hw = 0.148, hh = 0.138;
      const dx = (u - ex) / hw, dy = (v - ey) / hh;
      const topArch = -0.88 + 0.16 * (dx * dx) + side * dx * (FACE.tilt === undefined ? 0.06 : FACE.tilt);
      const botArch = 0.92 - 0.10 * (dx * dx);
      if (Math.abs(dx) <= 1.02 && dy >= topArch && dy <= botArch) {
        const lidShad = clamp((0.0 - dy) * 0.55, 0, 0.32);
        col = mixc([253, 250, 255], [204, 194, 218], lidShad);
        const idx = (u - (ex - side * 0.012)) / (hw * 0.76);
        const idy = (v - (ey + 0.015)) / (hh * 0.92);
        const ir = Math.hypot(idx * 0.92, idy);
        if (ir <= 1.0) {
          const gy = clamp((idy + 0.85) / 1.75, 0, 1);
          let icol = mixc(PAL.gemDeep, PAL.gemMid, Math.pow(gy, 0.82));
          if (gy > 0.58 && ir < 0.82) icol = mixc(icol, PAL.gemHi, (gy - 0.58) / 0.42 * 0.8);
          if (ir > 0.80) icol = mixc(icol, PAL.gemDeep, (ir - 0.80) / 0.20 * 0.88);
          if (idy > 0.30 && ir > 0.42 && ir < 0.76) icol = mixc(icol, PAL.gemHi, 0.6);
          const px = Math.abs(idx), py = Math.abs(idy);
          if ((FACE.pupil || 'slit') === 'slit') {
            if (py < 0.74 && px < 0.14 * (1.0 - 0.6 * py * py)) icol = FACE.pupilCol || [5, 20, 12];
          } else if (FACE.pupil === 'round') {
            if (Math.hypot(px, py) < 0.34) icol = FACE.pupilCol || [8, 18, 40];
          } else if (FACE.pupil === 'none') {
            // glowing eyes with no pupil
          }
          col = icol; glow = true;
        }
        const h1 = Math.hypot((u - (ex - 0.050)) / 0.036, (v - (ey - 0.044)) / 0.034);
        const h2 = Math.hypot((u - (ex + 0.046)) / 0.020, (v - (ey + 0.054)) / 0.020);
        if (h1 <= 1.0 || h2 <= 1.0) { col = [255, 255, 255]; glow = false; }
      }
      if (Math.abs(dx) <= 1.15) {
        const lashY = topArch * hh + ey;
        const distTop = v - lashY;
        if (distTop >= -0.042 && distTop <= 0.015) { col = PAL.lash; glow = false; }
        if (distTop >= -0.064 && distTop < -0.048 && Math.abs(dx) < 0.85) col = mixc(col, PAL.skinDeep, 0.65);
        // optional lash flick (demon / jester): wings the outer corner up
        if (FACE.wing && dx * side > 0.55 && dx * side < 1.35 && Math.abs(v - (lashY - (dx * side - 0.55) * 0.08)) < 0.014) { col = PAL.lash; glow = false; }
      }
      if (dx * side > 0.20 && Math.abs(dx) <= 1.0 && Math.abs(v - (ey + botArch * hh)) < 0.011) { col = mixc(col, PAL.lash, 0.75); glow = false; }
      const browY = ey - hh * 1.32 + 0.016 * Math.pow(dx - side * 0.2, 2) - side * dx * (FACE.browTilt === undefined ? 0.015 : FACE.browTilt);
      if (Math.abs(dx) <= 0.98 && Math.abs(v - browY) < (FACE.browThick || 0.014)) { col = PAL.brow || PAL.hairDeep; glow = false; }
    }
    if (Math.hypot((u - 0.5) / 0.016, (v - 0.735) / 0.013) <= 1.0) col = PAL.skinDeep;
    const mx = (u - 0.5) / 0.105;
    if (Math.abs(mx) <= 1.0) {
      const smileCurve = 0.835 + (FACE.smile === undefined ? 0.020 : FACE.smile) * (1.0 - mx * mx);
      const dv = v - smileCurve;
      if (Math.abs(dv) < 0.010) col = PAL.lip;
      else if (dv > 0 && dv < 0.016 * (1.0 - mx * mx)) col = mixc(PAL.lip, PAL.blush, 0.45);
      if (FACE.fang !== false && mx > -0.68 && mx < -0.40 && dv > 0.004 && dv < 0.030 * (1.0 - Math.abs(mx + 0.54) / 0.14)) col = [255, 255, 255];
    }
    if (FACE.extra) { const res = FACE.extra(u, v, col, { mixc, clamp, PAL }); if (res) { col = res.col; if (res.glow !== undefined) glow = res.glow; } }
    return { col, glow };
  }

  P.headface = (t, r, f, ctx) => {
    if (f !== 'north') return P.skin(t, r, f, ctx);
    const SS = 3;
    for (let y = 0; y < r.h; y++) {
      for (let x = 0; x < r.w; x++) {
        let rr = 0, gg = 0, bb = 0, gl = 0;
        for (let sy = 0; sy < SS; sy++) for (let sx = 0; sx < SS; sx++) {
          const s = sampleFace((x + (sx + 0.5) / SS) / r.w, (y + (sy + 0.5) / SS) / r.h);
          rr += s.col[0]; gg += s.col[1]; bb += s.col[2];
          if (s.glow) gl++;
        }
        const c = [Math.round(rr / (SS * SS)), Math.round(gg / (SS * SS)), Math.round(bb / (SS * SS))];
        t.set(r.x + x, r.y + y, c);
        if (ctx.glow && gl > (SS * SS) * 0.5) ctx.glow.set(r.x + x, r.y + y, c);
      }
    }
  };

  P.ov_blink = (t, r, f) => {
    if (f !== 'north') return clear(t, r);
    for (let y = 0; y < r.h; y++) {
      const v = (y + 0.5) / r.h;
      for (let x = 0; x < r.w; x++) {
        const u = (x + 0.5) / r.w;
        let hit = false, col = PAL.skinMid;
        for (const side of [-1, 1]) {
          const dx = (u - (0.5 + side * 0.235)) / 0.155;
          if (Math.abs(dx) <= 1.05 && v >= 0.06 && v <= 0.94) {
            hit = true;
            if (Math.abs(v - (0.68 - 0.15 * (1.0 - dx * dx))) < 0.11) col = PAL.lash;
            else col = mixc(PAL.skinMid, PAL.skinShad, v * 0.35);
          }
        }
        t.set(r.x + x, r.y + y, hit ? [...col, 255] : [0, 0, 0, 0]);
      }
    }
  };

  P.ov_grin = (t, r, f) => {
    if (f !== 'north') return clear(t, r);
    for (let y = 0; y < r.h; y++) {
      const v = (y + 0.5) / r.h;
      for (let x = 0; x < r.w; x++) {
        const u = (x + 0.5) / r.w;
        const dx = (u - 0.5) / 0.42, dy = (v - 0.48) / 0.38;
        if (dx * dx + dy * dy <= 1.0) {
          if (v < 0.32 && (Math.abs(dx + 0.45) < 0.16 || Math.abs(dx - 0.45) < 0.16)) t.set(r.x + x, r.y + y, [255, 255, 255, 255]);
          else t.set(r.x + x, r.y + y, [...PAL.lip, 255]);
        } else t.set(r.x + x, r.y + y, [0, 0, 0, 0]);
      }
    }
  };

  return P;
}

// default swatch colours for faces that are skipped (cubes drawn with a flat 2x2 colour)
function swatchesFor(PAL) {
  return {
    skin: PAL.skinMid, hair: PAL.hairMid, cloth: PAL.clothDark, scale: PAL.scaleDark, horn: PAL.scaleDeep,
    wingBone: PAL.scaleDeep, gold: PAL.goldMid, gem: PAL.gemMid, glove: PAL.clothDark, shoe: PAL.clothDeep, headface: PAL.skinMid,
  };
}

module.exports = { makePainters, swatchesFor, hex, mixc, clamp };
