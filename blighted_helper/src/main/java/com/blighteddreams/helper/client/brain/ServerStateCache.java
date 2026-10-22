package com.blighteddreams.helper.client.brain;

import com.blighteddreams.helper.network.HelperPayloads;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/** Client side of the server-facts round trip: asks the server for the colony and Key text and remembers the last answer. */
public final class ServerStateCache {
    private static volatile String latest = "";
    private static volatile CompletableFuture<String> waiting;

    private ServerStateCache() {}

    /** Called by the network handler when the server answers. */
    public static void onReply(String text) {
        latest = text == null ? "" : text;
        CompletableFuture<String> w = waiting;
        if (w != null) {
            w.complete(latest);
        }
    }

    /**
     * Asks the server for fresh facts and calls {@code then} with them, or with the last known ones if the server does not
     * answer within {@code timeoutMillis}. {@code then} runs on a background thread.
     */
    public static void fetch(long timeoutMillis, Consumer<String> then) {
        CompletableFuture<String> f = new CompletableFuture<>();
        waiting = f;
        try {
            PacketDistributor.sendToServer(new HelperPayloads.StateRequest());
        } catch (RuntimeException e) { // not connected
            then.accept(latest);
            return;
        }
        f.completeOnTimeout(null, timeoutMillis, TimeUnit.MILLISECONDS).thenAccept(fresh -> then.accept(fresh != null ? fresh : latest));
    }

    /** Forget what the server said (leaving a world). */
    public static void clear() {
        latest = "";
    }
}
