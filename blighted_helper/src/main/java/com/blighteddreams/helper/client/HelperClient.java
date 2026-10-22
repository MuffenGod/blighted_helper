package com.blighteddreams.helper.client;

import com.blighteddreams.helper.BlightedHelper;
import com.blighteddreams.helper.AvatarConfig;
import com.blighteddreams.helper.HelperEntities;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;

@EventBusSubscriber(modid = BlightedHelper.MOD_ID, value = Dist.CLIENT)
public class HelperClient {
    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(HelperEntities.HELPER.get(), HelperAvatarRenderer::new);
    }

    /** F3+T re-reads the standee layer list and custom skin override. */
    @SubscribeEvent
    public static void onRegisterReloadListeners(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener((ResourceManagerReloadListener) rm -> {
            StandeeLayers.invalidate();
            HelperSkinRenderer.invalidateSkinCache();
        });
    }

    /** /helperavatar: change how she is drawn without editing the config file. */
    @SubscribeEvent
    public static void onRegisterClientCommands(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("helperavatar")
                .then(Commands.literal("style")
                        .then(Commands.argument("style", StringArgumentType.word())
                                .suggests((c, b) -> SharedSuggestionProvider.suggest(new String[]{"skin", "standee", "model"}, b))
                                .executes(ctx -> {
                                    AvatarConfig.setStyle(StringArgumentType.getString(ctx, "style"));
                                    return say(ctx, "Style is now " + AvatarConfig.style() + ".");
                                })))
                .then(Commands.literal("dragonparts")
                        .then(Commands.argument("on", BoolArgumentType.bool()).executes(ctx -> {
                            AvatarConfig.setDragonParts(BoolArgumentType.getBool(ctx, "on"));
                            return say(ctx, "3D Dragon horns, wings & tail " + (AvatarConfig.dragonParts() ? "on" : "off") + ".");
                        })))
                .then(Commands.literal("idle")
                        .then(Commands.argument("variant", com.mojang.brigadier.arguments.IntegerArgumentType.integer(0, 7)).executes(ctx -> {
                            int v = com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "variant");
                            int n = 0;
                            for (net.minecraft.world.entity.Entity e : net.minecraft.client.Minecraft.getInstance().level.entitiesForRendering()) {
                                if (e instanceof com.blighteddreams.helper.HelperEntity h) {
                                    h.animIdleVariant = v;
                                    h.animIdleStartTick = h.tickCount;
                                    n++;
                                }
                            }
                            return say(ctx, "Started idle flourish " + v + " on " + n + " helper(s).");
                        })))
                .then(Commands.literal("reloadparts").executes(ctx -> {
                    HelperSkinRenderer.reloadVariantParts();
                    net.minecraft.client.Minecraft.getInstance().reloadResourcePacks();
                    return say(ctx, "Reloaded the variant parts and resource packs (config/blighted_helper-parts.json overrides the built-in file).");
                }))
                .then(Commands.literal("reload").executes(ctx -> {
                    StandeeLayers.invalidate();
                    HelperSkinRenderer.invalidateSkinCache();
                    return say(ctx, "Reloaded Emily skin and standee textures.");
                }))
                .then(Commands.literal("directional")
                        .then(Commands.argument("on", BoolArgumentType.bool()).executes(ctx -> {
                            AvatarConfig.setDirectional(BoolArgumentType.getBool(ctx, "on"));
                            return say(ctx, "Directional billboarding " + (AvatarConfig.directional() ? "on" : "off") + ".");
                        })))
                .then(Commands.literal("shadow")
                        .then(Commands.argument("on", BoolArgumentType.bool()).executes(ctx -> {
                            AvatarConfig.setCastShadow(BoolArgumentType.getBool(ctx, "on"));
                            return say(ctx, "Silhouette ground shadow " + (AvatarConfig.castShadow() ? "on" : "off") + ".");
                        })))
                .then(Commands.literal("envshading")
                        .then(Commands.argument("on", BoolArgumentType.bool()).executes(ctx -> {
                            AvatarConfig.setEnvShading(BoolArgumentType.getBool(ctx, "on"));
                            return say(ctx, "Environmental 3D normal shading " + (AvatarConfig.envShading() ? "on" : "off") + ".");
                        })))
                .then(Commands.literal("hover")
                        .then(Commands.argument("value", DoubleArgumentType.doubleArg(0.0, 1.0)).executes(ctx -> {
                            AvatarConfig.setHover(DoubleArgumentType.getDouble(ctx, "value"));
                            return say(ctx, "Hover height: " + AvatarConfig.hover() + " blocks.");
                        })))
                .then(Commands.literal("hemfade")
                        .then(Commands.argument("value", DoubleArgumentType.doubleArg(0.0, 0.4)).executes(ctx -> {
                            AvatarConfig.setHemFade(DoubleArgumentType.getDouble(ctx, "value"));
                            return say(ctx, "Hem mist fade: " + AvatarConfig.hemFade() + ".");
                        })))
                .then(Commands.literal("depth")
                        .then(Commands.argument("value", DoubleArgumentType.doubleArg(0.0, 4.0)).executes(ctx -> {
                            AvatarConfig.setDepth(DoubleArgumentType.getDouble(ctx, "value"));
                            return say(ctx, "Layer spacing x" + AvatarConfig.depth() + " (1 = as designed).");
                        })))
                .then(Commands.literal("turn")
                        .then(Commands.argument("value", DoubleArgumentType.doubleArg(0.0, 1.0)).executes(ctx -> {
                            AvatarConfig.setTurn(DoubleArgumentType.getDouble(ctx, "value"));
                            return say(ctx, "She turns " + Math.round(AvatarConfig.turn() * 100) + "% of the way toward you.");
                        })))
                .then(Commands.literal("maxview")
                        .then(Commands.argument("degrees", DoubleArgumentType.doubleArg(0.0, 80.0)).executes(ctx -> {
                            AvatarConfig.setMaxView(DoubleArgumentType.getDouble(ctx, "degrees"));
                            return say(ctx, "Largest sideways view: " + AvatarConfig.maxView() + " degrees.");
                        })))
                .then(Commands.literal("debug")
                        .then(Commands.argument("on", BoolArgumentType.bool()).executes(ctx -> {
                            AvatarConfig.setDebugTint(BoolArgumentType.getBool(ctx, "on"));
                            return say(ctx, "Layer tint " + (AvatarConfig.debugTint() ? "on" : "off") + ".");
                        })))
                .then(Commands.literal("status").executes(ctx -> say(ctx, "Avatar: " + AvatarConfig.style()
                        + ", dragonParts=" + AvatarConfig.dragonParts()
                        + ", directional=" + AvatarConfig.directional()
                        + " (views " + StandeeLayers.get().views().keySet() + ")"
                        + ", shadow=" + AvatarConfig.castShadow()
                        + ", envShading=" + AvatarConfig.envShading()
                        + ", depth x" + AvatarConfig.depth() + ", turn " + AvatarConfig.turn() + ", max view " + AvatarConfig.maxView()
                        + ", hover " + AvatarConfig.hover() + ", hemFade " + AvatarConfig.hemFade()
                        + ", petals " + AvatarConfig.petals() + ", afterimages " + AvatarConfig.afterimages() + "."))));
    }

    private static int say(com.mojang.brigadier.context.CommandContext<net.minecraft.commands.CommandSourceStack> ctx, String text) {
        ctx.getSource().sendSuccess(() -> Component.literal(text), false);
        return 1;
    }
}
