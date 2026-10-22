package com.blighteddreams.helper.client.starter;

import com.blighteddreams.helper.HelperEntities;
import com.blighteddreams.helper.HelperEntity;
import com.blighteddreams.helper.HelperStats;
import com.blighteddreams.helper.HelperVariant;
import com.blighteddreams.helper.network.HelperPayloads;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.network.PacketDistributor;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Companion selection: one rotating hero preview in the centre, role and description on the left, aptitudes on the
 * right, a slim selector strip along the bottom. Clean lettering only, the richness comes from light and motion.
 * Left/Right (or A/D, scroll, click) browse, Enter chooses, click the name to rename.
 */
public class HelperStarterScreen extends Screen {

    private final HelperVariant[] variants = new HelperVariant[]{
            HelperVariant.GAIA,
            HelperVariant.ANGEL,
            HelperVariant.DEMON,
            HelperVariant.JESTER
    };

    private static final String[] ROLES = {"Frontline Tank", "Radiant Healer", "Hellfire Mage", "Void Mystic", "Chaos Trickster"};
    private static final String[] APT_LABELS = HelperStats.LABELS;
    private final int[][] ratings = new int[variants.length][];

    private static final int WHITE = 0xFFFFFF;
    private static final int DIM = 0x9C93B4;

    private final boolean isSummoned;
    private final int activeVariant;
    private final CompoundTag profiles;

    private final String[] names = new String[variants.length];
    private final int[] levels = new int[variants.length];
    private final boolean[] initialized = new boolean[variants.length];
    private final HelperEntity[] dummies = new HelperEntity[variants.length];
    private final float[] aptShown = new float[5];
    private final List<Mote> bursts = new ArrayList<>();
    private final Random random = new Random();

    private EditBox nameBox;
    private int sel;
    private int prevSel;
    private int slideDir = 1;
    private long switchAt = -10000;
    private float spin;
    private long spinPausedUntil;
    private boolean dragging;
    private final long openedAt = Util.getMillis();
    private long lastFrame = openedAt;
    private boolean closing;
    private long confirmAt;

    private static final class Mote {
        float x, y, vx, vy, life, max, size;
        int rgb;
    }

    public HelperStarterScreen() {
        this(new CompoundTag());
    }

    public HelperStarterScreen(CompoundTag data) {
        super(Component.literal("Companion Sanctuary"));
        CompoundTag d = (data != null) ? data : new CompoundTag();
        this.isSummoned = d.getBoolean("isSummoned");
        this.activeVariant = d.contains("activeVariant") ? d.getInt("activeVariant") : 0;
        this.profiles = d.getCompound("profiles");

        for (int i = 0; i < variants.length; i++) {
            HelperVariant v = variants[i];
            String vKey = "variant_" + v.id;
            if (this.profiles.contains(vKey)) {
                CompoundTag p = this.profiles.getCompound(vKey);
                this.initialized[i] = p.getBoolean("initialized");
                this.levels[i] = Math.max(1, p.getInt("level"));
                String n = p.getString("name");
                this.names[i] = (n != null && !n.isBlank()) ? n : v.getRandomName();
            } else {
                this.levels[i] = 1;
                this.names[i] = v.getRandomName();
            }
            if (isSummoned && v.id == activeVariant) {
                sel = i;
            }
        }
        prevSel = sel;
        for (int i = 0; i < 5; i++) {
            aptShown[i] = 0;
        }
    }

    // ------------------------------------------------------------------ setup

