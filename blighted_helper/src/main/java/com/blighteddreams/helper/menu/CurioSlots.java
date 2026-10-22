package com.blighteddreams.helper.menu;

import com.blighteddreams.helper.HelperEntity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.SlotItemHandler;
import net.neoforged.neoforge.network.PacketDistributor;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.type.capability.ICuriosItemHandler;
import top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler;
import top.theillusivec4.curios.common.network.server.sync.SPacketSyncRender;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Everything in the helper menu that touches Curios. Only loaded when Curios is installed. */
final class CurioSlots {
    private CurioSlots() {}

    static List<HelperMenu.CurioEntry> entries(LivingEntity e) {
        List<HelperMenu.CurioEntry> out = new ArrayList<>();
        Optional<ICuriosItemHandler> inv = CuriosApi.getCuriosInventory(e);
        if (inv.isEmpty()) return out;
        for (Map.Entry<String, ICurioStacksHandler> en : inv.get().getCurios().entrySet()) {
            ICurioStacksHandler h = en.getValue();
            if (!h.isVisible()) continue;
            for (int i = 0; i < h.getSlots(); i++) {
                boolean render = i >= h.getRenders().size() || h.getRenders().get(i);
                out.add(new HelperMenu.CurioEntry(en.getKey(), i, render));
            }
        }
        return out;
    }

    static Slot slot(HelperEntity helper, String id, int index, int x, int y) {
        IItemHandlerModifiable stacks = CuriosApi.getCuriosInventory(helper).flatMap(inv -> inv.getStacksHandler(id))
                .<IItemHandlerModifiable>map(ICurioStacksHandler::getStacks).orElseGet(() -> new ItemStackHandler(index + 1));
        return new CurioSlot(stacks, index, x, y, helper, id);
    }

    /** Client copy: contents come from the menu sync; the validity check uses her client entity when it is loaded. */
    static Slot clientSlot(LivingEntity owner, String id, int x, int y) {
        return new CurioSlot(new ItemStackHandler(1), 0, x, y, owner, id);
    }

    static void setRender(HelperEntity helper, String id, int index, boolean render) {
        CuriosApi.getCuriosInventory(helper).flatMap(inv -> inv.getStacksHandler(id)).ifPresent(h -> {
            if (index < h.getRenders().size()) {
                h.getRenders().set(index, render);
                PacketDistributor.sendToPlayersTrackingEntity(helper, new SPacketSyncRender(helper.getId(), id, index, render));
            }
        });
    }

    private static final class CurioSlot extends SlotItemHandler {
        private final LivingEntity owner;
        private final String id;
        private final int curioIndex;

        CurioSlot(IItemHandlerModifiable handler, int index, int x, int y, LivingEntity owner, String id) {
            super(handler, index, x, y);
            this.owner = owner;
            this.id = id;
            this.curioIndex = index;
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            if (stack.isEmpty()) return false;
            if (owner == null) return true;
            SlotContext ctx = new SlotContext(id, owner, curioIndex, false, true);
            return CuriosApi.isStackValid(ctx, stack) && CuriosApi.getCurio(stack).map(c -> c.canEquip(ctx)).orElse(true);
        }

        @Override
        public int getMaxStackSize() {
            return 1;
        }
    }
}
