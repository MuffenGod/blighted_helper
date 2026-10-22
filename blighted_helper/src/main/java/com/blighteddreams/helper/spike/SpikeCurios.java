package com.blighteddreams.helper.spike;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.type.capability.ICuriosItemHandler;
import top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Curios access for the spike. Only loaded when curios is present (callers check ModList). */
final class SpikeCurios {
    private SpikeCurios() {}

    static List<String> describe(LivingEntity e) {
        List<String> out = new ArrayList<>();
        var inv = CuriosApi.getCuriosInventory(e);
        if (inv.isEmpty()) {
            out.add("curios: NO curios inventory on this entity");
            return out;
        }
        ICuriosItemHandler h = inv.get();
        StringBuilder sb = new StringBuilder("curios inventory present, slot types:");
        for (Map.Entry<String, ICurioStacksHandler> en : h.getCurios().entrySet()) {
            sb.append(' ').append(en.getKey()).append('(').append(en.getValue().getSlots()).append(')');
            for (int i = 0; i < en.getValue().getSlots(); i++) {
                ItemStack st = en.getValue().getStacks().getStackInSlot(i);
                if (!st.isEmpty()) sb.append('=').append(SpikeBodies.stack(st));
            }
        }
        out.add(sb.toString());
        return out;
    }

    /** Puts one item into slot 0 of the given curios slot type. Returns an error string or null on success. */
    static String equip(LivingEntity e, String slot, String itemId) {
        var inv = CuriosApi.getCuriosInventory(e);
        if (inv.isEmpty()) return "no curios inventory on " + e.getName().getString();
        ResourceLocation rl = ResourceLocation.tryParse(itemId);
        if (rl == null || !BuiltInRegistries.ITEM.containsKey(rl)) return "unknown item " + itemId;
        Item item = BuiltInRegistries.ITEM.get(rl);
        var handler = inv.get().getStacksHandler(slot);
        if (handler.isEmpty()) return "no curios slot type '" + slot + "' on " + e.getName().getString() + " (has: " + String.join(",", inv.get().getCurios().keySet()) + ")";
        handler.get().getStacks().setStackInSlot(0, new ItemStack(item));
        return null;
    }
}
