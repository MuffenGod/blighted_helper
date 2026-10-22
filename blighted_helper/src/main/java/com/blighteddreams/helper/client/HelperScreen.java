package com.blighteddreams.helper.client;

import com.blighteddreams.helper.HelperEntity;
import com.blighteddreams.helper.HelperLeveling;
import com.blighteddreams.helper.WeaponKind;
import com.blighteddreams.helper.menu.HelperMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

import java.util.Locale;

/**
 * Emily's inventory screen. Drawn with plain fills (no texture to ship): armor and hands beside a live preview of her,
 * her bag, the player's inventory, and a Curios column on the left where each slot has an eye toggle that hides or shows
 * that curio on her body, like the player's own Curios screen. Hidden curios keep working; only the drawing stops.
 */
public class HelperScreen extends AbstractContainerScreen<HelperMenu> {
    private static final int BG = 0xF0201A26;
    private static final int EDGE = 0xFF8A6BB0;
    private static final int SLOT = 0xFF120E16;
    private static final int SLOT_EDGE = 0xFF4A3D5A;
    private static final int TEXT = 0xFFE9DDF5;

    public HelperScreen(HelperMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageWidth = 176;
        this.imageHeight = 234;
        this.inventoryLabelY = 141;
        this.titleLabelY = -11;
    }

    private HelperEntity helper() {
        Entity e = minecraft == null || minecraft.level == null ? null : minecraft.level.getEntity(menu.helperId);
        return e instanceof HelperEntity h ? h : null;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        super.render(g, mouseX, mouseY, partial);
        renderEyeTooltip(g, mouseX, mouseY);
        this.renderTooltip(g, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics g, float partial, int mouseX, int mouseY) {
        int x = leftPos, y = topPos;
        panel(g, x, y, imageWidth, imageHeight);
        int cols = menu.curioColumns();
        if (cols > 0) {
            int rows = Math.min(HelperMenu.CURIO_ROWS, menu.curios.size());
            int left = x + HelperMenu.curioX(cols - 1) - 6;
            panel(g, left, y, x - left + 2, rows * 18 + 22);
            g.drawString(font, "Curios", left + 6, y + rows * 18 + 10, 0xFFB9A6D0, false);
        }
        for (Slot s : menu.slots) {
            g.fill(x + s.x - 1, y + s.y - 1, x + s.x + 17, y + s.y + 17, SLOT_EDGE);
            g.fill(x + s.x, y + s.y, x + s.x + 16, y + s.y + 16, SLOT);
        }
        // preview window
        g.fill(x + 26, y + 7, x + 75, y + 79, SLOT_EDGE);
        g.fill(x + 27, y + 8, x + 74, y + 78, 0xFF0B0810);
        HelperEntity h = helper();
        if (h != null) {
            InventoryScreen.renderEntityInInventoryFollowsMouse(g, x + 27, y + 8, x + 74, y + 78, 28, 0.0625F, mouseX, mouseY, h);
            int tx = x + 98;
            g.drawString(font, h.getName().getString(), tx, y + 6, 0xFFF5D0FF, false);
            String lvlText = "★ Lv." + h.getLevel() + (h.getLevel() >= HelperLeveling.MAX_LEVEL ? " §6(MAX)" : " §e(" + h.getXp() + "/" + h.getXpForNextLevel() + " XP)");
            g.drawString(font, lvlText, tx, y + 17, 0xFFFFD700, false);
            g.drawString(font, "❤ " + Math.round(h.getHealth()) + " / " + Math.round(h.getMaxHealth()), tx, y + 28, 0xFFFF8A9A, false);
            int arm = Math.round(h.getSyncedArmor());
            int tgh = Math.round(h.getSyncedToughness());
            String armText = "◈ " + arm + " armor" + (tgh > 0 ? " (+" + tgh + ")" : "");
            g.drawString(font, armText, tx, y + 39, 0xFFB8C8E0, false);
            g.drawString(font, cap(h.getStance().name()) + (h.isDowned() ? ", down" : ""), tx, y + 50, TEXT, false);
            g.drawString(font, cap(WeaponKind.of(h.getMainHandItem(), h.getOffhandItem()).name()), tx, y + 61, 0xFFFFE3A0, false);
        }
        // eye toggles
        for (int i = 0; i < menu.curios.size(); i++) {
            int[] r = eyeRect(i);
            boolean on = menu.curios.get(i).render;
            boolean hover = mouseX >= r[0] && mouseX < r[0] + 10 && mouseY >= r[1] && mouseY < r[1] + 10;
            g.fill(r[0], r[1], r[0] + 10, r[1] + 10, hover ? 0xFF5E4A78 : 0xFF2C2338);
            if (on) {
                g.fill(r[0] + 2, r[1] + 4, r[0] + 8, r[1] + 6, 0xFFEFE6FF);
                g.fill(r[0] + 3, r[1] + 3, r[0] + 7, r[1] + 7, 0xFFEFE6FF);
                g.fill(r[0] + 4, r[1] + 4, r[0] + 6, r[1] + 6, 0xFF6B3FA0);
            } else {
                g.fill(r[0] + 2, r[1] + 5, r[0] + 8, r[1] + 6, 0xFF7A6E88);
                g.fill(r[0] + 2, r[1] + 6, r[0] + 3, r[1] + 7, 0xFF7A6E88);
                g.fill(r[0] + 7, r[1] + 6, r[0] + 8, r[1] + 7, 0xFF7A6E88);
            }
        }
    }

    private static String cap(String s) {
        String l = s.toLowerCase(Locale.ROOT);
        return Character.toUpperCase(l.charAt(0)) + l.substring(1);
    }

    private static void panel(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x - 1, y - 1, x + w + 1, y + h + 1, EDGE);
        g.fill(x, y, x + w, y + h, BG);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        g.drawString(font, "Bag", 98, 72, 0xFFB9A6D0, false);
        g.drawString(font, playerInventoryTitle, 8, inventoryLabelY, 0xFFB9A6D0, false);
    }

