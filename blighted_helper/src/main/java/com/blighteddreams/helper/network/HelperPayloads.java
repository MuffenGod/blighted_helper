package com.blighteddreams.helper.network;

import com.blighteddreams.helper.BlightedHelper;
import io.netty.buffer.ByteBuf;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Packets of the companion's brain (design/HELPER_PLAN.md section 6d). The brain runs on the client (the API key never
 * leaves it), but some facts only the server knows: the player's colony and its Keys. The client asks, the server answers
 * with a short block of plain text that goes into the model's prompt.
 */
public final class HelperPayloads {
    private HelperPayloads() {}

    private static <T extends CustomPacketPayload> CustomPacketPayload.Type<T> typeOf(String path) {
        return new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(BlightedHelper.MOD_ID, path));
    }

    /** Client to server: send me the server-side facts for my prompt. */
    public record StateRequest() implements CustomPacketPayload {
        public static final Type<StateRequest> TYPE = typeOf("helper_state_request");
        public static final StreamCodec<ByteBuf, StateRequest> CODEC = StreamCodec.unit(new StateRequest());

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Server to client: the facts, as plain text lines. */
    public record StateReply(String text) implements CustomPacketPayload {
        public static final Type<StateReply> TYPE = typeOf("helper_state_reply");
        public static final StreamCodec<ByteBuf, StateReply> CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, StateReply::text, StateReply::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Client to server: request a companion action (e.g. stop attacking, change stance, open inventory). */
    public record ActionRequest(String action, String arg) implements CustomPacketPayload {
        public static final Type<ActionRequest> TYPE = typeOf("helper_action_request");
        public static final StreamCodec<ByteBuf, ActionRequest> CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, ActionRequest::action,
                ByteBufCodecs.STRING_UTF8, ActionRequest::arg,
                ActionRequest::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Client to server: one order for Emily's body (mine, go here, remember this, ...). */
    public record AgentDirective(String verb, String target, int amount, String extra) implements CustomPacketPayload {
        public static final Type<AgentDirective> TYPE = typeOf("helper_agent_directive");
        public static final StreamCodec<ByteBuf, AgentDirective> CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, AgentDirective::verb,
                ByteBufCodecs.STRING_UTF8, AgentDirective::target,
                ByteBufCodecs.VAR_INT, AgentDirective::amount,
                ByteBufCodecs.STRING_UTF8, AgentDirective::extra,
                AgentDirective::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Server to client: stop any speech and reset her voice state after summon/dismiss/replacement. */
    public record VoiceReset() implements CustomPacketPayload {
        public static final Type<VoiceReset> TYPE = typeOf("helper_voice_reset");
        public static final StreamCodec<ByteBuf, VoiceReset> CODEC = StreamCodec.unit(new VoiceReset());

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Server to client: a line Emily wants spoken (and shown in the action bar). */
    public record AgentSay(String text, String mood) implements CustomPacketPayload {
        public static final Type<AgentSay> TYPE = typeOf("helper_agent_say");
        public static final StreamCodec<ByteBuf, AgentSay> CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, AgentSay::text,
                ByteBufCodecs.STRING_UTF8, AgentSay::mood,
                AgentSay::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Server to client: open the companion selection sanctuary UI with saved profiles. */
    public record OpenStarterSelect(CompoundTag data) implements CustomPacketPayload {
        public static final Type<OpenStarterSelect> TYPE = typeOf("helper_open_starter_select");
        public static final StreamCodec<ByteBuf, OpenStarterSelect> CODEC = StreamCodec.composite(
                ByteBufCodecs.COMPOUND_TAG, OpenStarterSelect::data,
                OpenStarterSelect::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Client to server: player chose/switched their companion variant and name. */
    public record ChooseStarter(int variantId, String name) implements CustomPacketPayload {
        public static final Type<ChooseStarter> TYPE = typeOf("helper_choose_starter");
        public static final StreamCodec<ByteBuf, ChooseStarter> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, ChooseStarter::variantId,
                ByteBufCodecs.STRING_UTF8, ChooseStarter::name,
                ChooseStarter::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Client to server: player clicked unsummon in the sanctuary UI. */
    public record UnsummonCompanion() implements CustomPacketPayload {
        public static final Type<UnsummonCompanion> TYPE = typeOf("helper_unsummon_companion");
        public static final StreamCodec<ByteBuf, UnsummonCompanion> CODEC = StreamCodec.unit(new UnsummonCompanion());

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }
}
