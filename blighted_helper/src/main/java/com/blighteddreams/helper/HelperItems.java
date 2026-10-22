package com.blighteddreams.helper;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public class HelperItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(BlightedHelper.MOD_ID);

    public static final DeferredItem<Item> DREAM_CHARM = ITEMS.register("dream_charm",
            () -> new DreamCharmItem(new Item.Properties()
                    .stacksTo(1)
                    .rarity(Rarity.RARE)));

    public static void register(IEventBus eventBus) {
        ITEMS.register(eventBus);
    }
}
