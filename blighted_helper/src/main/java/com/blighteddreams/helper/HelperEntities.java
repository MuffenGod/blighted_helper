package com.blighteddreams.helper;

import com.blighteddreams.helper.BlightedHelper;
import com.blighteddreams.helper.HelperItems;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.CreativeModeTabs;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class HelperEntities {
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, BlightedHelper.MOD_ID);

    public static final DeferredHolder<EntityType<?>, EntityType<HelperEntity>> HELPER = ENTITIES.register("helper",
            () -> EntityType.Builder.<HelperEntity>of(HelperEntity::new, MobCategory.MISC)
                    .sized(0.6F, 2.0F)
                    .eyeHeight(1.78F)
                    .clientTrackingRange(10)
                    .updateInterval(1)
                    .build("helper"));

    public static void register(IEventBus modBus) {
        ENTITIES.register(modBus);
        HelperInventory.register(modBus);
        modBus.addListener(HelperEntities::onAttributes);
        modBus.addListener(HelperEntities::onCreativeTab);
    }

    private static void onAttributes(EntityAttributeCreationEvent event) {
        event.put(HELPER.get(), HelperEntity.createAttributes().build());
    }

    private static void onCreativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
            event.accept(HelperItems.DREAM_CHARM.get());
        }
    }
}
