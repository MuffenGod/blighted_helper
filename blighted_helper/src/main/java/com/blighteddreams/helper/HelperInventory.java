package com.blighteddreams.helper;

import com.blighteddreams.helper.BlightedHelper;
import com.blighteddreams.helper.menu.HelperMenu;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.event.entity.EntityAttributeModificationEvent;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.function.Supplier;

/** Her bag (27 slots, saved with her), her inventory menu, and the player attributes a mob lacks (BODY_SPIKE.md 5.1). */
public final class HelperInventory {
    public static final int BAG_SLOTS = 27;

    private static final DeferredRegister<AttachmentType<?>> ATTACHMENTS = DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, BlightedHelper.MOD_ID);
    private static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, BlightedHelper.MOD_ID);

    public static final Supplier<AttachmentType<ItemStackHandler>> BAG = ATTACHMENTS.register("helper_bag",
            () -> AttachmentType.serializable(() -> new ItemStackHandler(BAG_SLOTS)).build());

    public static final DeferredHolder<MenuType<?>, MenuType<HelperMenu>> MENU = MENUS.register("helper_inventory",
            () -> IMenuTypeExtension.create(HelperMenu::fromNetwork));

    private HelperInventory() {}

    static void register(IEventBus modBus) {
        ATTACHMENTS.register(modBus);
        MENUS.register(modBus);
        modBus.addListener(HelperInventory::onAttributes);
    }

    /**
     * Every registered attribute the helper type lacks gets added with its default value, so items and curios whose
     * modifiers target player-only attributes (attack speed, luck, mod attributes) work on her too.
     */
    private static void onAttributes(EntityAttributeModificationEvent event) {
        var type = HelperEntities.HELPER.get();
        for (Holder.Reference<Attribute> h : BuiltInRegistries.ATTRIBUTE.holders().toList()) {
            if (!event.has(type, h)) event.add(type, h);
        }
    }
}
