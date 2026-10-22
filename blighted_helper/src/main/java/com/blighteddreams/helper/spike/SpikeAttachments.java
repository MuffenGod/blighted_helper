package com.blighteddreams.helper.spike;

import com.blighteddreams.helper.BlightedHelper;
import com.blighteddreams.helper.HelperEntities;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.common.NeoForgeMod;
import net.neoforged.neoforge.event.entity.EntityAttributeModificationEvent;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** Body B additions that cost no change to HelperEntity: a 36-slot inventory as a data attachment, and every missing player attribute. */
final class SpikeAttachments {
    private SpikeAttachments() {}

    static final DeferredRegister<AttachmentType<?>> ATTACHMENTS = DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, BlightedHelper.MOD_ID);
    static final Supplier<AttachmentType<ItemStackHandler>> INVENTORY = ATTACHMENTS.register("spike_helper_inventory",
            () -> AttachmentType.serializable(() -> new ItemStackHandler(36)).build());

    static void init(IEventBus modBus) {
        ATTACHMENTS.register(modBus);
        if (!Boolean.getBoolean("blighted.spike.noattrfix")) modBus.addListener(SpikeAttachments::onAttributes);
    }

    static final List<String> ADDED = new ArrayList<>();

    /**
     * Every attribute any mod registered that the helper type lacks gets added with its default value. Without this, an
     * item whose modifier targets (say) attack speed, block break speed or a mod's own attribute silently does nothing on her.
     */
    @SubscribeEvent
    static void onAttributes(EntityAttributeModificationEvent event) {
        var type = HelperEntities.HELPER.get();
        int n = 0;
        for (Holder.Reference<Attribute> h : BuiltInRegistries.ATTRIBUTE.holders().toList()) {
            if (!event.has(type, h)) {
                event.add(type, h);
                ADDED.add(h.key().location().toString());
                n++;
            }
        }
        Spike.LOG.info("[SPIKE] attribute fix: added {} missing attributes to {}", n, type.builtInRegistryHolder().key().location());
    }
}
