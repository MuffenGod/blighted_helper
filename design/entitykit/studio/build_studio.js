// node studio/build_studio.js  ->  studio/studio.html (self-contained: model data, the 4 skins, the transpiled body + motion layers, motion data and the attack poses)
const fs = require('fs'), path = require('path');
const { execFileSync } = require('child_process');
execFileSync(process.execPath, [path.join(__dirname, '..', 'anim', 'java2js.js')], { stdio: 'ignore' });   // the game's Java -> anim/*_core.js
const { extract } = require('./extract_swings');
const out = path.join(__dirname, '..', 'out', 'skin');
const data = {
  base: JSON.parse(fs.readFileSync(path.join(__dirname, 'base.json'))),
  parts: JSON.parse(fs.readFileSync(path.join(out, 'variant_parts.json'))),
  textures: {}, glows: {},
};
for (const v of ['gaia', 'jester', 'demon', 'angel']) { data.textures[v] = 'data:image/png;base64,' + fs.readFileSync(path.join(out, `xl_${v}.png`)).toString('base64'); data.glows[v] = 'data:image/png;base64,' + fs.readFileSync(path.join(out, `xl_${v}_glow.png`)).toString('base64'); }
const motionPath = path.join(__dirname, '..', 'out', 'motion.json');
const read = (p) => (fs.existsSync(p) ? fs.readFileSync(p, 'utf8') : '');
let html = fs.readFileSync(path.join(__dirname, 'studio.template.html'), 'utf8').replace('/*DATA*/null', () => JSON.stringify(data));
html = html.replace('/*MOTION_CORE*/', () => read(path.join(__dirname, '..', 'anim', 'motion_core.js'))).replace('/*BODY_CORE*/', () => read(path.join(__dirname, '..', 'anim', 'body_core.js')))
  .replace('/*MOTION_DATA*/null', () => read(motionPath) || 'null').replace('/*SWINGS*/null', () => JSON.stringify(extract())).replace('/*PLAYER*/', () => read(path.join(__dirname, 'player.js')));
fs.writeFileSync(path.join(__dirname, 'studio.html'), html);
console.log('studio.html', (html.length / 1024).toFixed(0) + ' KB');
