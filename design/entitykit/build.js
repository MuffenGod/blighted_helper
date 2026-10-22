// node build.js <variant|all> [--install] [--out dir]
//   builds design/entitykit/out/<id>.{geo.json,animation.json,png,_glowmask.png} and, with --install,
//   copies them into blighted_helper/src/main/resources/assets/blighted_helper/.
const fs = require('fs');
const path = require('path');
const { makeKit } = require('./core');
const { makePainters, swatchesFor } = require('./paint');
const rig = require('./rig');
const anim = require('./anim');

const ALL = ['emily', 'jester', 'demon', 'angel', 'astral'];
const args = process.argv.slice(2);
const oi = args.indexOf('--out');
const want = args.filter((a, i) => !a.startsWith('--') && (oi < 0 || i !== oi + 1));
const targets = want.length === 0 || want[0] === 'all' ? ALL : want;
const install = args.includes('--install');
const outDir = (() => { const i = args.indexOf('--out'); return i >= 0 ? args[i + 1] : path.join(__dirname, 'out'); })();
const ROOT = path.resolve(__dirname, '..', '..');
const RES = path.join(ROOT, 'blighted_helper', 'src', 'main', 'resources', 'assets', 'blighted_helper');

function buildOne(name) {
  const mod = require('./variants/' + name);
  const kit = makeKit();
  const painters = makePainters(mod.PAL, mod.V || {});
  Object.assign(painters, mod.painters ? mod.painters(mod.PAL, painters) : {});
  const V = rig.build(kit, { ...(mod.V || {}), kit });
  const res = kit.build({ id: mod.id, painters, swatches: { ...swatchesFor(mod.PAL), ...(mod.swatches || {}) }, out: outDir, bounds: mod.bounds, animFactory: (a) => anim({ bones: a.bones }) });
  console.log(mod.id.padEnd(8), 'bones', res.bones, 'cubes', res.cubes, 'atlas', res.atlas, 'used', res.used);
  if (install) {
    const put = (rel, f) => { const d = path.join(RES, rel); fs.mkdirSync(path.dirname(d), { recursive: true }); fs.copyFileSync(path.join(outDir, f), d); };
    put(`geo/${mod.id}.geo.json`, `${mod.id}.geo.json`);
    put(`animations/${mod.id}.animation.json`, `${mod.id}.animation.json`);
    put(`textures/entity/${mod.id}.png`, `${mod.id}.png`);
    put(`textures/entity/${mod.id}_glowmask.png`, `${mod.id}_glowmask.png`);
  }
  return kit;
}
for (const t of targets) buildOne(t);
