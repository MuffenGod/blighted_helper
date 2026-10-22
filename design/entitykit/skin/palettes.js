// Colour ramps (Hi / Mid / Dark / Deep) for the painted extra parts, per variant. The old keys (cloth, scale, wing, ...) stay for the older painters.
const { hex: H } = require('../paint');
const Emily = require('../variants/emily').PAL;
const ramp = (n, hi, mid, dark, deep) => ({ [n + 'Hi']: H(hi), [n + 'Mid']: H(mid), [n + 'Dark']: H(dark), [n + 'Deep']: H(deep) });
const GOLD = ramp('gold', '#fff4c0', '#f2c84e', '#b9852a', '#6a4510');

const base = (extra) => ({ ...Emily, ...extra });

const gaia = base({
  ...GOLD,
  ...ramp('gold', '#fff3b8', '#edc35a', '#b5842c', '#744e14'),
  ...ramp('cloth', '#34324a', '#1c1a2a', '#10101a', '#08070d'),
  ...ramp('horn', '#4a4f6a', '#262a3c', '#141622', '#07080d'),
  ...ramp('wingweb', '#d0304e', '#8a1634', '#4a0b20', '#1e0610'),
  ...ramp('wingbone', '#5a4a52', '#2c2228', '#16101a', '#08050a'),
  ...ramp('ember', '#fff0b0', '#ffa030', '#c04010', '#601008'),
  ...ramp('gem', '#e0fff2', '#46e896', '#12864c', '#05351c'),
  ...ramp('hair', '#fff8d8', '#fbe49e', '#cfa044', '#a47626'),
});

const jester = base({
  ...require('../variants/jester').PAL,
  ...GOLD,
  ...ramp('purple', '#c4a0ff', '#7e4cdc', '#4a2a9c', '#1e1048'),
  ...ramp('cloth', '#34304a', '#1a1830', '#0e0c1a', '#06050c'),
  ...ramp('ruff', '#ffffff', '#f6f0fc', '#d0c6e8', '#8e84b0'),
  ...ramp('hair', '#ffffff', '#b8b4d0', '#8a86a8', '#5c5878'),
  ...ramp('red', '#ff9aa6', '#e0283c', '#8a1020', '#400810'),
});

const demon = base({
  ...ramp('gold', '#ffe8a0', '#e0b040', '#a07820', '#5a4010'),
  ...ramp('cloth', '#34343e', '#18181f', '#0e0e14', '#06060a'),
  ...ramp('navy', '#52669c', '#2c3d66', '#1a2342', '#0a1022'),
  ...ramp('horn', '#f4f4f8', '#b6b6c4', '#747480', '#34343e'),
  ...ramp('wingweb', '#a07ce0', '#5e3e9c', '#34205c', '#160c2c'),
  ...ramp('wingbone', '#6a5a84', '#352a46', '#1c1428', '#0a0610'),
  ...ramp('ruby', '#ffb8f0', '#e040c8', '#8a1a84', '#3a0a40'),
  ...ramp('leather', '#4a3a58', '#2a2036', '#161020', '#0a0610'),
  ...ramp('silver', '#f2f2f6', '#b4b4c0', '#74747f', '#34343c'),
  ...ramp('greyd', '#8a8a98', '#52525e', '#2e2e38', '#16161c'),
  ...ramp('hair', '#8a60c0', '#4a2c78', '#2c1a46', '#0e0818'),
  ...ramp('ember', '#ffd8ff', '#e040ff', '#8a20b0', '#3a0a50'),
});

const angel = base({
  ...GOLD,
  ...ramp('feat', '#ffffff', '#f8f2e2', '#dccfae', '#a99a72'),
  ...ramp('cloth', '#3a3a54', '#201f30', '#12121c', '#08080e'),
  ...ramp('azure', '#c8e8ff', '#5aa0dc', '#2c5ea0', '#16305c'),
  ...ramp('ruff', '#ffffff', '#f8f2e4', '#d8d0b8', '#a89f84'),
  ...ramp('hair', '#fffbe8', '#fbe8b4', '#e2c98e', '#b09a5c'),
  ...ramp('halo', '#ffffff', '#fff2b8', '#f2c850', '#b9852a'),
  featTip: H('#f0d070'),
});

module.exports = { gaia, jester, demon, angel };
