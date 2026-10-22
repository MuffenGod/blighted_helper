package com.blighteddreams.helper.spike;

import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * THROWAWAY: Phase 0b body spike (design/BODY_SPIKE.md). Everything in this package is inert unless the JVM is started
 * with -Dblighted.spike=true, so the real instance never runs it. Delete the package (and the one line in
 * BlightedHelper) when the spike is finished.
 */
public final class Spike {
    public static final boolean ENABLED = Boolean.getBoolean("blighted.spike");
    public static final Logger LOG = LoggerFactory.getLogger("blighted_enhancements/spike");
    public static final String NAME = "Emily";

    private Spike() {}

    public static void init(net.neoforged.bus.api.IEventBus modBus) {
        if (!ENABLED) return;
        LOG.info("[SPIKE] body spike is ON (-Dblighted.spike=true): /bdspike commands, fake player body A, client skin override");
        NeoForge.EVENT_BUS.register(SpikeEvents.class);
        SpikeAttachments.init(modBus);
    }

    /** Tab-list-only presence for body B uses its own id so it can coexist with a fake player body A. */
    public static UUID ghostUuidFor(UUID owner) {
        return UUID.nameUUIDFromBytes(("BlightedEmilyGhost:" + owner).getBytes(StandardCharsets.UTF_8));
    }

    /** Stable UUID of the companion of one owner (version 3, so it can never collide with an online-mode account). */
    public static UUID uuidFor(UUID owner) {
        return UUID.nameUUIDFromBytes(("BlightedEmily:" + owner).getBytes(StandardCharsets.UTF_8));
    }
}
