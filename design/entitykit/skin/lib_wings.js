// Wing builders shared by the premium variants. All angles are authored for the LEFT wing (s=+1) and mirrored by s (-1 = right wing).
// In-wing-plane angles are measured from "straight down", positive = outward. The wing plane is the local XY plane (x outward, -y up); the root
// is turned about Y so the wing sweeps back from the shoulder blade.
const { P, cube, PI, lerp } = require('./kit');
const D = (deg) => (deg * PI) / 180;

// A feathered wing: two gilded bones and fans of separate feathers (primaries, secondaries, coverts).
function featherWing(V, s, o = {}) {
  const pre = o.prefix || ('x_aw' + (s > 0 ? 'l' : 'r'));
  const sh = o.shoulder || [2.0, -4.4, 2.0];
  const parts = [];
  const arm0L = o.arm0 || 6.5, arm1L = o.arm1 || 5.5, a0 = o.a0 === undefined ? 0.36 : o.a0, a1 = o.a1 === undefined ? 0.5 : o.a1;
  const ramp = o.ramp || 'feat', tip = o.tip === undefined ? 'gold' : o.tip, boneRamp = o.boneRamp || 'gold';
  const bone = (len, w) => cube(-w / 2, -len - 0.3, -0.35, w, len + 0.3, 0.7, 'metal', { ramp: boneRamp, bevel: 1, rivets: true });
  parts.push(P(pre + 'a0', 'bodytop', V, { pivot: [s * sh[0], sh[1], sh[2]], rot: [o.rx === undefined ? -0.12 : o.rx, -s * (o.ry === undefined ? 0.62 : o.ry), s * a0], hd: 1.5,
    cubes: [bone(arm0L, 1.2)] }));
  parts.push(P(pre + 'a1', pre + 'a0', V, { pivot: [0, -arm0L, 0], rot: [0, 0, s * a1], cubes: [bone(arm1L, 1.0)] }));
  const armSum = a0 + a1;
  const share = (key) => ({ share: (o.shareKey || V) + '_' + key, ...(s < 0 ? { mirror: true } : {}) });
  const feather = (name, key, parent, att, worldAng, len, w, z, opt) => {
    parts.push(P(name, parent, V, { pivot: [0, att, z], rot: [opt.rx || 0, 0, s * (-worldAng - (opt.sum === undefined ? armSum : opt.sum))],
      cubes: [cube(-w / 2, -0.4, -0.25, w, len + 0.4, 0.5, 'feather', { ramp, tip: tip || undefined, dir: 1, seed: opt.seed || 0, curl: (opt.curl || 0), round: opt.round, glowTip: o.glowTip, selfGlow: o.selfGlow === undefined ? 0.2 : o.selfGlow },
        share(key))],
      sway: [0.012, 0, 0.03, 0.09 + 0.01 * (opt.i || 0), (opt.i || 0) * 0.5], group: pre + 'fan', ang: worldAng }));
  };
  const NP = o.np || 9;
  for (let i = 0; i < NP; i++) {
    const t = i / (NP - 1);
    const ang = lerp(D(3), D(52), Math.pow(t, 0.95));
    const len = 15 + 11 * Math.sin(PI * (0.1 + 0.8 * t)) - 3 * t;
    feather(pre + 'p' + i, 'p' + i, pre + 'a1', -arm1L, ang, len, 3.0 - 0.5 * t, -0.15 - 0.012 * i, { i, seed: i, curl: 0.35 * t });
  }
  const NS = o.ns || 7;
  for (let i = 0; i < NS; i++) {
    const t = i / (NS - 1);
    feather(pre + 's' + i, 's' + i, pre + 'a1', -arm1L * (1 - 0.82 * t), lerp(D(2), D(26), t), 13 - 3 * t, 3.0, 0.2 + 0.01 * i, { i: i + 9, seed: 3 + i, curl: 0.2 });
  }
  const NC = o.nc || 7;
  for (let i = 0; i < NC; i++) {
    const t = i / (NC - 1);
    feather(pre + 'c' + i, 'c' + i, pre + 'a1', -arm1L * (0.9 - 0.8 * t), lerp(D(4), D(34), t), 8 - 1.2 * t, 2.6, 0.7 + 0.02 * i, { i: i + 4, seed: 7 + i, round: 0.8 });
  }
  for (let i = 0; i < 3; i++) {
    feather(pre + 'd' + i, 'd' + i, pre + 'a0', -arm0L * (0.3 + 0.3 * i), D(10 + 8 * i), 8, 2.6, 0.7, { i: i + 11, seed: 2 * i, round: 0.8, sum: a0 });
  }
  return parts;
}

