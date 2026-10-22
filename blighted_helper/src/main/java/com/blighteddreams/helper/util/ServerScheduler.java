package com.blighteddreams.helper.util;

import net.minecraft.server.MinecraftServer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayList;
import java.util.List;

/** Minimal "run this on the server thread N ticks from now" queue. Register the class on the NeoForge event bus. */
public final class ServerScheduler {
    private record Task(long dueTick, Runnable action) {}

    private static final List<Task> TASKS = new ArrayList<>();

    private ServerScheduler() {}

    public static synchronized void later(MinecraftServer server, int ticks, Runnable action) {
        TASKS.add(new Task(server.getTickCount() + Math.max(1, ticks), action));
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        long now = event.getServer().getTickCount();
        List<Runnable> due = new ArrayList<>();
        synchronized (ServerScheduler.class) {
            TASKS.removeIf(t -> {
                if (t.dueTick() <= now) {
                    due.add(t.action());
                    return true;
                }
                return false;
            });
        }
        for (Runnable r : due) {
            try {
                r.run();
            } catch (Throwable t) {
                com.blighteddreams.helper.BlightedHelper.LOGGER.error("Scheduled task failed", t);
            }
        }
    }
}
