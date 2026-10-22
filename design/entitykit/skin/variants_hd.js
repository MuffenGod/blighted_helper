// Premium redesigns of the four helpers (authored in real model units, see kit.js). Anything not redesigned yet falls back to variants_skin.js.
const out = {};
for (const [id, file] of [['angel', './v_angel'], ['jester', './v_jester'], ['demon', './v_demon'], ['gaia', './v_gaia']]) {
  try { out[id] = require(file)[id]; } catch (e) { if (e.code !== 'MODULE_NOT_FOUND' || !String(e.message).includes(file.slice(2))) throw e; }
}
module.exports = out;