    /** Screen rectangle {x, y} of the eye toggle for curio entry i: just left of its slot. */
    private int[] eyeRect(int i) {
        Slot s = menu.slots.get(HelperMenu.CURIO_START + i);
        return new int[]{leftPos + s.x - 12, topPos + s.y + 3};
    }

    private void renderEyeTooltip(GuiGraphics g, int mouseX, int mouseY) {
        for (int i = 0; i < menu.curios.size(); i++) {
            int[] r = eyeRect(i);
            if (mouseX >= r[0] && mouseX < r[0] + 10 && mouseY >= r[1] && mouseY < r[1] + 10) {
                HelperMenu.CurioEntry e = menu.curios.get(i);
                g.renderTooltip(font, Component.literal((e.render ? "Shown on her" : "Hidden") + " (" + e.id + ")"), mouseX, mouseY);
                return;
            }
            Slot s = menu.slots.get(HelperMenu.CURIO_START + i);
            if (!s.hasItem() && isHovering(s.x, s.y, 16, 16, mouseX, mouseY)) {
                g.renderTooltip(font, Component.translatable("curios.identifier." + menu.curios.get(i).id), mouseX, mouseY);
                return;
            }
        }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        for (int i = 0; i < menu.curios.size(); i++) {
            int[] r = eyeRect(i);
            if (mx >= r[0] && mx < r[0] + 10 && my >= r[1] && my < r[1] + 10) {
                menu.curios.get(i).render = !menu.curios.get(i).render;
                if (minecraft != null && minecraft.gameMode != null) minecraft.gameMode.handleInventoryButtonClick(menu.containerId, i);
                return true;
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    protected boolean hasClickedOutside(double mx, double my, int left, int top, int button) {
        int cols = menu.curioColumns();
        if (cols > 0) {
            int panelLeft = left + HelperMenu.curioX(cols - 1) - 6;
            int rows = Math.min(HelperMenu.CURIO_ROWS, menu.curios.size());
            if (mx >= panelLeft && mx < left && my >= top && my < top + rows * 18 + 22) return false;
        }
        return super.hasClickedOutside(mx, my, left, top, button);
    }
}
