package com.blighteddreams.helper;

import dev.architectury.event.EventResult;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.fml.ModList;
import top.theillusivec4.champions.api.ChampionsApi;
import top.theillusivec4.champions.api.event.ChampionEvents;
import top.theillusivec4.champions.neoforge.platform.NeoForgeAttachmentProvider;

public final class HelperChampionsCompat {
    private static boolean registered = false;

    public static void init() {
        if (!ModList.get().isLoaded("champions")) return;
        if (registered) return;
        try {
            ChampionEvents.SPAWN.register((champion, tier, context) -> {
                if (champion != null && champion.entity() instanceof HelperEntity) {
                    return EventResult.interruptFalse();
                }
                return EventResult.pass();
            });
            registered = true;
            BlightedHelper.LOGGER.info("Registered Champions spawn filter to prevent Helper champions.");
        } catch (Throwable t) {
            BlightedHelper.LOGGER.warn("Failed to register Champions spawn listener: {}", t.getMessage());
        }
    }

    public static void stripChampion(LivingEntity entity) {
        if (!ModList.get().isLoaded("champions") || entity == null) return;
        try {
            if (ChampionsApi.get().isChampion(entity)) {
                if (entity.hasData(NeoForgeAttachmentProvider.CHAMPION_DATA)) {
                    entity.removeData(NeoForgeAttachmentProvider.CHAMPION_DATA);
                }
                if (entity.hasData(NeoForgeAttachmentProvider.CHAMPION_DATA_CLIENT)) {
                    entity.removeData(NeoForgeAttachmentProvider.CHAMPION_DATA_CLIENT);
                }
            }
        } catch (Throwable ignored) {
        }
    }
}
