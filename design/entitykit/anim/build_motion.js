// node anim/build_motion.js [--install] [--dev]  -> out/motion.json (studio) and, with --install, the mod resource assets/blighted_helper/helper/motion.json
// Warns about channels that name a part the variant does not have (they would be silently skipped in game).
const fs = require('fs');
const path = require('path');
const ROOT = path.resolve(__dirname, '..', '..', '..');
const OUT = path.join(__dirname, '..', 'out', 'motion.json');
const PARTS = path.join(__dirname, '..', 'out', 'skin', 'variant_parts.json');
const STD = ['root', 'bodybottom', 'bodymiddle', 'bodytop', 'bodytop_female', 'neck', 'head', 'hair1', 'hair2', 'rightarm', 'rightarmlower', 'leftarm', 'leftarmlower',
  'rightwing', 'leftwing', 'skirt1', 'skirt2', 'skirt3', 'tail1', 'tail2', 'tail3', 'rightleg', 'rightleglower', 'leftleg', 'leftleglower'];

const data = { version: 1, variants: {} };
for (const v of ['jester', 'demon', 'angel', 'gaia']) {
  try { data.variants[v] = require('./m_' + v).motion; } catch (e) { if (e.code !== 'MODULE_NOT_FOUND') throw e; console.log('no motion for', v); }
}
if (fs.existsSync(PARTS)) {
  const parts = JSON.parse(fs.readFileSync(PARTS)).parts;
  for (const [v, m] of Object.entries(data.variants)) {
    const have = new Set([...STD, ...parts.filter((p) => p.variants.includes(v)).map((p) => p.name)]);
    const missing = new Map();
    const clips = [['idle', m.idle], ['walk', m.walk], ['run', m.run], ...(m.flourish || []).map((f, i) => ['f' + i, f])];
    for (const [cn, c] of clips) for (const t of (c && c.tracks) || []) {
      if (/^(fx|ev|spread)\./.test(t.c)) continue;
      const part = t.c.slice(0, t.c.lastIndexOf('.'));
      if (!have.has(part)) { if (!missing.has(part)) missing.set(part, new Set()); missing.get(part).add(cn); }
    }
    if (missing.size) console.log(`  ${v}: no part`, [...missing].map(([p, cs]) => `${p} (${[...cs].join(',')})`).join(', '));
  }
}
fs.mkdirSync(path.dirname(OUT), { recursive: true });
fs.writeFileSync(OUT, JSON.stringify(data));
console.log('motion.json', (JSON.stringify(data).length / 1024).toFixed(1), 'KB', Object.entries(data.variants).map(([k, m]) => `${k}: ${m.flourish ? m.flourish.length : 0} flourishes`).join(', '));
if (process.argv.includes('--dev')) {
  const d = 'C:/Users/srafa/bd/game/config/blighted_helper-motion.json';
  fs.copyFileSync(OUT, d);
  console.log('copied to the scratch game config', d);
}
if (process.argv.includes('--install')) {
  const dest = path.join(ROOT, 'blighted_helper', 'src', 'main', 'resources', 'assets', 'blighted_helper', 'helper', 'motion.json');
  fs.mkdirSync(path.dirname(dest), { recursive: true });
  fs.copyFileSync(OUT, dest);
  console.log('installed', dest);
}
