// Reads Gaia's real ModelPart tree out of HelperGaiaModel.createBodyLayer() into base.json so the studio shows exactly what the game builds.
const fs = require('fs'), path = require('path');
const SRC = path.resolve(__dirname, '..', '..', '..', 'blighted_helper', 'src', 'main', 'java', 'com', 'blighteddreams', 'helper', 'client', 'HelperGaiaModel.java');
let s = fs.readFileSync(SRC, 'utf8');
const start = s.indexOf('private static MeshDefinition buildMesh()');
const end = s.indexOf('return mesh;', start);
s = s.slice(start, end);
const num = (t) => parseFloat(t.replace(/F$/i, ''));
function balanced(str, i) { // str[i] === '(' ; returns index after the matching ')'
  let d = 0;
  for (let k = i; k < str.length; k++) { if (str[k] === '(') d++; else if (str[k] === ')') { d--; if (d === 0) return k + 1; } }
  throw new Error('unbalanced');
}
const parts = [], varName = { meshRoot: null, root: 'emily_gaia' };
const re = /(?:PartDefinition\s+(\w+)\s*=\s*)?(\w+)\.addOrReplaceChild\(\s*"(\w+)"/g;
let m;
while ((m = re.exec(s))) {
  const [, assignVar, parentVar, name] = m;
  const open = s.indexOf('(', m.index + (m[1] ? m[0].indexOf('addOrReplaceChild') : m[0].indexOf('addOrReplaceChild')));
  const body = s.slice(open, balanced(s, open));
  if (assignVar) varName[assignVar] = name;
  const parent = parentVar === 'meshRoot' ? null : (varName[parentVar] !== undefined ? varName[parentVar] : parentVar);
  const cubes = [];
  const cre = /\.texOffs\(\s*(\d+)\s*,\s*(\d+)\s*\)((?:\s*\.mirror\(\))?)\s*\.addBox\(([^)]*)\)/g;
  let c;
  while ((c = cre.exec(body))) {
    const a = c[4].split(',').map((t) => num(t.trim()));
    cubes.push({ uv: [+c[1], +c[2]], box: a.slice(0, 6), mirror: /mirror/.test(c[3]) });
  }
  let pivot = [0, 0, 0], rot = [0, 0, 0];
  const po = /PartPose\.offsetAndRotation\(([^)]*)\)/.exec(body), pf = /PartPose\.offset\(([^)]*)\)/.exec(body);
  if (po) { const a = po[1].split(',').map((t) => num(t.trim())); pivot = a.slice(0, 3); rot = a.slice(3, 6); }
  else if (pf) pivot = pf[1].split(',').map((t) => num(t.trim()));
  parts.push({ name, parent: name === 'emily_gaia' ? null : parent, pivot, rot, cubes });
}
fs.writeFileSync(path.join(__dirname, 'base.json'), JSON.stringify({ parts }));
console.log('parts', parts.length, 'cubes', parts.reduce((a, p) => a + p.cubes.length, 0));
console.log(parts.map((p) => p.name + '<' + p.parent).join(' '));
