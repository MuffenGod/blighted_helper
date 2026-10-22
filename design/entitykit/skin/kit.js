// Authoring kit for the premium helper variants.
// Everything is authored in REAL model units (1 unit = 1 vanilla pixel, y is DOWN, -z is the front, +x is her left).
// A part marked `hd: k` (on its subtree root) is rendered at k x the texel density: finalize() multiplies the cube coordinates and the
// pivots of its descendants by k, and the root gets scale 1/k, so the part looks exactly as authored but is painted with k*k times the texels.
//
// part := { name, parent, variants:[V], pivot:[x,y,z], rot:[x,y,z], cubes:[{box:[x,y,z,w,h,d], mat, o, inflate, share, mirror}],
//           hd?, follow?, sway?, phys?, drive?, show? }
const PI = Math.PI;
const lerp = (a, b, t) => a + (b - a) * t;
const clamp = (v, a, b) => Math.max(a, Math.min(b, v));
const half = (v) => Math.round(v * 2) / 2;          // authoring granularity for hd parts (0.5 unit)

// ---------------------------------------------------------------------------------------------------------- basic builders
function P(name, parent, V, o) {
  return { name, parent, variants: [V], pivot: o.pivot || [0, 0, 0], rot: o.rot || [0, 0, 0], cubes: o.cubes || [], ...(o.hd ? { hd: o.hd } : {}),
    ...(o.follow ? { follow: o.follow } : {}), ...(o.sway ? { sway: o.sway } : {}), ...(o.phys ? { phys: o.phys } : {}), ...(o.drive ? { drive: o.drive } : {}),
    ...(o.show ? { show: o.show } : {}), ...(o.group ? { group: o.group } : {}), ...(o.ang !== undefined ? { ang: o.ang } : {}), ...(o.hidden ? { hidden: true } : {}) };
}
const cube = (x, y, z, w, h, d, mat, o, extra) => ({ box: [x, y, z, w, h, d], mat, o: o || {}, ...(extra || {}) });
// centred box helper: width w (x), height h (y, grows DOWN from y0), depth d (z)
const cbox = (w, h, d, y0 = 0) => [-w / 2, y0, -d / 2, w, h, d];

// A chain of n tapering segments. dir=+1 hangs down (+y, tails, tassels), dir=-1 grows up (-y, horns, hat points).
// seg(i, t) returns { rot:[x,y,z] (relative to the previous segment), len, w, d, mat, o, sway, extraCubes }
function chain(prefix, parent, V, n, first, seg, opt = {}) {
  const dir = opt.dir === undefined ? 1 : opt.dir;
  const out = [];
  let p = parent, prevLen = 0;
  for (let i = 0; i < n; i++) {
    const t = n === 1 ? 0 : i / (n - 1);
    const s = seg(i, t);
    const L = s.len, over = s.over === undefined ? 0.35 : s.over;      // a little overlap hides the gaps between rotated segments
    const y0 = dir > 0 ? 0 : -L - over, h = L + over;
    const cubes = [cube(-s.w / 2, y0, -s.d / 2, s.w, h, s.d, s.mat, s.o, s.cubeExtra), ...(s.extraCubes || [])];
    out.push(P(prefix + i, p, V, {
      pivot: i === 0 ? first.pivot : [0, dir * prevLen, 0], rot: i === 0 ? [first.rot[0] + s.rot[0], first.rot[1] + s.rot[1], first.rot[2] + s.rot[2]] : s.rot,
      cubes, sway: s.sway, phys: s.phys, hd: i === 0 ? opt.hd : undefined,
    }));
    p = prefix + i; prevLen = L;
  }
  return out;
}

// n panels evenly around a vertical axis, each facing outward (a ruff, a skirt, a halo)
function ring(prefix, parent, V, { n, R, rz, y, cx = 0, cz = 0, off = 0, tilt = 0, cubes, sway, phys, hd, roll = 0, group, skip }) {
  const out = [];
  for (let i = 0; i < n; i++) {
    const phi = ((360 * i) / n + off) * PI / 180;
    if (skip && skip(phi, i)) continue;
    out.push(P(prefix + i, parent, V, { pivot: [cx + R * Math.sin(phi), y, cz - (rz || R) * Math.cos(phi)], rot: [tilt, -phi, roll], cubes: cubes(i, phi), sway, phys, hd, group, ang: phi }));
  }
  return out;
}

// ---------------------------------------------------------------------------------------------------------- finalize: hd subtrees
// Returns parts with hd roots scaled and every cube/pivot of the subtree multiplied by k. Cube dims are snapped to whole texels.
function finalize(parts) {
  const byName = new Map(parts.map((p) => [p.name, p]));
  const kOf = new Map();
  const resolve = (p) => {
    if (kOf.has(p.name)) return kOf.get(p.name);
    let r = { k: 1, root: false };
    if (p.hd) r = { k: p.hd, root: true };
    else if (byName.has(p.parent)) { const pr = resolve(byName.get(p.parent)); if (pr.k > 1) r = { k: pr.k, root: false }; }
    kOf.set(p.name, r); return r;
  };
  return parts.map((p) => {
    const { k, root } = resolve(p);
    if (k === 1) return p;
    const q = { ...p };
    if (!root) q.pivot = p.pivot.map((v) => +(v * k).toFixed(3));
    else q.scale = [1 / k, 1 / k, 1 / k];
    q.cubes = p.cubes.map((c) => {
      const [x, y, z, w, h, d] = c.box;
      const sn = (v) => (v === 0 ? 0 : Math.max(1, Math.round(v * k)));
      return { ...c, box: [x * k, y * k, z * k, sn(w), sn(h), sn(d)], ...(c.inflate ? { inflate: c.inflate * k } : {}) };
    });
    if (p.hdK === undefined) q.hdK = k;
    return q;
  });
}

module.exports = { P, cube, cbox, chain, ring, finalize, PI, lerp, clamp, half };
