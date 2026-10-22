package com.blighteddreams.helper.client;

import net.minecraft.client.model.geom.ModelPart;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * One variant's rig for {@link HelperMotion}: its standard parts plus the extra parts that apply to it, the rest pose, the compiled motion
 * tracks and the spring binding. Built once per model and variant; the pose arrays are scratch space reused every frame.
 */
final class HelperMotionRig {
    static final String[] STD_NAMES = {"root", "bodybottom", "bodymiddle", "bodytop", "bodytop_female", "neck", "head", "hair1", "hair2", "rightarm", "rightarmlower",
            "leftarm", "leftarmlower", "rightwing", "leftwing", "skirt1", "skirt2", "skirt3", "tail1", "tail2", "tail3", "rightleg", "rightleglower", "leftleg", "leftleglower"};
    private static final String[] AXES = {"x", "y", "z", "xRot", "yRot", "zRot", "vis", "sc"};

    final String variant;
    final int generation = HelperMotionData.generation;
    final int n;
    final ModelPart[] parts;
    final HelperVariantParts.Spec[] specs;   // null for the standard parts
    final float[] rest, pose, xch;
    final HelperMotion.VariantMotion vm;
    final HelperMotion.Bind bind;
    final int fxGlow;
    /** per part: the standard part it copies every frame (spec.follow), or -1 */
    final int[] follow;
    final Map<String, Integer> xIndex = new HashMap<>();
    final String[] xNames;

    HelperMotionRig(String variant, ModelPart[] std, List<HelperVariantParts.Spec> specList, List<ModelPart> specParts) {
        this.variant = variant;
        this.n = std.length + specList.size();
        this.parts = new ModelPart[n];
        this.specs = new HelperVariantParts.Spec[n];
        Map<String, Integer> idOf = new HashMap<>();
        for (int i = 0; i < std.length; i++) { parts[i] = std[i]; idOf.put(STD_NAMES[i], i); }
        for (int i = 0; i < specList.size(); i++) {
            int id = std.length + i;
            parts[id] = specParts.get(i);
            specs[id] = specList.get(i);
            idOf.put(specList.get(i).name, id);
        }
        this.follow = new int[n];
        for (int i = 0; i < n; i++) {
            Integer f = specs[i] != null && specs[i].follow != null ? idOf.get(specs[i].follow) : null;
            this.follow[i] = f != null && f < std.length ? f : -1;
        }
        final int S = HelperMotion.STRIDE;
        rest = new float[n * S];
        pose = new float[n * S];
        for (int i = 0; i < n; i++) {
            int o = i * S;
            if (specs[i] == null) {
                net.minecraft.client.model.geom.PartPose ip = parts[i].getInitialPose();
                rest[o] = ip.x; rest[o + 1] = ip.y; rest[o + 2] = ip.z;
                rest[o + 3] = ip.xRot; rest[o + 4] = ip.yRot; rest[o + 5] = ip.zRot;
                rest[o + 6] = 1.0F;
            } else {
                HelperVariantParts.Spec sp = specs[i];
                rest[o] = sp.px; rest[o + 1] = sp.py; rest[o + 2] = sp.pz; rest[o + 3] = sp.rx; rest[o + 4] = sp.ry; rest[o + 5] = sp.rz;
                rest[o + 6] = sp.hidden ? 0.0F : 1.0F;
            }
            rest[o + 7] = 1.0F;
        }

        // extra channels: spread.<group> for fan groups, fx.glow, plus whatever the motion data names (fx.*, ev.*)
        List<String> xnames = new ArrayList<>();
        Map<String, Integer> xidx = new HashMap<>();
        Set<String> groups = new HashSet<>();
        for (int i = std.length; i < n; i++) if (specs[i].group != null && specs[i].group.endsWith("fan")) groups.add(specs[i].group);
        for (String g : groups) addX("spread." + g, xnames, xidx);
        addX("fx.glow", xnames, xidx);
        final int chBase = n * S;

        HelperMotionData.RawVariant raw = HelperMotionData.variant(variant);
        this.vm = new HelperMotion.VariantMotion();
        if (raw != null) {
            vm.idle = compile(raw.idle, idOf, chBase, xnames, xidx);
            vm.walk = compile(raw.walk, idOf, chBase, xnames, xidx);
            vm.run = compile(raw.run, idOf, chBase, xnames, xidx);
            vm.flourish = new HelperMotion.Clip[raw.flourish.size()];
            for (int i = 0; i < vm.flourish.length; i++) vm.flourish[i] = compile(raw.flourish.get(i), idOf, chBase, xnames, xidx);
            vm.nflourish = vm.flourish.length;
        }

        // spring parts: every extra with sway or phys; chain gain is spread over the chain so long strands bend about as far as short ones
        List<Integer> sim = new ArrayList<>();
        Set<String> simSet = new HashSet<>();
        for (int i = std.length; i < n; i++) {
            if (specs[i].sway != null || specs[i].hasPhys) { sim.add(i); simSet.add(specs[i].name); }
        }
        Map<String, List<String>> kids = new HashMap<>();
        Map<String, HelperVariantParts.Spec> byName = new HashMap<>();
        for (int i = std.length; i < n; i++) {
            byName.put(specs[i].name, specs[i]);
            kids.computeIfAbsent(specs[i].parent, k -> new ArrayList<>()).add(specs[i].name);
        }
        HelperMotion.Bind b = new HelperMotion.Bind();
        b.nparts = n; b.chBase = chBase;
        int ns = sim.size();
        b.nsim = ns;
        b.simPart = new int[ns]; b.simPhi = new float[ns]; b.simRing = new boolean[ns]; b.simPar = new float[ns * 3]; b.simSway = new float[ns * 5];
        b.simDepth = new int[ns]; b.simV = new float[ns * 2]; b.simG = new float[ns];
        for (int j = 0; j < ns; j++) {
            int id = sim.get(j);
            HelperVariantParts.Spec sp = specs[id];
            b.simPart[j] = id;
            b.simPhi[j] = sp.ang;
            b.simRing[j] = sp.group != null && sp.hasAng && !sp.group.endsWith("fan");
            float[] sw = sp.sway != null ? sp.sway : new float[]{0, 0, 0, 0.05F, 0};
            for (int k = 0; k < 5; k++) b.simSway[j * 5 + k] = sw[k];
            int depth = 0;
            HelperVariantParts.Spec q = sp;
            while (true) {
                HelperVariantParts.Spec par = byName.get(q.parent);
                if (par == null || !simSet.contains(par.name)) break;
                q = par;
                depth++;
            }
            b.simDepth[j] = depth;
            int below = below(sp.name, kids, simSet);
            float gain = sp.physG;
            int len = depth + below + 1;
            if (len > 1) gain *= 1.3F / len;
            b.simPar[j * 3] = sp.physP; b.simPar[j * 3 + 1] = sp.physZ; b.simPar[j * 3 + 2] = gain;
            b.simV[j * 2] = sp.physVx; b.simV[j * 2 + 1] = sp.physVz;
            b.simG[j] = sp.physGc;
        }
        List<Integer> fan = new ArrayList<>();
        for (int i = std.length; i < n; i++) if (specs[i].group != null && specs[i].group.endsWith("fan")) fan.add(i);
        b.ngrp = fan.size();
        b.grpPart = new int[b.ngrp]; b.grpXch = new int[b.ngrp]; b.grpAng = new float[b.ngrp];
        for (int g = 0; g < b.ngrp; g++) {
            int id = fan.get(g);
            b.grpPart[g] = id;
            b.grpAng[g] = specs[id].ang;
            b.grpXch[g] = addX("spread." + specs[id].group, xnames, xidx);
        }
        b.nxch = xnames.size();
        b.xdef = new float[b.nxch];
        for (int i = 0; i < b.nxch; i++) b.xdef[i] = HelperMotion.defaultFor(xnames.get(i));
        this.bind = b;
        this.xNames = xnames.toArray(new String[0]);
        this.xch = new float[b.nxch];
        this.fxGlow = xidx.get("fx.glow");
        this.xIndex.putAll(xidx);
    }

