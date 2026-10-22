package com.blighteddreams.helper.network;

import com.blighteddreams.helper.HelperServerState;
import com.blighteddreams.helper.client.brain.ServerStateCache;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public class HelperNetworking {
    public static void registerPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1.0.0");

        registrar.playToServer(HelperPayloads.StateRequest.TYPE, HelperPayloads.StateRequest.CODEC, (p, ctx) -> ctx.enqueueWork(() -> {
            if (ctx.player() instanceof ServerPlayer sp) {
                PacketDistributor.sendToPlayer(sp, new HelperPayloads.StateReply(HelperServerState.build(sp)));
            }
        }));

        registrar.playToClient(HelperPayloads.StateReply.TYPE, HelperPayloads.StateReply.CODEC,
                (p, ctx) -> ctx.enqueueWork(() -> ServerStateCache.onReply(p.text())));

        registrar.playToClient(HelperPayloads.OpenStarterSelect.TYPE, HelperPayloads.OpenStarterSelect.CODEC,
                (p, ctx) -> ctx.enqueueWork(() -> com.blighteddreams.helper.client.starter.HelperStarterClient.openScreen(p.data())));

        registrar.playToServer(HelperPayloads.ChooseStarter.TYPE, HelperPayloads.ChooseStarter.CODEC, (p, ctx) -> ctx.enqueueWork(() -> {
            if (ctx.player() instanceof ServerPlayer sp) {
                com.blighteddreams.helper.HelperManager.chooseStarter(sp, p.variantId(), p.name());
            }
        }));

        registrar.playToServer(HelperPayloads.UnsummonCompanion.TYPE, HelperPayloads.UnsummonCompanion.CODEC, (p, ctx) -> ctx.enqueueWork(() -> {
            if (ctx.player() instanceof ServerPlayer sp) {
                com.blighteddreams.helper.HelperManager.unsummon(sp);
            }
        }));

        registrar.playToServer(HelperPayloads.AgentDirective.TYPE, HelperPayloads.AgentDirective.CODEC, (p, ctx) -> ctx.enqueueWork(() -> {
            if (ctx.player() instanceof ServerPlayer sp) {
                com.blighteddreams.helper.HelperEntity h = com.blighteddreams.helper.HelperManager.find(sp);
                if (h != null) {
                    h.agent().assign(new com.blighteddreams.helper.agent.EmilyDirective(
                            p.verb(), p.target(), p.amount(), p.extra()));
                }
            }
        }));

        registrar.playToClient(HelperPayloads.VoiceReset.TYPE, HelperPayloads.VoiceReset.CODEC,
                (p, ctx) -> ctx.enqueueWork(() -> com.blighteddreams.helper.client.brain.HelperAutoAi.onCompanionChanged()));

        registrar.playToClient(HelperPayloads.AgentSay.TYPE, HelperPayloads.AgentSay.CODEC,
                (p, ctx) -> ctx.enqueueWork(() -> com.blighteddreams.helper.client.voice.HelperVoice.speak(p.text(), p.mood())));

        registrar.playToServer(HelperPayloads.ActionRequest.TYPE, HelperPayloads.ActionRequest.CODEC, (p, ctx) -> ctx.enqueueWork(() -> {
            if (ctx.player() instanceof ServerPlayer sp) {
                com.blighteddreams.helper.HelperEntity h = com.blighteddreams.helper.HelperManager.find(sp);
                if (h == null) return;
                String act = p.action().toLowerCase(java.util.Locale.ROOT);
                switch (act) {
                    case "stop" -> {
                        h.agent().abortTask(false);
                        h.stopAttacking();
                        sp.displayClientMessage(net.minecraft.network.chat.Component.literal(h.getName().getString() + " stopped attacking.").withStyle(net.minecraft.ChatFormatting.GREEN), true);
                    }
                    case "target" -> {
                        try {
                            int targetId = Integer.parseInt(p.arg());
                            net.minecraft.world.entity.Entity e = sp.serverLevel().getEntity(targetId);
                            if (e instanceof net.minecraft.world.entity.LivingEntity le && le.isAlive() && le != sp && le != h) {
                                h.agent().abortTask(true);
                                h.commandAttack(le);
                                sp.displayClientMessage(net.minecraft.network.chat.Component.literal("§c[" + h.getName().getString() + " -> " + le.getName().getString() + "] §fTarget acquired!").withStyle(net.minecraft.ChatFormatting.RED), true);
                            }
                        } catch (NumberFormatException ignored) {}
                    }
                    case "stance" -> {
                        try {
                            com.blighteddreams.helper.HelperEntity.Stance s = com.blighteddreams.helper.HelperEntity.Stance.valueOf(p.arg().toUpperCase(java.util.Locale.ROOT));
                            h.agent().abortTask(true);
                            h.setStance(s);
                            h.stopAttacking();
                            sp.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.blighted_helper.helper_stance." + s.name().toLowerCase(java.util.Locale.ROOT), h.getDisplayName()), true);
                        } catch (IllegalArgumentException ignored) {}
                    }
                    case "tactic" -> {
                        com.blighteddreams.helper.HelperEntity.CombatTactic t = com.blighteddreams.helper.HelperEntity.CombatTactic.fromString(p.arg());
                        h.setCombatTactic(t);
                        sp.displayClientMessage(net.minecraft.network.chat.Component.literal("⚔ Combat tactic set to: " + t.name()).withStyle(net.minecraft.ChatFormatting.GOLD), true);
                    }
                    case "inventory" -> {
                        if (h.distanceToSqr(sp) <= 100.0) {
                            com.blighteddreams.helper.menu.HelperMenu.open(sp, h);
                        }
                    }
                    case "summon" -> {
                        com.blighteddreams.helper.HelperManager.summonOrRecall(sp);
                    }
                }
            }
        }));
    }
}
