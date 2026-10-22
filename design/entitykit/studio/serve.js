// node studio/serve.js  ->  http://localhost:8765/?v=jester&yaw=3.14&pitch=0.1&dist=95&ty=-10   (rebuilds studio.html first)
const { execFileSync } = require('child_process');
const http = require('http'), fs = require('fs'), path = require('path');
execFileSync(process.execPath, [path.join(__dirname, 'extract_base.js')], { stdio: 'ignore' });
execFileSync(process.execPath, [path.join(__dirname, 'build_studio.js')], { stdio: 'inherit' });
const SHOTS = path.join(__dirname, '..', 'out', 'shots'); fs.mkdirSync(SHOTS, { recursive: true });
http.createServer((q, r) => {
  if (q.method === 'POST' && q.url.startsWith('/save/')) {
    let b = ''; q.on('data', (c) => (b += c));
    q.on('end', () => { const n = q.url.slice(6).replace(/[^\w.-]/g, ''); fs.writeFileSync(path.join(SHOTS, n + '.png'), Buffer.from(b.split(',')[1], 'base64')); r.writeHead(200); r.end('saved ' + n); });
    return;
  }
  if (q.url.split('?')[0] === '/') { try { execFileSync(process.execPath, [path.join(__dirname, 'build_studio.js')], { stdio: 'ignore' }); } catch (e) { console.log('build failed', e.message); } }   // always serve a fresh build
  const f = path.join(__dirname, q.url.split('?')[0] === '/' ? 'studio.html' : q.url.split('?')[0]);
  fs.readFile(f, (e, d) => { if (e) { r.writeHead(404); r.end(); } else { r.writeHead(200, { 'content-type': 'text/html' }); r.end(d); } });
}).listen(8765, () => console.log('studio on http://localhost:8765/'));