    private static int below(String name, Map<String, List<String>> kids, Set<String> simSet) {
        int m = 0;
        List<String> ks = kids.get(name);
        if (ks == null) return 0;
        for (String k : ks) if (simSet.contains(k)) m = Math.max(m, 1 + below(k, kids, simSet));
        return m;
    }

    private static int addX(String name, List<String> xnames, Map<String, Integer> xidx) {
        Integer i = xidx.get(name);
        if (i != null) return i;
        xidx.put(name, xnames.size());
        xnames.add(name);
        return xnames.size() - 1;
    }

    private int channel(String name, Map<String, Integer> idOf, int chBase, List<String> xnames, Map<String, Integer> xidx) {
        if (name.startsWith("fx.") || name.startsWith("spread.") || name.startsWith("ev.")) return chBase + addX(name, xnames, xidx);
        int dot = name.lastIndexOf('.');
        if (dot < 0) return -1;
        Integer id = idOf.get(name.substring(0, dot));
        if (id == null) return -1;
        String ax = name.substring(dot + 1);
        for (int a = 0; a < AXES.length; a++) if (AXES[a].equals(ax)) return id * HelperMotion.STRIDE + a;
        return -1;
    }

    private HelperMotion.Clip compile(HelperMotionData.RawClip rc, Map<String, Integer> idOf, int chBase, List<String> xnames, Map<String, Integer> xidx) {
        if (rc == null) return null;
        HelperMotion.Clip c = new HelperMotion.Clip();
        c.dur = rc.dur;
        List<HelperMotion.Track> ts = new ArrayList<>();
        for (HelperMotionData.RawTrack rt : rc.tracks) {
            int ch = channel(rt.c, idOf, chBase, xnames, xidx);
            if (ch < 0) continue;
            HelperMotion.Track t = new HelperMotion.Track();
            t.ch = ch; t.mode = rt.mode; t.keys = rt.keys; t.nkeys = rt.nkeys; t.osc = rt.osc; t.nosc = rt.nosc;
            t.wIn = rt.wIn; t.wOut = rt.wOut; t.loop = rt.loop; t.tb = rt.tb;
            ts.add(t);
        }
        c.tracks = ts.toArray(new HelperMotion.Track[0]);
        c.ntracks = c.tracks.length;
        return c;
    }
}
