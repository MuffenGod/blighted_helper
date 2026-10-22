package com.blighteddreams.helper.menu;

import com.blighteddreams.helper.HelperEntity;
import com.blighteddreams.helper.HelperInventory;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.SlotItemHandler;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * Emily's inventory: her armor, both hands, her 27-slot bag, and every Curios slot she has, each with a "show it on her"
 * toggle like the player's own Curios screen. Slot order: 0-3 armor, 4 main hand, 5 off hand, 6-32 bag, then curios,
 * then the player's 36.
 */
public class HelperMenu extends AbstractContainerMenu {
    public static final EquipmentSlot[] GEAR = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET,
            EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND};
    public static final int BAG_START = GEAR.length;
    public static final int CURIO_START = BAG_START + HelperInventory.BAG_SLOTS;

    /** One curios slot as the screen sees it. {@code render} is mutable so the screen can flip it at once. */
    public static final class CurioEntry {
        public final String id;
        public final int index;
        public boolean render;

        CurioEntry(String id, int index, boolean render) {
            this.id = id;
            this.index = index;
            this.render = render;
        }
    }

    public final int helperId;
    @Nullable
    private final HelperEntity helper;
    public final List<CurioEntry> curios;
    private final int playerStart;

    /** Server side. */
    public HelperMenu(int windowId, Inventory playerInv, HelperEntity helper) {
        this(windowId, playerInv, helper, helper.getId(), ModList.get().isLoaded("curios") ? CurioSlots.entries(helper) : List.of());
    }

    /** Client side, from the open packet. */
    public static HelperMenu fromNetwork(int windowId, Inventory playerInv, RegistryFriendlyByteBuf buf) {
        int id = buf.readVarInt();
        int n = buf.readVarInt();
        List<CurioEntry> list = new ArrayList<>(n);
        for (int i = 0; i < n; i++) list.add(new CurioEntry(buf.readUtf(), buf.readVarInt(), buf.readBoolean()));
        return new HelperMenu(windowId, playerInv, null, id, list);
    }

    public static void writeOpenData(RegistryFriendlyByteBuf buf, HelperEntity helper) {
        buf.writeVarInt(helper.getId());
        List<CurioEntry> list = ModList.get().isLoaded("curios") ? CurioSlots.entries(helper) : List.of();
        buf.writeVarInt(list.size());
        for (CurioEntry e : list) {
            buf.writeUtf(e.id);
            buf.writeVarInt(e.index);
            buf.writeBoolean(e.render);
        }
    }

    private HelperMenu(int windowId, Inventory playerInv, @Nullable HelperEntity helper, int helperId, List<CurioEntry> curios) {
        super(HelperInventory.MENU.get(), windowId);
        this.helper = helper;
        this.helperId = helperId;
        this.curios = curios;

        Container gear = helper != null ? new GearContainer(helper) : new SimpleContainer(GEAR.length);
        for (int i = 0; i < 4; i++) {
            EquipmentSlot es = GEAR[i];
            addSlot(new Slot(gear, i, 8, 8 + i * 18) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return stack.getEquipmentSlot() == es || playerInv.player.getEquipmentSlotForItem(stack) == es;
                }

                @Override
                public int getMaxStackSize() {
                    return 1;
                }
            });
        }
        addSlot(new Slot(gear, 4, 77, 62));
        addSlot(new Slot(gear, 5, 77, 44));

        IItemHandlerModifiable bag = helper != null ? helper.getData(HelperInventory.BAG) : new ItemStackHandler(HelperInventory.BAG_SLOTS);
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 9; c++) {
                addSlot(new SlotItemHandler(bag, c + r * 9, 8 + c * 18, 84 + r * 18));
            }
        }

        for (int i = 0; i < curios.size(); i++) {
            int col = i / CURIO_ROWS;
            int row = i % CURIO_ROWS;
            int x = curioX(col);
            int y = 8 + row * 18;
            CurioEntry e = curios.get(i);
            if (helper != null) {
                addSlot(CurioSlots.slot(helper, e.id, e.index, x, y));
            } else {
                addSlot(ModList.get().isLoaded("curios") ? CurioSlots.clientSlot(playerInv.player.level().getEntity(helperId) instanceof HelperEntity he ? he : null, e.id, x, y)
                        : new SlotItemHandler(new ItemStackHandler(1), 0, x, y));
            }
        }

        playerStart = slots.size();
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 9; c++) {
                addSlot(new Slot(playerInv, c + r * 9 + 9, 8 + c * 18, 152 + r * 18));
            }
        }
        for (int c = 0; c < 9; c++) {
            addSlot(new Slot(playerInv, c, 8 + c * 18, 210));
        }
    }

    public static final int CURIO_ROWS = 8;

    /** Left edge of a curios column; columns grow leftwards from the main panel, 12 px of each is the eye toggle. */
    public static int curioX(int col) {
        return -24 - col * 32;
    }

    public int curioColumns() {
        return (curios.size() + CURIO_ROWS - 1) / CURIO_ROWS;
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (id < 0 || id >= curios.size() || helper == null) return false;
        CurioEntry e = curios.get(id);
        e.render = !e.render;
        if (ModList.get().isLoaded("curios")) CurioSlots.setRender(helper, e.id, e.index, e.render);
        return true;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        ItemStack copy = stack.copy();
        if (index >= playerStart) {
            // armor first, then curios, then her bag
            if (!moveItemStackTo(stack, 0, 4, false)
                    && !moveItemStackTo(stack, CURIO_START, playerStart, false)
                    && !moveItemStackTo(stack, BAG_START, CURIO_START, false)) {
                return ItemStack.EMPTY;
            }
        } else if (!moveItemStackTo(stack, playerStart, slots.size(), true)) {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
        else slot.setChanged();
        return copy;
    }

    @Override
    public boolean stillValid(Player player) {
        if (helper == null) return true;
        return helper.isAlive() && !helper.isRemoved() && helper.isOwner(player) && helper.distanceToSqr(player) < 100.0;
    }

    @Nullable
    public HelperEntity helper() {
        return helper;
    }

    /** Her six equipment slots as a Container (server side). */
    private static final class GearContainer implements Container {
        private final HelperEntity h;

        GearContainer(HelperEntity h) {
            this.h = h;
        }

        @Override public int getContainerSize() { return GEAR.length; }
        @Override public boolean isEmpty() {
            for (EquipmentSlot s : GEAR) if (!h.getItemBySlot(s).isEmpty()) return false;
            return true;
        }
        @Override public ItemStack getItem(int i) { return h.getItemBySlot(GEAR[i]); }
        @Override public ItemStack removeItem(int i, int n) {
            ItemStack cur = h.getItemBySlot(GEAR[i]);
            if (cur.isEmpty() || n <= 0) return ItemStack.EMPTY;
            ItemStack out = cur.split(n);
            h.setItemSlot(GEAR[i], cur.isEmpty() ? ItemStack.EMPTY : cur);
            return out;
        }
        @Override public ItemStack removeItemNoUpdate(int i) {
            ItemStack cur = h.getItemBySlot(GEAR[i]);
            h.setItemSlot(GEAR[i], ItemStack.EMPTY);
            return cur;
        }
        @Override public void setItem(int i, ItemStack stack) { h.setItemSlot(GEAR[i], stack); }
        @Override public void setChanged() {}
        @Override public boolean stillValid(Player p) { return true; }
        @Override public void clearContent() { for (EquipmentSlot s : GEAR) h.setItemSlot(s, ItemStack.EMPTY); }
    }

    /** Opens her inventory for her owner. */
    public static void open(ServerPlayer player, HelperEntity helper) {
        player.openMenu(new net.minecraft.world.SimpleMenuProvider(
                (id, inv, p) -> new HelperMenu(id, inv, helper), helper.getDisplayName()), buf -> writeOpenData(buf, helper));
    }
}
