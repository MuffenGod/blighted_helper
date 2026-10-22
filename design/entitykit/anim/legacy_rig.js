// Motion helpers for the helpers that use their earlier (2026-10-21) look: Gaia and the infernal fly on the standard Gaia wings
// (leftwing / rightwing, the infernal's own wings copy them) and swing the standard three-segment tail; the jester's cap is two chains
// (x_hat0..5 + x_hatbell, x_hatb0..4 + x_hatbbell). Same signatures as the premium helpers in m_*.js, so the choreography carries over.
const { key, osc } = require('./dsl');

const neg = (ks, k = 1) => ks.map(([t, v, e]) => [t, -v * k, e]);
const scl = (ks, k) => ks.map(([t, v, e]) => [t, v * k, e]);

// open: + spreads the flat wings out to the sides (|yRot| toward pi/2; at 0 they point straight back, edge-on from the front)
const wingOpen = (ks, k = 1) => [key('leftwing.yRot', scl(ks, 0.7 * k)), key('rightwing.yRot', neg(ks, 0.7 * k))];
// lift: callers pass negative = raise
const wingLift = (ks) => [key('rightwing.zRot', neg(ks)), key('leftwing.zRot', scl(ks, 1))];
// fan spread has no equivalent on the flat wings: open them a little instead
const wingFan = (ks) => wingOpen(ks.map(([t, v, e]) => [t, (v - 1) * 0.6, e]));
const flap = (amp, period, env = 0, ph = 0) => [osc('rightwing.zRot', amp * 1.6, period, ph, env), osc('leftwing.zRot', -amp * 1.6, period, ph, env)];
const tailWave = (amp, period, env = 0) => [1, 2, 3].map((i) => osc(`tail${i}.yRot`, amp * 1.9, period, -(i - 1) * 0.9, env));
// raise: callers pass negative = raise (positive xRot lifts the tail)
const tailRaise = (ks) => [key('tail1.xRot', neg(ks)), key('tail2.xRot', neg(ks, 0.5))];

// the user preferred these helpers without the pulsing glow: drop every fx.glow track
function noGlow(motion) {
  const strip = (c) => (c ? { ...c, tracks: c.tracks.filter((t) => t.c !== 'fx.glow') } : c);
  return { idle: strip(motion.idle), walk: strip(motion.walk), run: strip(motion.run), flourish: motion.flourish.map(strip) };
}

module.exports = { wingOpen, wingLift, wingFan, flap, tailWave, tailRaise, noGlow };
