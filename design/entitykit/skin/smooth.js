// Smoothing kit: replacement meshes that follow Gaia's skeleton but have rounder silhouettes.
// Everything samples Gaia's own texture by re-using UV strips of the box it replaces, so the colours stay exactly the same.
const D45 = Math.PI / 4;

// An octagonal prism standing in for a w x h x d box (uv = its texOffs, origin = its addBox corner), chamfer c.
// Built from a cross of two boxes plus four 45-degree slabs. Returns parts parented to `parent`.
function octPrism(prefix, parent, variant, { uv, origin, w, h, d, c = 1, follow = null, pivot = [0, 0, 0] }) {
  const [u, v] = uv, [ox, oy, oz] = origin;
  const parts = [];
  const mk = (name, p, rot, cube) => ({ name: prefix + name, parent, variants: [variant], pivot: p, rot, cubes: [cube], ...(follow ? { follow } : {}) });
  // two cross boxes: full depth with a narrowed width, and full width with a narrowed depth
  parts.push(mk('A', pivot, [0, 0, 0], { uv: [u + c, v], box: [ox + c, oy, oz, w - 2 * c, h, d], mat: 'raw' }));
  parts.push(mk('B', pivot, [0, 0, 0], { uv: [u + 2 * c, v + 2 * c], box: [ox, oy, oz + c, w, h, d - 2 * c], mat: 'raw' }));
  // four diagonal slabs bridging the cut corners
  const cx = ox + w / 2, cz = oz + d / 2, mx = w / 2 - c / 2, mz = d / 2 - c / 2;
  [[1, 1], [1, -1], [-1, 1], [-1, -1]].forEach(([sx, sz], i) => {
    const slabW = Math.max(1, Math.round(c * 1.414));
    parts.push(mk('C' + i, [pivot[0] + cx + sx * mx, pivot[1], pivot[2] + cz + sz * mz], [0, sx * sz * D45, 0],
      { uv: [u + (sx > 0 ? w + d - 1 : 0), v + (sz > 0 ? 0 : 0)], box: [-slabW / 2, oy, -0.5, slabW, h, 1], mat: 'raw', inflate: 0.15 }));
  });
  return parts;
}

// re-parent every part of `variant` that hung off `from` onto `to`
function reparent(parts, variant, from, to) {
  for (const p of parts) if (p.variants.includes(variant) && p.parent === from) p.parent = to;
}

// Jester/any variant: a smoother head. Replaces Gaia's head (skull + hair crown + side locks + blink overlay) with a replica that follows it.
function smoothHead(variant) {
  const parts = [];
  parts.push({ name: 'x_head', parent: 'neck', variants: [variant], pivot: [0, 0, 0.5], rot: [0, 0, 0], follow: 'head',
    cubes: [
      { uv: [0, 0], box: [-3, -6, -3, 6, 6, 6], mat: 'raw' },                 // the skull with Gaia's own face
      { uv: [48, 37], box: [-3.4, -0.5, -2.5, 1, 5, 1], mat: 'raw' },          // front locks
      { uv: [48, 37], box: [2.4, -0.5, -2.5, 1, 5, 1], mat: 'raw', mirror: true },
    ] });
  parts.push({ name: 'x_headeyes', parent: 'x_head', variants: [variant], pivot: [0, 0, 0], rot: [0, 0, 0], follow: 'headeyes', followVisible: true,
    cubes: [{ uv: [24, 0], box: [-3, -6, -3.1, 6, 6, 0], mat: 'raw' }] });
  parts.push(...octPrism('x_hair', 'x_head', variant, { uv: [36, 0], origin: [-3.5, -6.5, -3.5], w: 7, h: 7, d: 7, c: 1 }));
  return { parts, hide: ['head'] };
}

module.exports = { octPrism, reparent, smoothHead };
