package com.blighteddreams.helper.spike;

import com.blighteddreams.helper.BlightedHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.Set;
import java.util.TreeSet;
import java.util.WeakHashMap;
import java.util.function.Supplier;

/**
 * Client half of the spike: keeps a log of what the tab list looks like, and swaps the skin of Emily's PlayerInfo for the
 * skin the mod ships (no mixin: a reflective write of PlayerInfo.skinLookup, keyed by her UUID).
 */
@EventBusSubscriber(modid = BlightedHelper.MOD_ID, value = Dist.CLIENT)
public final class SpikeClient {
    private SpikeClient() {}

    private static final ResourceLocation SKIN = ResourceLocation.fromNamespaceAndPath(BlightedHelper.MOD_ID, "textures/entity/emily_skin.png");
    private static final Set<PlayerInfo> patched = Collections.newSetFromMap(new WeakHashMap<>());
    private static Field skinField;
    private static boolean fieldTried;
    private static String lastTab = "";
    private static int tick;

    @SubscribeEvent
    public static void onTick(ClientTickEvent.Post e) {
        if (!Spike.ENABLED) return;
        Minecraft mc = Minecraft.getInstance();
        ClientPacketListener conn = mc.getConnection();
        if (conn == null || mc.player == null) return;
        if (++tick % 10 != 0) return;
        if (tick % 200 == 0 && mc.level != null) {
            for (var en : mc.level.entitiesForRendering()) {
                if (en instanceof com.blighteddreams.helper.HelperEntity h && net.neoforged.fml.ModList.get().isLoaded("curios")) {
                    var inv = top.theillusivec4.curios.api.CuriosApi.getCuriosInventory(h);
                    StringBuilder sb = new StringBuilder();
                    inv.ifPresent(c -> c.getCurios().forEach((k, v) -> { for (int i = 0; i < v.getSlots(); i++) { var st = v.getStacks().getStackInSlot(i); if (!st.isEmpty()) sb.append(k).append("=").append(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(st.getItem())).append(" renders=").append(v.getRenders().get(i)).append("; "); } }));
                    Spike.LOG.info("[SPIKE][client] body B client-side curios: {} (layerAdded={})", inv.isPresent() ? sb : "none", layerAdded);
                }
            }
        }
        var emilyId = Spike.uuidFor(mc.player.getUUID());
        var ghostId = Spike.ghostUuidFor(mc.player.getUUID());
        Set<String> names = new TreeSet<>();
        for (PlayerInfo pi : conn.getOnlinePlayers()) {
            boolean emily = pi.getProfile().getId().equals(emilyId) || pi.getProfile().getId().equals(ghostId);
            PlayerSkin skin = pi.getSkin();
            names.add(pi.getProfile().getName() + "[" + (skin.texture().getPath().length() > 30 ? "..." + skin.texture().getPath().substring(skin.texture().getPath().length() - 28) : skin.texture().getPath()) + "," + skin.model().id() + ",ping=" + pi.getLatency() + ",mode=" + pi.getGameMode() + "]");
            if (emily && !patched.contains(pi)) {
                if (override(pi)) {
                    patched.add(pi);
                    Spike.LOG.info("[SPIKE][client] skin override applied to tab-list entry {} -> {}", pi.getProfile().getName(), SKIN);
                }
            }
        }
        String now = String.join(" | ", names);
        if (!now.equals(lastTab)) {
            lastTab = now;
            Spike.LOG.info("[SPIKE][client] tab list is now: {}", now);
            for (var p : mc.level.players()) {
                Spike.LOG.info("[SPIKE][client] level player entity {} class={} pos={}", p.getName().getString(), p.getClass().getSimpleName(), p.blockPosition());
            }
        }
    }

    private static boolean layerAdded;

    /** Draws worn Curios on body B: her skin renderer is not registered on its own, so Curios never attaches its layer to it. */
    @SubscribeEvent
    public static void onAddLayers(net.neoforged.neoforge.client.event.EntityRenderersEvent.AddLayers e) {
        if (!Spike.ENABLED || !net.neoforged.fml.ModList.get().isLoaded("curios")) return;
        try {
            var r = e.getRenderer(com.blighteddreams.helper.HelperEntities.HELPER.get());
            Field f = r.getClass().getDeclaredField("skinRenderer");
            f.setAccessible(true);
            Object skin = f.get(r);
            com.blighteddreams.helper.client.CuriosLayerAdder.add((net.minecraft.client.renderer.entity.LivingEntityRenderer<?, ?>) skin);
            layerAdded = true;
            Spike.LOG.info("[SPIKE][client] CuriosLayer added to {} (model {})", skin.getClass().getSimpleName(), ((net.minecraft.client.renderer.entity.LivingEntityRenderer<?, ?>) skin).getModel().getClass().getSimpleName());
        } catch (Throwable t) {
            Spike.LOG.warn("[SPIKE][client] could not add the Curios layer to body B: {}", t.toString());
        }
    }

    private static boolean override(PlayerInfo pi) {
        try {
            if (!fieldTried) {
                fieldTried = true;
                skinField = PlayerInfo.class.getDeclaredField("skinLookup");
                skinField.setAccessible(true);
            }
            if (skinField == null) return false;
            Supplier<PlayerSkin> mine = () -> new PlayerSkin(SKIN, null, null, null, PlayerSkin.Model.SLIM, true);
            skinField.set(pi, mine);
            return true;
        } catch (Throwable t) {
            Spike.LOG.warn("[SPIKE][client] skin override failed: {}", t.toString());
            skinField = null;
            return false;
        }
    }
}
