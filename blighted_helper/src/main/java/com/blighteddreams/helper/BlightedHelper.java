package com.blighteddreams.helper;

import com.blighteddreams.helper.network.HelperNetworking;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(BlightedHelper.MOD_ID)
public class BlightedHelper {
    public static final String MOD_ID = "blighted_helper";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public BlightedHelper(IEventBus modEventBus, ModContainer modContainer) {
        LOGGER.info("Initializing Blighted Helper (Emily AI Companion)");

        // Configurations
        modContainer.registerConfig(ModConfig.Type.COMMON, HelperConfig.SPEC, "blighted_helper-common.toml");
        modContainer.registerConfig(ModConfig.Type.CLIENT, VoiceConfig.SPEC, "blighted_helper-voice.toml");
        modContainer.registerConfig(ModConfig.Type.CLIENT, BrainConfig.SPEC, "blighted_helper-brain.toml");
        modContainer.registerConfig(ModConfig.Type.CLIENT, AvatarConfig.SPEC, "blighted_helper-avatar.toml");

        // Items & Entities
        HelperItems.register(modEventBus);
        HelperEntities.register(modEventBus);

        // Network Payloads
        modEventBus.addListener(HelperNetworking::registerPayloads);

        // Event Listeners
        NeoForge.EVENT_BUS.register(HelperManager.class);
        NeoForge.EVENT_BUS.register(HelperCommands.class);
        NeoForge.EVENT_BUS.register(com.blighteddreams.helper.util.ServerScheduler.class);

        // Champions Compat (Prevents Helper from becoming a champion)
        HelperChampionsCompat.init();

        // Phase 0b spike (inert unless -Dblighted.spike=true)
        com.blighteddreams.helper.spike.Spike.init(modEventBus);
    }
}
