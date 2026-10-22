// node studio/build_showcase.js -> studio/showcase.html : the studio as a single publishable page (viewer UI, no dev panel)
const fs = require('fs'), path = require('path');
require('child_process').execFileSync(process.execPath, [path.join(__dirname, 'build_studio.js')], { stdio: 'ignore' });
let h = fs.readFileSync(path.join(__dirname, 'studio.html'), 'utf8');
h = h.replace(/<!doctype html>\s*<html[^>]*>\s*<head>/i, '').replace(/<meta charset[^>]*>\s*/i, '').replace(/<meta name="viewport"[^>]*>\s*/i, '').replace(/<\/head>\s*<body>/i, '').replace(/<\/body>\s*<\/html>\s*$/i, '');
h = h.replace('<title>Model Studio</title>', '<title>Helper Studio</title>\n<meta name="claude:viewport-fit-sides" content="cover">');
// the viewer flag must exist before the studio scripts run
h = h.replace('<div id="app">', '<script>window.__VIEWER = true;</script>\n<div id="app">');
// gutters: side padding for the page content
h = h.replace('* { box-sizing: border-box; }', '* { box-sizing: border-box; } :root { padding-left: 0; padding-right: 0; }');
h = h.replace('#app { display:grid; grid-template-columns: 1fr 320px; height:100vh; }', '#app { display:grid; grid-template-columns: 1fr 320px; height:100%; }').replace('#bar { position:absolute; left:12px; top:12px;', '#bar { position:absolute; left:max(16px, env(safe-area-inset-left, 0px)); top:12px;').replace('#hint { position:absolute; left:12px; bottom:10px;', '#hint { position:absolute; left:max(16px, env(safe-area-inset-left, 0px)); bottom:10px;');
fs.writeFileSync(path.join(__dirname, 'showcase.html'), h);
console.log('showcase.html', (h.length / 1024).toFixed(0) + ' KB', 'starts:', h.slice(0, 60).replace(/\s+/g, ' '));
