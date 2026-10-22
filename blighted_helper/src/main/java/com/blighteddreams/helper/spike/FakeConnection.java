package com.blighteddreams.helper.spike;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.network.Connection;
import net.minecraft.network.UnconfiguredPipelineHandler;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * A server-side Connection with nobody on the other end: an EmbeddedChannel whose last outbound stage swallows every
 * packet (so nothing is encoded or buffered), plus NeoForge's own "mock connection" setup so that custom payloads other
 * mods send to her pass NetworkRegistry.checkPacket instead of throwing "Payload X may not be sent to the client".
 */
final class FakeConnection {
    private FakeConnection() {}

    /** Counts what the server tried to send her; the spike reads this for evidence (what do mods push at a player?). */
    static final class Sink extends ChannelOutboundHandlerAdapter {
        final AtomicLong total = new AtomicLong();
        final Map<String, AtomicLong> byType = new ConcurrentHashMap<>();

        @Override
        public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
            if (msg instanceof Packet<?> p) {
                total.incrementAndGet();
                String key = p instanceof net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket cp ? "payload:" + cp.payload().type().id() : p.type().id().toString();
                byType.computeIfAbsent(key, k -> new AtomicLong()).incrementAndGet();
                promise.setSuccess();
            } else {
                ctx.write(msg, promise); // pipeline configuration tasks (setupInboundProtocol) still reach the Inbound handler
            }
        }
    }

    record Made(Connection connection, EmbeddedChannel channel, Sink sink) {}

    static Made create() {
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        Sink sink = new Sink();
        // head -> tail: inbound config handler, the Connection itself, the sink. Writes travel tail -> head, so the sink sees
        // every packet first and the config handler only ever sees non-packet tasks.
        EmbeddedChannel channel = new EmbeddedChannel(new UnconfiguredPipelineHandler.Inbound(), connection, sink);
        NetworkRegistry.configureMockConnection(connection);
        return new Made(connection, channel, sink);
    }
}
