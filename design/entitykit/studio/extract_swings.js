// Reads the attack key poses out of HelperSwings.java so the studio plays the same combos as the game.
// Returns { SWORD: [{ name, windup, strike, follow, spin, leftDelay }, ...], DAGGER: [...], ... }
const fs = require('fs'), path = require('path');
const SRC = path.resolve(__dirname, '..', '..', '..', 'blighted_helper', 'src', 'main', 'java', 'com', 'blighteddreams', 'helper', 'client', 'HelperSwings.java');

function num(s) {
  s = s.trim().replace(/[fF]$/, '');
  if (s === 'TAU') return Math.PI * 2;
  if (s === '-TAU') return -Math.PI * 2;
  return +s;
}

// split the text of one call's arguments at depth-0 commas
function args(text) {
  const out = []; let d = 0, cur = '';
  for (const ch of text) {
    if (ch === '(') d++;
    if (ch === ')') d--;
    if (ch === ',' && d === 0) { out.push(cur.trim()); cur = ''; continue; }
    cur += ch;
  }
  if (cur.trim()) out.push(cur.trim());
  return out;
}

function extract() {
  const java = fs.readFileSync(SRC, 'utf8');
  const out = {};
  for (const m of java.matchAll(/private static final Swing\[\] (\w+) = \{([\s\S]*?)\n    \};/g)) {
    const [, kind, body] = m;
    const list = [];
    // each entry is a top-level call: s(...), shock(...) or new Swing(...)
    let i = 0, comment = '';
    while (i < body.length) {
      const rest = body.slice(i);
      const cm = rest.match(/^[\s,]*\/\/([^\n]*)\n/);
      if (cm) { comment = cm[1].trim(); i += cm[0].length; continue; }
      const start = rest.search(/(new Swing|shock|s)\(/);
      if (start < 0) break;
      const open = i + start + rest.slice(start).indexOf('(');
      let d = 0, j = open;
      for (; j < body.length; j++) { if (body[j] === '(') d++; if (body[j] === ')') { d--; if (d === 0) break; } }
      const a = args(body.slice(open + 1, j));
      const keys = a.filter((x) => x.startsWith('k(')).map((x) => args(x.slice(2, -1)).map(num));
      const after = a.slice(3);
      const isNew = /new Swing\s*$/.test(body.slice(i, open)) || body.slice(i + start, open).startsWith('new Swing');
      let spin = 0, leftDelay = 0;
      if (isNew && after.length) spin = num(after[0]) || 0;
      const last = a[a.length - 1];
      if (/^-?\d+(\.\d+)?[fF]?$/.test(last) && a.length > 4 && !(isNew && a.length === 6)) leftDelay = num(last);
      list.push({ name: comment, windup: keys[0], strike: keys[1], follow: keys[2], spin, leftDelay });
      comment = '';
      i = j + 1;
    }
    out[kind] = list;
  }
  return out;
}

module.exports = { extract };
if (require.main === module) {
  const s = extract();
  for (const [k, l] of Object.entries(s)) console.log(k, l.length, l.map((x) => `${x.name.slice(0, 28)} spin=${x.spin.toFixed(2)} ld=${x.leftDelay} n=${x.windup.length}`).join(' | '));
}
