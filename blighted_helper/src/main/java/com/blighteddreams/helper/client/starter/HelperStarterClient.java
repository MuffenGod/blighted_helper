package com.blighteddreams.helper.client.starter;

import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;

/**
 * Client entry point for opening the companion starter selection screen.
 */
public final class HelperStarterClient {
    private HelperStarterClient() {}

    public static void openScreen(CompoundTag data) {
        Minecraft mc = Minecraft.getInstance();
        mc.setScreen(new HelperStarterScreen(data));
    }
}
