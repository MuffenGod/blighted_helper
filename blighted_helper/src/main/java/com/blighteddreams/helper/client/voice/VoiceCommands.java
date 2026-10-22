package com.blighteddreams.helper.client.voice;

import com.blighteddreams.helper.BlightedHelper;
import com.blighteddreams.helper.VoiceConfig;
import com.blighteddreams.helper.ai.HelperMoods;
import com.blighteddreams.helper.client.brain.HelperBrain;
import com.blighteddreams.helper.client.brain.ServerStateCache;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;

/**
 * Client commands for the companion's voice and AI brain:
 * /helpervoice say <text>, /helpervoice mood <mood> <text>, /helpervoice start, /helpervoice autostart <on>,
 * /helpervoice proactive <on>, /helpervoice stop, /helpervoice status, /helpervoice ask <question>,
 * /helpervoice forget, /helpervoice brain.
 */
@EventBusSubscriber(modid = BlightedHelper.MOD_ID, value = Dist.CLIENT)
public final class VoiceCommands {
    private VoiceCommands() {}

    @SubscribeEvent
    public static void onRegisterClientCommands(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("helpervoice")
                .then(Commands.literal("say")
                        .then(Commands.argument("text", StringArgumentType.greedyString())
                                .executes(ctx -> say(ctx, null))))
                .then(Commands.literal("mood")
                        .then(Commands.argument("mood", StringArgumentType.word())
                                .suggests((c, b) -> SharedSuggestionProvider.suggest(HelperMoods.all(), b))
                                .then(Commands.argument("text", StringArgumentType.greedyString())
                                        .executes(ctx -> say(ctx, StringArgumentType.getString(ctx, "mood"))))))
                .then(Commands.literal("ask")
                        .then(Commands.argument("text", StringArgumentType.greedyString())
                                .executes(ctx -> {
                                    HelperBrain.ask(StringArgumentType.getString(ctx, "text"));
                                    return 1;
                                })))
                .then(Commands.literal("start").executes(ctx -> {
                    VoiceServerLauncher.ensureRunningAsync();
                    HelperVoice.chat("Checking / starting local Emily voice server in the background...");
                    return 1;
                }))
                .then(Commands.literal("autostart")
                        .then(Commands.argument("on", BoolArgumentType.bool()).executes(ctx -> {
                            boolean on = BoolArgumentType.getBool(ctx, "on");
                            VoiceConfig.setAutoStart(on);
                            if (on) {
                                VoiceServerLauncher.ensureRunningAsync();
                            }
                            HelperVoice.chat("Voice server auto-start is now " + (on ? "on" : "off") + ".");
                            return 1;
                        })))
                .then(Commands.literal("proactive")
                        .then(Commands.argument("on", BoolArgumentType.bool()).executes(ctx -> {
                            boolean on = BoolArgumentType.getBool(ctx, "on");
                            VoiceConfig.setProactiveAi(on);
                            HelperVoice.chat("Proactive AI reactions & natural chat ('Emily, ...') are now " + (on ? "on" : "off") + ".");
                            return 1;
                        })))
                .then(Commands.literal("forget").executes(ctx -> {
                    HelperBrain.forget();
                    HelperVoice.chat("She has forgotten the conversation so far.");
                    return 1;
                }))
                .then(Commands.literal("brain").executes(ctx -> {
                    HelperBrain.status(HelperVoice::chat);
                    return 1;
                }))
                .then(Commands.literal("stop").executes(ctx -> {
                    HelperVoice.stop();
                    HelperBrain.stop();
                    return 1;
                }))
                .then(Commands.literal("status").executes(ctx -> {
                    HelperVoice.chat("Voice config: autoStart=" + VoiceConfig.autoStart() + ", proactiveAi=" + VoiceConfig.proactiveAi());
                    HelperVoice.status(HelperVoice::chat);
                    return 1;
                })));
    }

    private static int say(CommandContext<CommandSourceStack> ctx, String mood) {
        String text = StringArgumentType.getString(ctx, "text");
        HelperVoice.speak(text, mood);
        ctx.getSource().sendSuccess(() -> Component.literal("Speaking" + (mood == null ? "" : " (" + mood + ")") + ": " + text), false);
        return 1;
    }

    /** Stop talking when leaving the world. */
    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        HelperVoice.stop();
        HelperBrain.stop();
        ServerStateCache.clear();
    }
}
