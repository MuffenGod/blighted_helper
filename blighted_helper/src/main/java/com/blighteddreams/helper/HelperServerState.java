package com.blighteddreams.helper;

import net.minecraft.server.level.ServerPlayer;

/** The part of the companion's prompt only the server knows. Server side. */
public final class HelperServerState {
    private HelperServerState() {}

    public static String build(ServerPlayer player) {
        if (player == null) return "";
        StringBuilder sb = new StringBuilder(HelperProgression.build(player));
        try {
            HelperEntity helper = HelperManager.find(player);
            if (helper != null) {
                sb.append("\n\n[").append(helper.getName().getString()).append("'s Body]\n");
                sb.append("Current activity: ").append(helper.agent().statusText()).append("\n");
                sb.append("Autonomy: ").append(helper.agent().autonomy().name().toLowerCase(java.util.Locale.ROOT)).append("\n");
                if (helper.getOwnerId() != null && helper.level() instanceof net.minecraft.server.level.ServerLevel level) {
                    sb.append(com.blighteddreams.helper.agent.EmilyMemory.get(level)
                            .describe(helper.getOwnerId(), level.dimension().location().toString(),
                                    helper.blockPosition(), 12));
                }
            }
        } catch (Throwable ignored) {
        }
        return sb.toString();
    }
}

