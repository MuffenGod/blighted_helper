// Studio animation player: runs the game's real body layer (HelperBody) and motion layer (HelperMotion), both transpiled from Java, on the studio model.
// Needs globals from studio.template.html: nodes, base, extra, current, root (the display group) and HelperMotion, HelperBody, __SWINGS (inlined).
(function () {
  const M = HelperMotion, B = HelperBody;
  const STD = ['emily_gaia', 'bodybottom', 'bodymiddle', 'bodytop', 'bodytop_female', 'neck', 'head', 'hair1', 'hair2', 'rightarm', 'rightarmlower', 'leftarm', 'leftarmlower',
    'rightwing', 'leftwing', 'skirt1', 'skirt2', 'skirt3', 'tail1', 'tail2', 'tail3', 'rightleg', 'rightleglower', 'leftleg', 'leftleglower'];
  const AXES = { x: 0, y: 1, z: 2, xRot: 3, yRot: 4, zRot: 5, vis: 6, sc: 7 };
  const VARIANT_ID = { gaia: 0, demon: 1, angel: 2, astral: 3, jester: 4 };
  const KINDS = ['FIST', 'SWORD', 'DAGGER', 'AXE', 'HEAVY', 'SPEAR', 'STAFF', 'BOW', 'CROSSBOW', 'DUAL'];
  const SWING_TICKS = [6, 10, 7, 13, 16, 11, 12, 10, 10, 9];   // WeaponKind.swingTicks (ranged kinds fall back to 10)
  const BUSY = [0, 1, 1, 1, 1, 3, 3, 1, 3, 3];                 // HelperBody.CARRY busy arms
  const clamp01 = (x) => Math.max(0, Math.min(1, x));
  const S8 = 8;

  // ---- the rig binding for the current variant: names -> ids, sim parts, groups
  let rig = null, vm = null, simState = null, pose = null, rest = null, xch = null;
  const bodySt = new Float32Array(B.S_LEN);
  const player = { mode: 'idle', playing: true, speed: 0.5, kind: 1, swing: -1, t: 0, age: 0 };

  function buildRig(variant, motionJson) {
    const parts = extra.filter((p) => p.variants.includes(variant));
    const names = STD.slice(), idOf = {};
    names[0] = 'root';
    parts.forEach((p) => names.push(p.name));
    names.forEach((n, i) => { idOf[n] = i; });
    const nparts = names.length;
    rest = new Float32Array(nparts * S8);
    const stdByName = {}; base.forEach((p) => { stdByName[p.name] = p; });
    names.forEach((n, i) => {
      const spec = i === 0 ? stdByName.emily_gaia : (stdByName[n] || parts.find((p) => p.name === n));
      if (!spec) return;
      rest[i * S8 + 0] = spec.pivot[0]; rest[i * S8 + 1] = spec.pivot[1]; rest[i * S8 + 2] = spec.pivot[2];
      rest[i * S8 + 3] = spec.rot[0]; rest[i * S8 + 4] = spec.rot[1]; rest[i * S8 + 5] = spec.rot[2];
      rest[i * S8 + 6] = spec.hidden ? 0 : 1; rest[i * S8 + 7] = 1;
    });
    const xnames = [], xidx = {};
    const xc = (n) => { if (!(n in xidx)) { xidx[n] = xnames.length; xnames.push(n); } return xidx[n]; };
    const groups = [...new Set(parts.filter((p) => p.group).map((p) => p.group))];
    groups.forEach((g) => xc('spread.' + g));
    ['fx.glow'].forEach(xc);
    const chBase = nparts * S8;
    const jsonV = motionJson && motionJson.variants && motionJson.variants[variant];
    const chan = (name) => {
      const dot = name.lastIndexOf('.');
      const pn = name.slice(0, dot), ax = name.slice(dot + 1);
      if (name.startsWith('fx.') || name.startsWith('spread.') || name.startsWith('ev.')) return chBase + xc(name);
      if (!(pn in idOf) || !(ax in AXES)) return -1;
      return idOf[pn] * S8 + AXES[ax];
    };
    const mkClip = (c) => {
      if (!c) return null;
      const clip = new M.Clip(); clip.dur = c.dur || 60; clip.tracks = [];
      for (const t of c.tracks || []) {
        const ch = chan(t.c); if (ch < 0) continue;
        const tr = new M.Track(); tr.ch = ch; tr.mode = t.m === 'set' ? 1 : 0;
        tr.keys = new Float32Array((t.k || []).flat().map((v) => v)); tr.nkeys = (t.k || []).length;
        tr.osc = new Float32Array((t.o || []).map((o) => [o[0], o[1], o[2] || 0, o[3] === undefined ? 1 : o[3]]).flat()); tr.nosc = (t.o || []).length;
        tr.wIn = t.in === undefined ? 0.15 : t.in; tr.wOut = t.out === undefined ? 0.15 : t.out; tr.loop = t.loop || 0; tr.tb = t.tb || 0;
        clip.tracks.push(tr);
      }
      clip.ntracks = clip.tracks.length; return clip;
    };
    vm = new M.VariantMotion();
    vm.idle = mkClip(jsonV && jsonV.idle); vm.walk = mkClip(jsonV && jsonV.walk); vm.run = mkClip(jsonV && jsonV.run);
    vm.flourish = ((jsonV && jsonV.flourish) || []).map(mkClip); vm.nflourish = vm.flourish.length;
    const b = new M.Bind(); b.nparts = nparts; b.chBase = chBase;
    const sim = parts.filter((p) => p.sway || p.phys);
    b.nsim = sim.length; b.simPart = new Int32Array(sim.map((p) => idOf[p.name])); b.simPhi = new Float32Array(sim.map((p) => p.ang || 0));
    b.simRing = sim.map((p) => !!(p.group && p.ang !== undefined && !p.group.endsWith('fan')));
    b.simPar = new Float32Array(sim.flatMap((p) => { const ph = p.phys || {}; return [ph.p || 16, ph.z === undefined ? 0.32 : ph.z, ph.g === undefined ? 1 : ph.g]; }));
    b.simSway = new Float32Array(sim.flatMap((p) => { const s = p.sway || [0, 0, 0, 0.05, 0]; return [s[0], s[1], s[2], s[3], s[4]]; }));
    b.simV = new Float32Array(sim.flatMap((p) => { const v = (p.phys && p.phys.v) || [0, 0]; return [v[0], v[1]]; }));
    b.simG = new Float32Array(sim.map((p) => (p.phys && p.phys.gc) || 0));
    const simSet = new Set(sim.map((p) => p.name)), kids = {};
    parts.forEach((p) => { (kids[p.parent] = kids[p.parent] || []).push(p); });
    const below = (p) => { let m = 0; for (const c of kids[p.name] || []) if (simSet.has(c.name)) m = Math.max(m, 1 + below(c)); return m; };
    const depth = (p) => { let d = 0, q = p; for (;;) { const par = parts.find((x) => x.name === q.parent); if (!par || !simSet.has(par.name)) break; q = par; d++; } return d; };
    b.simDepth = new Int32Array(sim.map(depth));
    sim.forEach((p, j) => { const n = depth(p) + below(p) + 1; if (n > 1) b.simPar[j * 3 + 2] *= 1.3 / n; });
    const gp = parts.filter((p) => p.group && p.group.endsWith('fan'));
    b.grpAng = new Float32Array(gp.map((p) => p.ang || 0)); b.ngrp = gp.length; b.grpPart = new Int32Array(gp.map((p) => idOf[p.name])); b.grpXch = new Int32Array(gp.map((p) => xc('spread.' + p.group)));
    b.nxch = xnames.length; b.xdef = new Float32Array(xnames.map((n) => (n.startsWith('spread.') || n === 'fx.glow' ? 1 : 0)));
    // parts that copy a standard part's pose every frame (the jester's head replica, the infernal's wings)
    const follows = parts.filter((p) => p.follow && idOf[p.follow] !== undefined).map((p) => [idOf[p.name], idOf[p.follow]]);
    rig = { names, idOf, b, nparts, parts, xnames, xidx, follows };
    pose = new Float32Array(nparts * S8); xch = new Float32Array(b.nxch);
    simState = new Float32Array(8 + b.nsim * 4);
    bodySt.fill(0);
  }

  function swingObj(kind, idx) {
    const list = (window.__SWINGS && (window.__SWINGS[KINDS[kind]] || window.__SWINGS.SWORD)) || [];
    if (!list.length) return null;
    const s = list[((idx % list.length) + list.length) % list.length];
    const sw = new B.Swing(); sw.windup = s.windup; sw.strike = s.strike; sw.follow = s.follow; sw.spin = s.spin; sw.leftDelay = s.leftDelay; sw.kind = kind;
    return sw;
  }

  // ---- one frame: the body layer, then the motion layer (as HelperGaiaModel.setupAnim does)
  const bin = new B.In();
  function frame(dtTicks, input) {
    pose.set(rest);
    bin.age = input.age; bin.dt = dtTicks; bin.limbSwing = input.limbSwing; bin.amt = input.amt; bin.run = input.run; bin.air = input.air; bin.vy = input.vy || 0;
    bin.vf = input.vf; bin.vl = input.vl || 0; bin.yaw = input.yaw || 0; bin.calm = input.calm; bin.combat = !!input.combat; bin.carry = !!input.carry;
    bin.kind = input.kind || 0; bin.aim = 0; bin.style = VARIANT_ID[current] || 0; bin.riding = false; bin.flourish = input.fl >= 0;
    B.locomotion(pose, rest, bin, bodySt);
    B.stance(pose, rest, bin, bodySt);
    if (input.swing && input.swingQ >= 0 && input.swingQ < 1) B.swing(pose, rest, bin, bodySt, input.swing, input.swingQ);
    B.finish(pose, rest, bin, bodySt);
    const inp = new M.In();
    inp.age = input.age; inp.dt = dtTicks; inp.limbSwing = input.limbSwing; inp.amt = input.amt; inp.run = input.run; inp.air = input.air;
    inp.idle = 1 - clamp01(input.amt * 6); inp.calm = input.calm; inp.fl = input.fl; inp.flT = input.flT; inp.vf = input.vf; inp.vl = input.vl || 0;
    inp.busy = input.carry || input.combat ? BUSY[input.kind || 0] : 0;
    M.run(pose, rest, xch, vm, inp, rig.b, simState);
    for (const [a, f] of rig.follows) for (let k = 0; k < 6; k++) pose[a * S8 + k] = pose[f * S8 + k];
    apply(input);
    return inp;
  }

  function apply(input) {
    for (let i = 0; i < rig.nparts; i++) {
      const name = i === 0 ? 'emily_gaia' : rig.names[i];
      const n = nodes[name]; if (!n) continue;
      const o = i * S8;
      n.position.set(pose[o], pose[o + 1], pose[o + 2]);
      n.rotation.set(pose[o + 3], pose[o + 4], pose[o + 5], 'ZYX');
      const p = n.userData; const base = p && p.scale ? p.scale[0] : 1;
      if (i >= M.NSTD) { n.visible = pose[o + 6] > 0.5 && !hiddenByDebug(name); n.scale.setScalar(base * pose[o + 7]); }
    }
    if (glowMat) glowMat.uniforms.glow.value = xch[rig.xidx['fx.glow']] === undefined ? 1 : xch[rig.xidx['fx.glow']];
    // the jump arc is the entity moving, not the model: lift the whole display group
    root.position.y = (input && input.lift ? input.lift : 0) * 16;
    root.rotation.y = input && input.turnYaw ? -input.turnYaw : 0;
    props(input);
  }
  const hiddenByDebug = (name) => (window.__hide || []).some((p) => name.startsWith(p));

  // ---- weapon props in her hands (rough shapes, only for judging poses). Grip = forearm-local (0, 5.5, -1), as translateToHand + ItemInHandLayer place it.
  const propMat = { steel: new THREE.MeshBasicMaterial({ color: 0xc9ccd6 }), dark: new THREE.MeshBasicMaterial({ color: 0x3a2e28 }), gold: new THREE.MeshBasicMaterial({ color: 0xd8b04a }), orb: new THREE.MeshBasicMaterial({ color: 0xa070ff }) };
  const box = (g, x0, y0, z0, x1, y1, z1, mat) => { const m = new THREE.Mesh(new THREE.BoxGeometry(x1 - x0, y1 - y0, z1 - z0), propMat[mat]); m.position.set((x0 + x1) / 2, (y0 + y1) / 2, (z0 + z1) / 2); g.add(m); };
  function makeProp(kind) {
    const g = new THREE.Group(); g.position.set(0, 5.5, -1);
    const blade = new THREE.Group(); blade.rotation.x = -0.5; g.add(blade);   // the item points forward and a little up from the fist
    if (kind === 1 || kind === 9) { box(blade, -0.5, -0.4, -12, 0.5, 0.4, -1.5, 'steel'); box(blade, -2, -0.6, -1.6, 2, 0.6, -0.8, 'gold'); box(blade, -0.4, -0.4, -0.8, 0.4, 0.4, 2.2, 'dark'); }
    else if (kind === 2) { box(blade, -0.4, -0.3, -7, 0.4, 0.3, -1.2, 'steel'); box(blade, -1.2, -0.5, -1.3, 1.2, 0.5, -0.7, 'gold'); box(blade, -0.35, -0.35, -0.7, 0.35, 0.35, 1.6, 'dark'); }
    else if (kind === 3) { box(blade, -0.4, -0.4, -11, 0.4, 0.4, 3, 'dark'); box(blade, -0.5, -4.2, -11.5, 0.5, 0.8, -8.5, 'steel'); }
    else if (kind === 4) { box(blade, -1, -0.5, -17, 1, 0.5, -2, 'steel'); box(blade, -3, -0.7, -2.1, 3, 0.7, -1.1, 'gold'); box(blade, -0.5, -0.5, -1.1, 0.5, 0.5, 3.5, 'dark'); }
    else if (kind === 5) { box(blade, -0.4, -0.4, -18, 0.4, 0.4, 9, 'dark'); box(blade, -0.8, -0.5, -22, 0.8, 0.5, -18, 'steel'); }
    else if (kind === 6) { box(blade, -0.45, -0.45, -16, 0.45, 0.45, 10, 'dark'); box(blade, -1.2, -1.2, -18.4, 1.2, 1.2, -16, 'orb'); }
    else if (kind === 7) { blade.rotation.x = 0; box(blade, -0.4, -8, -1.4, 0.4, -6, -0.6, 'dark'); box(blade, -0.4, -6, -2.4, 0.4, 6, -1.4, 'dark'); box(blade, -0.4, 6, -1.4, 0.4, 8, -0.6, 'dark'); box(blade, -0.1, -7.5, -0.2, 0.1, 7.5, 0, 'steel'); }
    else if (kind === 8) { blade.rotation.x = 0; box(blade, -0.6, -0.6, -9, 0.6, 0.6, 3, 'dark'); box(blade, -5, -0.4, -8.5, 5, 0.4, -7.5, 'steel'); }
    return g;
  }
  function props(input) {
    const kind = input && (input.carry || input.combat) ? input.kind : -1;
    for (const [arm, side] of [['rightarmlower', 'r'], ['leftarmlower', 'l']]) {
      const n = nodes[arm]; if (!n) continue;
      const want = kind > 0 && (side === 'r' || kind === 9) ? kind : -1;
      if (n.userData.propKind === want && n.userData.propNode && n.userData.propNode.parent === n) continue;
      if (n.userData.propNode) n.remove(n.userData.propNode);
      n.userData.propNode = want > 0 ? makeProp(want) : null; n.userData.propKind = want;
      if (n.userData.propNode) n.add(n.userData.propNode);
    }
  }

  // ---- inputs for a mode at time `age` (ticks); the jump and attack modes loop
  const JUMP = 34;
  function inputFor(mode, age, ls, tMode) {
    const walking = mode === 'walk' || mode.endsWith('walk') || mode === 'turn';
    const inp = { age, limbSwing: ls, amt: walking ? 0.55 : mode === 'run' ? 1.0 : 0, run: mode === 'run' ? 1 : 0, air: 0, vy: 0,
      calm: !mode.startsWith('combat') && mode !== 'attack', fl: -1, flT: 0, vf: walking ? 0.12 : mode === 'run' ? 0.26 : 0, vl: 0,
      combat: mode.startsWith('combat') || mode === 'attack', carry: mode.startsWith('carry'), kind: player.kind, yaw: 0 };
    if (mode === 'rest') inp.calm = false;
    if (mode.startsWith('f')) { const k = +mode.slice(1); inp.fl = k; const dur = (vm.flourish[k] && vm.flourish[k].dur) || 60; inp.flT = clamp01((tMode % (dur + 40)) / dur); if ((tMode % (dur + 40)) >= dur) inp.fl = -1; }
    if (mode === 'jump') {
      const t = tMode % JUMP, T = 11;
      if (t < T) { inp.air = 1; inp.vy = 0.42 - 0.08 * t; inp.lift = Math.max(0, 0.42 * t - 0.04 * t * t); }
    }
    if (mode === 'turn') { inp.yaw = tMode * 0.09; inp.turnYaw = inp.yaw; }
    if (mode === 'attack') {
      const ticks = SWING_TICKS[player.kind];
      const cyc = ticks + 9, n = Math.floor(tMode / cyc), t = tMode - n * cyc;
      inp.swing = swingObj(player.kind, player.swing >= 0 ? player.swing : n);
      inp.swingQ = t < ticks ? t / ticks : -1;
    }
    return inp;
  }

  // deterministic: simulate from rest in 1/4-tick steps (springs get their history) and stop at `t`
  // t = progress 0..1 for flourishes and attacks (of the selected swing), ticks for everything else
  function set(mode, t) {
    if (!rig || rig.variant !== current) { buildRig(current, window.__MOTION); rig.variant = current; }
    simState.fill(0); bodySt.fill(0);
    const isFl = mode.startsWith('f'), isAtk = mode === 'attack';
    const dur = isFl ? ((vm.flourish[+mode.slice(1)] && vm.flourish[+mode.slice(1)].dur) || 60) : isAtk ? SWING_TICKS[player.kind] : 0;
    const total = (isFl || isAtk) ? t * dur : t;
    const lead = 40, step = 0.25;
    let ls = 0, inp = null;
    for (let k = -lead; k <= total + 1e-6; k += step) {
      const age = 200 + k;
      inp = inputFor(mode, age, ls, Math.max(0, k));
      if (isFl) { inp.flT = clamp01(Math.max(0, k) / dur); inp.fl = k < 0 ? -1 : +mode.slice(1); }
      if (isAtk) { inp.swing = swingObj(player.kind, player.swing >= 0 ? player.swing : 0); inp.swingQ = k < 0 ? -1 : Math.min(0.999, k / dur); }
      frame(k === -lead ? 0 : step, inp);
      ls += inp.amt * step;
    }
    player.mode = mode; player.t = t;
  }

  // free-running playback
  let lastTs = 0, fAge = 200, fLs = 0, modeStart = 200;
  function tick(ts) {
    requestAnimationFrame(tick);
    if (!rig || rig.variant !== current) { buildRig(current, window.__MOTION); rig.variant = current; simState.fill(0); }
    if (!player.playing) return;
    const dt = Math.min(3, (ts - lastTs) / 50) || 0.3; lastTs = ts;        // 50 ms = one tick
    fAge += dt;
    const inp = inputFor(player.mode, fAge, fLs, fAge - modeStart);
    fLs += inp.amt * dt;
    frame(dt, inp);
  }
  requestAnimationFrame(tick);

  const modes = () => ['rest', 'idle', 'walk', 'run', 'jump', 'turn', 'carry', 'carrywalk', 'combat', 'combatwalk', 'attack', ...Array.from({ length: vm ? vm.nflourish : 8 }, (_, i) => 'f' + i)];
  window.__anim = { set, player, modes, KINDS, rebuild: () => { rig = null; }, get rig() { return rig; }, get vm() { return vm; }, swingCount: (k) => ((window.__SWINGS || {})[KINDS[k]] || []).length };
  window.__anim.setMode = (m) => { player.mode = m; modeStart = fAge; };
})();