    @Override
    protected void init() {
        super.init();
        this.clearWidgets();
        String keep = (nameBox != null) ? nameBox.getValue() : names[sel];
        nameBox = new EditBox(this.font, 0, 0, 90, 14, Component.literal("Name"));
        nameBox.setMaxLength(16);
        nameBox.setBordered(false);
        nameBox.setTextColor(WHITE);
        nameBox.setValue(keep);
        nameBox.visible = false;
        this.addRenderableWidget(nameBox);

        if (this.minecraft != null && this.minecraft.level != null) {
            for (int i = 0; i < variants.length; i++) {
                if (dummies[i] == null) {
                    HelperEntity dummy = new HelperEntity(HelperEntities.HELPER.get(), this.minecraft.level);
                    dummy.setVariant(variants[i]);
                    if (dummy.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.SCALE) != null) {
                        dummy.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.SCALE).setBaseValue(1.0);
                    }
                    dummies[i] = dummy;
                }
            }
        }
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // No vanilla blur; this screen paints its own backdrop.
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void tick() {
        super.tick();
        for (HelperEntity e : dummies) {
            if (e != null) {
                e.tickCount++;
            }
        }
        if (closing && Util.getMillis() - confirmAt > 560) {
            this.onClose();
        }
    }

    // ------------------------------------------------------------------ helpers

    private int[] ratingsOf(int i) {
        if (ratings[i] == null) ratings[i] = HelperStats.ratings(variants[i]);
        return ratings[i];
    }

    private long now() {
        return Util.getMillis() - openedAt;
    }

    private static float clamp01(float v) {
        return Mth.clamp(v, 0f, 1f);
    }

    private static float easeOut(float t) {
        t = clamp01(t);
        float u = 1f - t;
        return 1f - u * u * u;
    }

    private float appear(long delay, long dur) {
        return easeOut((now() - delay) / (float) dur);
    }

    private static int argb(int rgb, float a) {
        int al = Mth.clamp((int) (a * 255f), 0, 255);
        return (al << 24) | (rgb & 0xFFFFFF);
    }

    private static int mix(int a, int b, float t) {
        t = clamp01(t);
        int r = (int) (((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t);
        int gg = (int) (((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t);
        int bl = (int) ((a & 255) * (1 - t) + (b & 255) * t);
        return (r << 16) | (gg << 8) | bl;
    }

    private static float frac(float v) {
        return v - (float) Math.floor(v);
    }

    private void text(GuiGraphics g, String s, float x, float y, int rgb, float a, float scale, boolean shadow) {
        if (a < 0.04f) return;
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(scale, scale, 1f);
        g.drawString(this.font, s, 0, 0, argb(rgb, a), shadow);
        g.pose().popPose();
    }

    private void textCentered(GuiGraphics g, String s, float cx, float y, int rgb, float a, float scale, boolean shadow) {
        text(g, s, cx - this.font.width(s) * scale / 2f, y, rgb, a, scale, shadow);
    }

    private float spacedWidth(String s, float spacing) {
        float w = 0;
        for (int i = 0; i < s.length(); i++) {
            w += this.font.width(String.valueOf(s.charAt(i))) + spacing;
        }
        return w - spacing;
    }

    private void spaced(GuiGraphics g, String s, float x, float y, int rgb, float a, float spacing, float sweepX) {
        float cx = x;
        for (int i = 0; i < s.length(); i++) {
            String c = String.valueOf(s.charAt(i));
            int w = this.font.width(c);
            int col = rgb;
            if (sweepX > -999) {
                float b = clamp01(1f - Math.abs(cx + w / 2f - sweepX) / 26f);
                col = mix(rgb, WHITE, b * 0.85f);
            }
            if (a >= 0.04f) {
                g.drawString(this.font, c, (int) cx, (int) y, argb(col, a), false);
            }
            cx += w + spacing;
        }
    }

    private void spacedCentered(GuiGraphics g, String s, float cx, float y, int rgb, float a, float spacing, float sweepX) {
        spaced(g, s, cx - spacedWidth(s, spacing) / 2f, y, rgb, a, spacing, sweepX);
    }

    private List<FormattedCharSequence> wrap(String s, int width, int maxLines) {
        List<FormattedCharSequence> lines = new ArrayList<>(this.font.split(Component.literal(s), width));
        if (lines.size() > maxLines) {
            lines = new ArrayList<>(lines.subList(0, maxLines));
            String cut = s;
            // re-split with a trailing ellipsis so the last visible line reads as cut off on purpose
            List<FormattedCharSequence> all = this.font.split(Component.literal(cut), width);
            StringBuilder sb = new StringBuilder();
            all.get(maxLines - 1).accept((i, style, cp) -> {
                sb.appendCodePoint(cp);
                return true;
            });
            String last = sb.toString().trim();
            while (!last.isEmpty() && this.font.width(last + "...") > width) {
                last = last.substring(0, last.length() - 1);
            }
            lines.set(maxLines - 1, Component.literal(last + "...").getVisualOrderText());
        }
        return lines;
    }

    private void glow(GuiGraphics g, float cx, float cy, float rx, float ry, int rgb, float strength) {
        int layers = 12;
        for (int k = 0; k < layers; k++) {
            float r = 1f - k / (float) layers;
            float hy = ry * r;
            float hx = rx * r;
            int col = argb(rgb, strength / layers * 0.9f);
            for (float dy = -hy; dy < hy; dy += 2f) {
                float t = dy / hy;
                float half = hx * (float) Math.sqrt(Math.max(0, 1 - t * t));
                g.fill((int) (cx - half), (int) (cy + dy), (int) (cx + half), (int) (cy + dy) + 2, col);
            }
        }
    }

    private void ring(GuiGraphics g, float cx, float cy, float rx, float ry, int rgb, float a, float arcPhase) {
        int steps = (int) Math.max(120, rx * 5);
        for (int i = 0; i < steps; i++) {
            float ang = (float) (i * Math.PI * 2 / steps);
            float x = cx + (float) Math.cos(ang) * rx;
            float y = cy + (float) Math.sin(ang) * ry;
            float d = Math.abs(Mth.wrapDegrees((float) Math.toDegrees(ang - arcPhase)));
            float bright = clamp01(1f - d / 70f);
            float al = a * (0.35f + 0.65f * bright);
            g.fill((int) x, (int) y, (int) x + 1, (int) y + 1, argb(mix(rgb, WHITE, bright * 0.7f), al));
        }
    }

    private void chevron(GuiGraphics g, int cx, int cy, int dir, int size, int rgb, float a) {
        int col = argb(rgb, a);
        for (int i = 0; i <= size; i++) {
            int x = dir < 0 ? cx - size + i : cx + size - i;
            g.fill(x, cy - i, x + 2, cy - i + 1, col);
            g.fill(x, cy + i, x + 2, cy + i + 1, col);
        }
    }

    private void spawnBurst(float cx, float cy, int rgb, int count, float speed) {
        for (int i = 0; i < count; i++) {
            Mote m = new Mote();
            double ang = random.nextDouble() * Math.PI * 2;
            float sp = speed * (0.35f + random.nextFloat());
            m.x = cx;
            m.y = cy;
            m.vx = (float) Math.cos(ang) * sp;
            m.vy = (float) Math.sin(ang) * sp * 0.7f - speed * 0.25f;
            m.max = m.life = 0.7f + random.nextFloat() * 0.7f;
            m.size = random.nextInt(4) == 0 ? 2 : 1;
            m.rgb = random.nextBoolean() ? rgb : mix(rgb, WHITE, 0.6f);
            bursts.add(m);
        }
    }

    // ------------------------------------------------------------------ layout

    private float u() {
        return this.height / 240f;
    }

    private float stageX() {
        return this.width / 2f;
    }

    private float feetY() {
        return this.height * 0.55f;
    }

    private int stripTop() {
        return this.height - 68;
    }

    private static final int CARD_W = 30;
    private static final int CARD_GAP = 10;

    private int stripLeft() {
        int total = variants.length * CARD_W + (variants.length - 1) * CARD_GAP;
        return (this.width - total) / 2;
    }

    private int panelW() {
        return Mth.clamp((int) (this.width * 0.27f), 96, 132);
    }

    private int leftX() {
        return Math.max(14, (int) (this.width * 0.5f - this.width * 0.19f - panelW()));
    }

    private int rightX() {
        return this.width - leftX() - panelW();
    }

    private int panelY() {
        return (int) (this.height * 0.22f);
    }

    private int btnW() {
        return 116;
    }

    private int btnY() {
        return (int) (nameY() + 20);
    }

    private int btnX() {
        return (int) (stageX() - btnW() / 2f);
    }

    private float nameScale() {
        return 1.5f;
    }

    private float nameY() {
        return feetY() + 14;
    }

    // ------------------------------------------------------------------ state changes

    private void commitName() {
        if (nameBox != null) {
            String v = nameBox.getValue().trim();
            names[sel] = v.isEmpty() ? variants[sel].getRandomName(random) : v;
            nameBox.setValue(names[sel]);
        }
    }

    private void select(int index, boolean sound) {
        int n = variants.length;
        index = Math.floorMod(index, n);
        if (index == sel) return;
        commitName();
        blurName();
        prevSel = sel;
        slideDir = shortestDir(sel, index);
        sel = index;
        nameBox.setValue(names[sel]);
        switchAt = now();
        spin = 0;
        spinPausedUntil = 0;
        spawnBurst(stageX(), feetY() - this.height * 0.2f, variants[sel].glowColor, 26, 70f * u());
        if (sound && this.minecraft != null) {
            this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.AMETHYST_BLOCK_CHIME, 1.3F + sel * 0.08F, 0.7F));
        }
    }

    private int shortestDir(int from, int to) {
        int n = variants.length;
        int fwd = Math.floorMod(to - from, n);
        return fwd <= n / 2 ? 1 : -1;
    }

    private void blurName() {
        if (nameBox != null && nameBox.isFocused()) {
            nameBox.setFocused(false);
            this.setFocused(null);
        }
    }

    private void confirm() {
        if (closing) return;
        commitName();
        HelperVariant v = variants[sel];
        boolean active = isSummoned && v.id == activeVariant;
        if (active) {
            PacketDistributor.sendToServer(new HelperPayloads.UnsummonCompanion());
            if (this.minecraft != null) {
                this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.ENDER_EYE_DEATH, 1.2F));
            }
        } else {
            PacketDistributor.sendToServer(new HelperPayloads.ChooseStarter(v.id, names[sel]));
            if (this.minecraft != null) {
                this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.PLAYER_LEVELUP, 1.0F));
                this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.BEACON_ACTIVATE, 1.4F, 0.6F));
            }
        }
        closing = true;
        confirmAt = Util.getMillis();
        spawnBurst(stageX(), feetY() - this.height * 0.2f, v.glowColor, 70, 130f * u());
    }

    // ------------------------------------------------------------------ render

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        long nowMs = Util.getMillis();
        float dt = Math.min(0.1f, (nowMs - lastFrame) / 1000f);
        lastFrame = nowMs;
        float t = now() / 1000f;
        float uu = u();
        int W = this.width;
        int H = this.height;

        HelperVariant v = variants[sel];
        HelperVariant pv = variants[prevSel];
        float sw = easeOut((now() - switchAt) / 480f);
        int accent = mix(pv.glowColor, v.glowColor, sw);
        int tint = mix(pv.titleColor, v.titleColor, sw);

        // spin and stat easing
        boolean paused = nowMs < spinPausedUntil;
        if (!dragging && !paused) {
            spin += dt * 26f;
        }
        for (int i = 0; i < 5; i++) {
            aptShown[i] += (ratingsOf(sel)[i] - aptShown[i]) * Math.min(1f, dt * 7f);
        }

        // backdrop
        int bottomCol = mix(0x0B0716, accent, 0.16f);
        g.fillGradient(0, 0, W, H, 0xFF05030C, 0xFF000000 | bottomCol);
        g.fillGradient(0, 0, W, (int) (H * 0.18f), 0xCC000000, 0x00000000);
        g.fillGradient(0, (int) (H * 0.82f), W, H, 0x00000000, 0xAA000000);

        // light behind the hero
        float stageA = appear(100, 900);
        float cx = stageX();
        float feet = feetY();
        float cy = feet - H * 0.2f;
        float pulse = 0.85f + 0.15f * (float) Math.sin(t * 1.6);
        glow(g, cx, cy + 6 * uu, 118 * uu, 104 * uu, accent, 0.30f * stageA * pulse);
        glow(g, cx, cy, 58 * uu, 70 * uu, mix(accent, WHITE, 0.35f), 0.22f * stageA * pulse);
        // soft vertical beam
        for (int k = 0; k < 4; k++) {
            int bw = (int) ((10 + k * 14) * uu);
            g.fillGradient((int) cx - bw, (int) (H * 0.06f), (int) cx + bw, (int) feet,
                    0x00000000, argb(accent, 0.05f * stageA));
        }

        // drifting motes
        for (int i = 0; i < 46; i++) {
            float a = frac(i * 0.6180339f + 0.13f);
            float b = frac(i * 0.7548777f + 0.37f);
            float sp = 5f + 9f * frac(i * 0.5698403f);
            float y = H + 6 - frac(b + t * sp / (H + 12f)) * (H + 12f);
            float x = a * W + (float) Math.sin(t * 0.6 + i) * 7f;
            float tw = 0.35f + 0.65f * (0.5f + 0.5f * (float) Math.sin(t * (1.2 + a * 2) + i * 3));
            int size = (i % 5 == 0) ? 2 : 1;
            g.fill((int) x, (int) y, (int) x + size, (int) y + size, argb(mix(accent, WHITE, 0.4f), 0.28f * tw * appear(i * 20L, 900)));
        }

        // platform rings
        float ringA = appear(250, 700);
        float swell = 1f + 0.04f * (float) Math.sin(t * 2.0);
        ring(g, cx, feet + 1, 46 * uu * swell, 10 * uu * swell, accent, 0.55f * ringA, t * 1.4f);
        ring(g, cx, feet + 1, 31 * uu, 6.5f * uu, mix(accent, WHITE, 0.4f), 0.45f * ringA, -t * 2.1f);
        glow(g, cx, feet + 1, 54 * uu, 11 * uu, accent, 0.32f * ringA);
        // expanding pulse ring on switch
        float pulseT = (now() - switchAt) / 800f;
        if (pulseT >= 0 && pulseT < 1) {
            float e = easeOut(pulseT);
            ring(g, cx, feet + 1, (14 + 90 * e) * uu, (3 + 20 * e) * uu, WHITE, (1f - pulseT) * 0.7f, t * 3f);
        }

        // hero
        float modelA = appear(200, 800);
        float slide = (1f - sw) * slideDir * 38f * uu;
        float swScale = 0.92f + 0.08f * sw;
        HelperEntity hero = dummies[sel];
        if (hero != null && modelA > 0.02f) {
            float h = Math.max(1.4f, hero.getBbHeight());
            float fit = hero.getVariant().usesXlModel() ? 0.9f : 1.0f;   // the premium models are taller (horns, hat, halo) and wider (wings)
            float scale = (H * 0.335f / h) * fit * swScale * (0.92f + 0.08f * modelA);
            float rise = (1f - modelA) * 16f * uu;
            drawEntity(g, hero, cx + slide, feet + 2 - rise + (1f - sw) * 6f, scale, spin);
        }

        // header
        float headA = appear(0, 600);
        String title = isSummoned ? "YOUR COMPANIONS" : "CHOOSE YOUR COMPANION";
        float sweep = -40 + frac(t / 4.6f) * (spacedWidth(title, 3.5f) + 90);
        float tx = cx - spacedWidth(title, 3.5f) / 2f;
        float ty = 9 * Math.min(uu, 1.4f) + (1f - headA) * -6f;
        spaced(g, title, tx, ty, 0xF1E9FF, headA, 3.5f, tx + sweep);
        float lineW = 70 * easeOut((now() - 250) / 700f);
        g.fillGradient((int) (cx - lineW), (int) ty + 13, (int) cx, (int) ty + 14, 0x00000000, argb(accent, 0.8f * headA));
        g.fillGradient((int) cx, (int) ty + 13, (int) (cx + lineW), (int) ty + 14, argb(accent, 0.8f * headA), 0x00000000);
        textCentered(g, "Each companion keeps her own level, gear and spells", cx, ty + 18, DIM, 0.8f * headA, 0.8f, false);

        renderLeftPanel(g, v, tint, accent, sw);
        renderRightPanel(g, v, tint, accent, sw);

        // chevrons
        float ca = appear(750, 500);
        int chY = (int) (feet - H * 0.18f);
        int chOff = (int) (62 * uu);
        boolean hl = Math.abs(mouseX - (cx - chOff)) < 14 && Math.abs(mouseY - chY) < 20;
        boolean hr = Math.abs(mouseX - (cx + chOff)) < 14 && Math.abs(mouseY - chY) < 20;
        chevron(g, (int) (cx - chOff - (hl ? 2 : 0)), chY, -1, 6, hl ? WHITE : tint, (hl ? 1f : 0.55f) * ca);
        chevron(g, (int) (cx + chOff + (hr ? 2 : 0)), chY, 1, 6, hr ? WHITE : tint, (hr ? 1f : 0.55f) * ca);

        renderNameAndButton(g, v, tint, accent, mouseX, mouseY, t);
        renderStrip(g, mouseX, mouseY, t, accent);

        // hint line
        float hintA = appear(1000, 600);
        String hint = "A / D  or  arrows  browse        Enter  choose        click the name to rename";
        textCentered(g, hint, cx, H - 11, 0x6E668A, hintA, 0.75f, false);

        // particle bursts
        for (int i = bursts.size() - 1; i >= 0; i--) {
            Mote m = bursts.get(i);
            m.life -= dt;
            if (m.life <= 0) {
                bursts.remove(i);
                continue;
            }
            m.x += m.vx * dt;
            m.y += m.vy * dt;
            m.vy += 60f * dt;
            m.vx *= 0.985f;
            int s = (int) m.size;
            g.fill((int) m.x, (int) m.y, (int) m.x + s, (int) m.y + s, argb(m.rgb, clamp01(m.life / m.max)));
        }

        // intro fade-in and confirm flash
        float intro = 1f - clamp01(now() / 450f);
        if (intro > 0.01f) {
            g.fill(0, 0, W, H, argb(0x000000, intro));
        }
        if (closing) {
            float f = (nowMs - confirmAt) / 520f;
            g.fill(0, 0, W, H, argb(WHITE, clamp01(1f - f * 1.4f) * 0.45f + clamp01((f - 0.6f) * 2.5f) * 0.55f));
        }

        // name editor (only visible while editing)
        nameBox.visible = nameBox.isFocused();
        if (nameBox.visible) {
            int bw = Math.max(46, this.font.width(nameBox.getValue()) + 12);
            nameBox.setWidth(bw);
            nameBox.setX((int) (cx - bw / 2f));
            nameBox.setY((int) (nameY() + 2));
            nameBox.setTextColor(WHITE);
        }
        super.render(g, mouseX, mouseY, partialTick);
    }

    private void drawEntity(GuiGraphics g, HelperEntity e, float cx, float feetY, float scale, float spinDeg) {
        e.yBodyRot = e.yBodyRotO = 180f;
        e.yHeadRot = e.yHeadRotO = 180f;
        e.setYRot(180f);
        e.yRotO = 180f;
        e.setXRot(0f);
        e.xRotO = 0f;
        float h = e.getBbHeight();
        Quaternionf pose = new Quaternionf().rotateZ((float) Math.PI).rotateY((float) Math.toRadians(spinDeg));
        InventoryScreen.renderEntityInInventory(g, cx, feetY - h * scale / 2f, scale, new Vector3f(0f, h / 2f, 0f), pose, null, e);
    }

    private void renderLeftPanel(GuiGraphics g, HelperVariant v, int tint, int accent, float sw) {
        float pa = appear(450, 600);
        float a = pa * sw;
        float x = leftX() + (1f - pa) * -14f + (1f - sw) * -8f;
        float y = panelY();
        int w = panelW();

        spaced(g, "COMPANION", x, y, DIM, a * 0.9f, 2.5f, -9999);
        text(g, v.title, x, y + 13, tint, a, 1.6f, true);
        text(g, v.subtitle, x, y + 13 + 9 * 1.6f + 3, DIM, a, 0.9f, false);
        float ly = y + 13 + 9 * 1.6f + 3 + 12;
        g.fillGradient((int) x, (int) ly, (int) x + 34, (int) ly + 1, argb(accent, a), argb(accent, 0f));
        ly += 7;
        int i = sel;
        String status;
        int statusCol;
        if (isSummoned && v.id == activeVariant) {
            status = "Active guardian";
            statusCol = 0x6BFF9A;
        } else if (initialized[i]) {
            status = "Level " + levels[i] + "  -  bonded";
            statusCol = 0xFFD76B;
        } else {
            status = "Not yet bonded";
            statusCol = 0x8C85A6;
        }
        text(g, status, x, ly, statusCol, a, 0.85f, false);
        ly += 13;
        for (FormattedCharSequence line : wrap(v.description, (int) (w / 0.85f), 5)) {
            if (a > 0.04f) {
                g.pose().pushPose();
                g.pose().translate(x, ly, 0);
                g.pose().scale(0.85f, 0.85f, 1f);
                g.drawString(this.font, line, 0, 0, argb(0xD9D2EA, a), false);
                g.pose().popPose();
            }
            ly += 8.5f;
        }
    }

    private void renderRightPanel(GuiGraphics g, HelperVariant v, int tint, int accent, float sw) {
        float pa = appear(600, 600);
        float a = pa * sw;
        float x = rightX() + (1f - pa) * 14f + (1f - sw) * 8f;
        float y = panelY();
        int w = panelW();

        spaced(g, "STATS", x, y, DIM, a * 0.9f, 2.5f, -9999);
        float ry = y + 14;
        for (int i = 0; i < 5; i++) {
            float fill = clamp01(aptShown[i] / 10f);
            text(g, APT_LABELS[i], x, ry, 0xD9D2EA, a, 0.8f, false);
            String num = String.valueOf(ratingsOf(sel)[i]);
            text(g, num, x + w - this.font.width(num) * 0.8f, ry, tint, a, 0.8f, false);
            int by = (int) ry + 9;
            g.fill((int) x, by, (int) x + w, by + 2, argb(WHITE, 0.10f * a));
            int fw = (int) (w * fill);
            if (fw > 0) {
                g.fill((int) x, by, (int) x + fw, by + 2, argb(accent, a));
                g.fill((int) x + Math.max(0, fw - 3), by - 1, (int) x + fw, by + 3, argb(WHITE, 0.9f * a));
            }
            ry += 14;
        }
        ry += 3;
        HelperStats.Profile prof = HelperStats.of(v);
        spaced(g, "PERKS", x, ry, DIM, a * 0.9f, 2.5f, -9999);
        ry += 12;
        for (int pi = 0; pi < prof.perks().length; pi++) {
            int col = pi == 0 ? tint : 0xBDB5D3;
            for (FormattedCharSequence line : wrap(prof.perks()[pi], (int) (w / 0.8f), 2)) {
                if (a > 0.04f) {
                    g.pose().pushPose();
                    g.pose().translate(x, ry, 0);
                    g.pose().scale(0.8f, 0.8f, 1f);
                    g.drawString(this.font, line, 0, 0, argb(col, a), false);
                    g.pose().popPose();
                }
                ry += 8;
            }
            ry += 3;
        }
    }

    private void renderNameAndButton(GuiGraphics g, HelperVariant v, int tint, int accent, int mx, int my, float t) {
        float cx = stageX();
        float na = appear(700, 600) * easeOut((now() - switchAt) / 380f);
        float sc = nameScale();
        String name = names[sel];
        float nw = this.font.width(name) * sc;
        float ny = nameY();
        boolean editing = nameBox.isFocused();

        if (!editing) {
            text(g, name, cx - nw / 2f, ny, WHITE, na, sc, true);
        }
        boolean nameHover = !editing && mx >= cx - nw / 2f - 4 && mx <= cx + nw / 2f + 4 && my >= ny - 2 && my <= ny + 12 * sc;
        float ux = editing ? this.font.width(nameBox.getValue()) / 2f + 6 : nw / 2f;
        g.fill((int) (cx - ux), (int) (ny + 10 * sc + 1), (int) (cx + ux), (int) (ny + 10 * sc + 2),
                argb(nameHover || editing ? WHITE : accent, (nameHover || editing ? 0.9f : 0.45f) * na));

        // dice
        int dx = (int) (cx + Math.max(nw, 40) / 2f + 9);
        int dy = (int) (ny + 1);
        boolean diceHover = mx >= dx && mx <= dx + 11 && my >= dy && my <= dy + 11;
        int dcol = diceHover ? WHITE : mix(accent, WHITE, 0.3f);
        float da = (diceHover ? 1f : 0.65f) * na;
        g.renderOutline(dx, dy, 11, 11, argb(dcol, da));
        int[][] pips = {{2, 2}, {7, 2}, {4, 4}, {2, 7}, {7, 7}};
        for (int[] p : pips) {
            g.fill(dx + p[0], dy + p[1], dx + p[0] + 2, dy + p[1] + 2, argb(dcol, da));
        }

        // main button
        float ba = appear(900, 500);
        boolean active = isSummoned && v.id == activeVariant;
        int bx = btnX();
        int by = btnY();
        int bw = btnW();
        int bh = 18;
        boolean hover = !closing && mx >= bx && mx <= bx + bw && my >= by && my <= by + bh;
        int bcol = active ? 0xFF6B6B : accent;
        float hv = hover ? 1f : 0f;
        g.fill(bx, by, bx + bw, by + bh, argb(bcol, (0.10f + 0.28f * hv) * ba));
        g.renderOutline(bx, by, bw, bh, argb(mix(bcol, WHITE, hover ? 0.6f : 0.15f), (0.7f + 0.3f * hv) * ba));
        // sweeping shine
        float sp = frac(t / 2.8f);
        int shineX = bx - 24 + (int) (sp * (bw + 48));
        for (int k = 0; k < 12; k++) {
            int sx = shineX + k;
            if (sx > bx && sx < bx + bw - 1) {
                float f = 1f - Math.abs(k - 6) / 6f;
                g.fill(sx, by + 1, sx + 1, by + bh - 1, argb(WHITE, 0.16f * f * ba));
            }
        }
        // soft glow under the button
        float glowPulse = 0.5f + 0.5f * (float) Math.sin(t * 2.4);
        g.fillGradient(bx + 6, by + bh, bx + bw - 6, by + bh + 5, argb(bcol, (0.10f + 0.10f * glowPulse + 0.2f * hv) * ba), 0x00000000);
        String label = active ? "UNSUMMON" : (initialized[sel] ? "SUMMON" : "CHOOSE");
        spacedCentered(g, label, bx + bw / 2f, by + 5, hover ? WHITE : mix(bcol, WHITE, 0.75f), ba, 3f, -9999);
    }

    private void renderStrip(GuiGraphics g, int mx, int my, float t, int accent) {
        int top = stripTop();
        int left = stripLeft();
        for (int i = 0; i < variants.length; i++) {
            float a = appear(750 + i * 90L, 500);
            if (a < 0.02f) continue;
            HelperVariant v = variants[i];
            int x = left + i * (CARD_W + CARD_GAP);
            boolean selected = i == sel;
            boolean hover = mx >= x - 3 && mx <= x + CARD_W + 3 && my >= top - 6 && my <= top + 46;
            float lift = (selected ? 4f : (hover ? 2f : 0f));
            int cxm = x + CARD_W / 2;

            if (selected) {
                glow(g, cxm, top + 36, 24, 7, v.glowColor, 0.5f * a);
            }
            HelperEntity e = dummies[i];
            if (e != null) {
                float h = Math.max(1.4f, e.getBbHeight());
                float scale = (selected ? 36f : 31f) / h;
                float sway = selected ? (float) Math.sin(t * 1.3) * 25f : (hover ? (float) Math.sin(t * 2.0) * 18f : 0f);
                drawEntity(g, e, cxm, top + 36 - lift + (1f - a) * 10f, scale, sway);
            }
            int lineCol = selected ? v.glowColor : (hover ? v.titleColor : 0x5A5373);
            int lw = selected ? CARD_W : (hover ? 16 : 8);
            g.fill(cxm - lw / 2, top + 40, cxm + (lw + 1) / 2, top + 41, argb(lineCol, (selected ? 1f : 0.8f) * a));
            if (selected) {
                textCentered(g, v.title.toUpperCase(), cxm, top + 44, v.titleColor, a, 0.7f, false);
            }
        }
    }

    // ------------------------------------------------------------------ input

    private boolean inRect(double mx, double my, double x, double y, double w, double h) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (closing) return true;
        if (button != 0) return super.mouseClicked(mouseX, mouseY, button);

        float cx = stageX();
        float uu = u();
        float sc = nameScale();
        float nw = this.font.width(names[sel]) * sc;
        float ny = nameY();
        boolean editing = nameBox.isFocused();

        // dice reroll
        int dx = (int) (cx + Math.max(nw, 40) / 2f + 9);
        int dy = (int) (ny + 1);
        if (!editing && inRect(mouseX, mouseY, dx, dy, 11, 11) || editing && inRect(mouseX, mouseY, dx + 10, dy, 11, 11)) {
            String nn = variants[sel].getRandomName(random);
            names[sel] = nn;
            nameBox.setValue(nn);
            blurName();
            if (this.minecraft != null) {
                this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.4F));
            }
            return true;
        }

        // rename
        if (!editing && inRect(mouseX, mouseY, cx - nw / 2f - 4, ny - 2, nw + 8, 12 * sc)) {
            nameBox.visible = true;
            this.setFocused(nameBox);
            nameBox.setFocused(true);
            nameBox.moveCursorToEnd(false);
            nameBox.setHighlightPos(0);
            return true;
        }
        if (editing) {
            if (nameBox.mouseClicked(mouseX, mouseY, button)) return true;
            commitName();
            blurName();
        }

        // main button
        if (inRect(mouseX, mouseY, btnX(), btnY(), btnW(), 18)) {
            confirm();
            return true;
        }

        // chevrons
        float chY = feetY() - this.height * 0.18f;
        float chOff = 62 * uu;
        if (inRect(mouseX, mouseY, cx - chOff - 14, chY - 20, 28, 40)) {
            select(sel - 1, true);
            return true;
        }
        if (inRect(mouseX, mouseY, cx + chOff - 14, chY - 20, 28, 40)) {
            select(sel + 1, true);
            return true;
        }

        // strip
        int top = stripTop();
        int left = stripLeft();
        for (int i = 0; i < variants.length; i++) {
            int x = left + i * (CARD_W + CARD_GAP);
            if (inRect(mouseX, mouseY, x - 3, top - 6, CARD_W + 6, 52)) {
                select(i, true);
                return true;
            }
        }

        // grab the hero to spin it
        if (inRect(mouseX, mouseY, cx - 60 * uu, this.height * 0.1f, 120 * uu, feetY() - this.height * 0.1f)) {
            dragging = true;
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dx, double dy) {
        if (dragging) {
            spin += (float) dx * 1.6f;
            spinPausedUntil = Util.getMillis() + 2500;
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (dragging) {
            dragging = false;
            spinPausedUntil = Util.getMillis() + 2500;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (!closing && scrollY != 0) {
            select(sel + (scrollY < 0 ? 1 : -1), true);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (closing) return true;
        if (nameBox.isFocused()) {
            if (keyCode == 257 || keyCode == 335 || keyCode == 256) { // enter, keypad enter, escape
                commitName();
                blurName();
                return true;
            }
            return nameBox.keyPressed(keyCode, scanCode, modifiers) || true;
        }
        switch (keyCode) {
            case 263, 65 -> { // left, A
                select(sel - 1, true);
                return true;
            }
            case 262, 68 -> { // right, D
                select(sel + 1, true);
                return true;
            }
            case 257, 335, 32 -> { // enter, keypad enter, space
                confirm();
                return true;
            }
            default -> {
                if (keyCode >= 49 && keyCode < 49 + variants.length) { // 1..5
                    select(keyCode - 49, true);
                    return true;
                }
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
}
