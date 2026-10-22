package com.blighteddreams.helper.client;

import com.blighteddreams.helper.BlightedHelper;
import com.blighteddreams.helper.HelperInventory;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

/** Mod-bus client registrations for the companion. */
@EventBusSubscriber(modid = BlightedHelper.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class HelperClientMod {
    private HelperClientMod() {}

    @SubscribeEvent
    public static void onScreens(RegisterMenuScreensEvent event) {
        event.register(HelperInventory.MENU.get(), HelperScreen::new);
    }
}