module.exports = { featherWing, D };

// A DRAGON wing: a heavy two-bone arm that arches up and out, a wrist claw, four long tapering fingers and a fifth anchor bone running back to
// her spine, with one continuous membrane filling every gap (shallow scalloped edge). Much larger and sturdier than the first imp-like version.
// o: { ramp (web), bone, glowVein, a0, a1, arm0, arm1, ry, rx, shoulder, fingers:[{ang,len}], knuckle ramp, scal }
function batWing(V, s, o = {}) {
  const pre = o.prefix || ('x_bw' + (s > 0 ? 'l' : 'r'));
  const sh = o.shoulder || [2.1, -3.4, 1.1];
  const arm0L = o.arm0 || 6.0, arm1L = o.arm1 || 8.0, a0 = o.a0 === undefined ? 0.45 : o.a0, a1 = o.a1 === undefined ? 0.5 : o.a1;
  const rx = o.rx === undefined ? -0.14 : o.rx, ry = o.ry === undefined ? 0.4 : o.ry;
  const web = o.ramp || 'wingweb', bone = o.bone || 'wingbone';
  const fingers = o.fingers || [{ ang: D(128), len: 10 }, { ang: D(96), len: 21 }, { ang: D(70), len: 25 }, { ang: D(45), len: 25 }, { ang: D(20), len: 22 }];
  const parts = [];
  const rod = (len, w) => cube(-w / 2, -len - 0.3, -w / 2, w, len + 0.3, w, 'horn2', { ramp: bone, ring: 4, dir: -1 });
  const piv = [s * sh[0], sh[1], sh[2]];

  // where her lower back is, in this wing's frames: a point down her back at hip height, taken into the wing plane
  const mul = (A, B) => A.map((r, i) => B[0].map((_, j) => r.reduce((a, _, k) => a + A[i][k] * B[k][j], 0)));
  const Rx = (a) => [[1, 0, 0], [0, Math.cos(a), -Math.sin(a)], [0, Math.sin(a), Math.cos(a)]];
  const Ry = (a) => [[Math.cos(a), 0, Math.sin(a)], [0, 1, 0], [-Math.sin(a), 0, Math.cos(a)]];
  const Rz = (a) => [[Math.cos(a), -Math.sin(a), 0], [Math.sin(a), Math.cos(a), 0], [0, 0, 1]];
  const R0 = mul(Rz(s * a0), mul(Ry(-s * ry), Rx(rx)));
  const toA0 = (b) => { const d = [b[0] - piv[0], b[1] - piv[1], b[2] - piv[2]]; return [0, 1, 2].map((j) => R0[0][j] * d[0] + R0[1][j] * d[1] + R0[2][j] * d[2]); };
  const hip = toA0([s * (o.backX === undefined ? 0.8 : o.backX), o.backY === undefined ? 7.5 : o.backY, sh[2]]);   // [x, y, depth] in the shoulder frame
  const E = [0, -arm0L], c1 = Math.cos(s * a1), s1 = Math.sin(s * a1);
  const toA1 = (p) => { const dx = p[0] - E[0], dy = p[1] - E[1]; return [dx * c1 + dy * s1, -dx * s1 + dy * c1]; };   // shoulder frame -> elbow frame
  const B = toA1([hip[0], hip[1]]), Wl = [0, -arm1L], S1 = toA1([0, 0]);
  const dxy = [B[0] - Wl[0], B[1] - Wl[1]];
  const anchorLen = Math.hypot(dxy[0], dxy[1]);
  const theta = Math.atan2(-dxy[0], dxy[1]);                      // finger rotation (about z) that points along wrist -> hip
  const anchorAng = -theta * s - (a0 + a1);                       // same "angle from straight down" convention as the other fingers
  const all = fingers.concat([{ ang: anchorAng, len: anchorLen, anchor: true }]);

  parts.push(P(pre + 'a0', 'bodytop', V, { pivot: piv, rot: [rx, -s * ry, s * a0], hd: 1.5,
    cubes: [rod(arm0L, 2.2)], sway: [0.015, 0.0, 0.02, 0.07, 0.0] }));
  parts.push(P(pre + 'a1', pre + 'a0', V, { pivot: [0, -arm0L, 0], rot: [0, 0, s * a1],
    cubes: [rod(arm1L, 1.7), cube(-1.1, -arm1L - 1.0, -1.1, 2.2, 2.2, 2.2, 'orb', { ramp: o.knuckle || 'ruby', glow: 0.7 })], sway: [0.02, 0.0, 0.03, 0.08, 0.6] }));
  // heavy wrist claw
  parts.push(P(pre + 'th', pre + 'a1', V, { pivot: [0, -arm1L, 0.1], rot: [0, 0, s * (-2.1)], cubes: [cube(-0.9, 0, -0.6, 1.8, 2.6, 1.2, 'horn2', { ramp: bone, ring: 3, dir: 1 }), cube(-0.55, 2.4, -0.4, 1.1, 2.4, 0.8, 'horn2', { ramp: bone, ring: 3, dir: 1 })] }));
  // the skin between the arm and her back: shoulder, wrist and hip, a little oversized so it tucks under the arm bone and into her back
  {
    const poly = [S1, Wl, B];
    const minx = Math.min(...poly.map((p) => p[0])) - 0.5, maxx = Math.max(...poly.map((p) => p[0])) + 0.5;
    const miny = Math.min(...poly.map((p) => p[1])) - 0.5, maxy = Math.max(...poly.map((p) => p[1])) + 0.5;
    parts.push(P(pre + 'skin', pre + 'a1', V, { cubes: [cube(minx, miny, 0.05, maxx - minx, maxy - miny, 0, 'panel', { ramp: web, box: [minx, miny, maxx - minx, maxy - miny], poly, apex: Wl, reach: anchorLen })] }));
  }
  const armSum = a0 + a1;
  all.forEach((f, i) => {
    const next = all[i + 1];
    const L = f.len;
    if (f.anchor) { parts.push(P(pre + 'f' + i, pre + 'a1', V, { pivot: [0, -arm1L, 0.05 * i], rot: [0, 0, s * (-f.ang - armSum)], cubes: [] })); return; }   // the hip edge: fixed to the arm, not part of the fan spread
    const cubes = [cube(-0.65, -0.3, -0.65, 1.3, L * 0.5 + 0.3, 1.3, 'horn2', { ramp: bone, ring: 4, dir: 1 }), cube(-0.4, L * 0.5, -0.4, 0.8, L * 0.5, 0.8, 'horn2', { ramp: bone, ring: 4, dir: 1 })];
    const delta = f.ang - next.ang, wx = L * Math.sin(delta) * 1.15 + 0.8, sx = -s;
    cubes.push(cube(sx > 0 ? 0 : -wx, 0, 0, wx, L, 0, 'web', { ramp: web, bone, L, sx, delta, scal: o.scal === undefined ? 0.22 : o.scal, vein: 3, lerpTo: next.len / L, glowVein: o.glowVein }));
    parts.push(P(pre + 'f' + i, pre + 'a1', V, { pivot: [0, -arm1L, 0.05 * i], rot: [0, 0, s * (-f.ang - armSum)],
      cubes, sway: [0.01, 0.0, 0.035, 0.08, i * 0.7], group: pre + 'fan', ang: f.ang }));
  });
  return parts;
}

module.exports.batWing = batWing;
