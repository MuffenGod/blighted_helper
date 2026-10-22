package com.blighteddreams.helper.spike;

import com.blighteddreams.helper.HelperEntity;
import com.blighteddreams.helper.HelperManager;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.ChatType;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.PlayerChatMessage;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.player.ChatVisiblity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.network.connection.ConnectionType;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Spawn / remove / describe for both candidate bodies. */
final class SpikeBodies {
    private SpikeBodies() {}

    /** owner uuid -> her fake player (body A) */
    static final Map<UUID, SpikePlayer> A = new HashMap<>();

    @Nullable
    static SpikePlayer a(ServerPlayer owner) {
        SpikePlayer p = A.get(owner.getUUID());
        if (p != null && p.isRemoved()) {
            A.remove(owner.getUUID());
            return null;
        }
        return p;
    }

    static SpikePlayer spawnA(ServerPlayer owner) {
        MinecraftServer server = owner.server;
        SpikePlayer old = a(owner);
        if (old != null) removeA(owner);
        ServerLevel level = owner.serverLevel();
        UUID id = Spike.uuidFor(owner.getUUID());
        GameProfile profile = new GameProfile(id, Spike.NAME);
        // viewDistance 2 (the minimum a client reports) keeps the chunks she keeps loaded small; 0x7F = every skin layer on
        ClientInformation info = new ClientInformation("en_us", 2, ChatVisiblity.FULL, true, 0x7F, HumanoidArm.RIGHT, false, true);
        FakeConnection.Made made = FakeConnection.create();
        SpikePlayer p = new SpikePlayer(server, level, profile, info, made);
        p.owner = owner.getUUID();
        long t0 = System.nanoTime();
        server.getPlayerList().placeNewPlayer(made.connection(), p, new CommonListenerCookie(profile, 0, info, false, ConnectionType.NEOFORGE));
        long ms = (System.nanoTime() - t0) / 1_000_000;
        // placeNewPlayer puts her where her saved data says (or world spawn); put her beside the owner
        double yaw = Math.toRadians(owner.getYRot() + 90.0);
        double x = owner.getX() - Math.sin(yaw) * 1.5;
        double z = owner.getZ() + Math.cos(yaw) * 1.5;
        p.teleportTo(level, x, owner.getY(), z, owner.getYRot(), 0.0F);
        A.put(owner.getUUID(), p);
        Spike.LOG.info("[SPIKE] body A placed through PlayerList in {} ms: {} uuid={} entityId={} at {} (players online: {})",
                ms, p.getGameProfile().getName(), p.getUUID(), p.getId(), p.blockPosition(), server.getPlayerList().getPlayerCount());
        return p;
    }

    static void removeA(ServerPlayer owner) {
        SpikePlayer p = A.remove(owner.getUUID());
        if (p == null) return;
        p.connection.disconnect(Component.literal("spike: removed"));
        Spike.LOG.info("[SPIKE] body A removed; players online: {}", owner.server.getPlayerList().getPlayerCount());
    }

    @Nullable
    static HelperEntity b(ServerPlayer owner) {
        return HelperManager.find(owner);
    }

    static HelperEntity spawnB(ServerPlayer owner) {
        HelperManager.summonOrRecall(owner);
        return HelperManager.find(owner);
    }

    static void chatAs(SpikePlayer p, String text) {
        PlayerChatMessage msg = PlayerChatMessage.unsigned(p.getUUID(), text);
        p.server.getPlayerList().broadcastChatMessage(msg, p, ChatType.bind(ChatType.CHAT, p));
    }

    // ---- body B presence emulation: a tab-list entry and chat lines with no player behind them -----------------------------

    static net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket tabPacket(GameProfile profile, int latency) throws ReflectiveOperationException {
        var actions = java.util.EnumSet.of(net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket.Action.ADD_PLAYER,
                net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket.Action.UPDATE_LISTED,
                net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket.Action.UPDATE_LATENCY,
                net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket.Action.UPDATE_GAME_MODE);
        // the packet has no public constructor taking raw entries (only live ServerPlayers), so build an empty one and set its entry list
        var pkt = new net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket(actions, java.util.List.<ServerPlayer>of());
        var entry = new net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket.Entry(profile.getId(), profile, true, latency, net.minecraft.world.level.GameType.SURVIVAL, null, null);
        var f = net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket.class.getDeclaredField("entries");
        f.setAccessible(true);
        f.set(pkt, java.util.List.of(entry));
        return pkt;
    }

    static final java.util.Set<UUID> GHOSTS = new java.util.HashSet<>();

    static void ghostJoin(ServerPlayer owner) throws ReflectiveOperationException {
        GameProfile gp = new GameProfile(Spike.ghostUuidFor(owner.getUUID()), Spike.NAME);
        owner.server.getPlayerList().broadcastAll(tabPacket(gp, 12));
        GHOSTS.add(gp.getId());
        owner.server.getPlayerList().broadcastSystemMessage(Component.translatable("multiplayer.player.joined", Component.literal(Spike.NAME)).withStyle(net.minecraft.ChatFormatting.YELLOW), false);
    }

    static void ghostLeave(ServerPlayer owner) {
        UUID id = Spike.ghostUuidFor(owner.getUUID());
        owner.server.getPlayerList().broadcastAll(new net.minecraft.network.protocol.game.ClientboundPlayerInfoRemovePacket(List.of(id)));
        GHOSTS.remove(id);
        owner.server.getPlayerList().broadcastSystemMessage(Component.translatable("multiplayer.player.left", Component.literal(Spike.NAME)).withStyle(net.minecraft.ChatFormatting.YELLOW), false);
    }

    /** Same text the server prints for a real chat message, so mods that read chat from the log and players see an ordinary line. */
    static void ghostChat(ServerPlayer owner, String text) {
        owner.server.getPlayerList().broadcastSystemMessage(Component.translatable("chat.type.text", Component.literal(Spike.NAME), Component.literal(text)), false);
    }

    // ---- description helpers --------------------------------------------------------------------------------------

    static List<String> describe(LivingEntity e) {
        List<String> out = new ArrayList<>();
        out.add(String.format("%s [%s] id=%d uuid=%s", e.getName().getString(), e.getType().builtInRegistryHolder().key().location(), e.getId(), e.getUUID()));
        out.add(String.format("pos %.2f %.2f %.2f in %s, health %.1f/%.1f, armor value %d", e.getX(), e.getY(), e.getZ(), e.level().dimension().location(), e.getHealth(), e.getMaxHealth(), e.getArmorValue()));
        if (e instanceof ServerPlayer sp) {
            out.add(String.format("ServerPlayer: gamemode %s, food %d, xp level %d, listed in PlayerList: %s, class %s", sp.gameMode.getGameModeForPlayer(), sp.getFoodData().getFoodLevel(), sp.experienceLevel,
                    sp.server.getPlayerList().getPlayer(sp.getUUID()) == sp, sp.getClass().getSimpleName()));
        }
        out.add("mainhand " + stack(e.getMainHandItem()) + " | offhand " + stack(e.getOffhandItem()));
        StringBuilder armor = new StringBuilder("armor");
        for (EquipmentSlot s : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            armor.append(' ').append(s.getName()).append('=').append(stack(e.getItemBySlot(s)));
        }
        out.add(armor.toString());
        if (e instanceof net.minecraft.world.entity.player.Player pl) {
            StringBuilder inv = new StringBuilder("inventory:");
            for (int i = 0; i < pl.getInventory().getContainerSize(); i++) {
                ItemStack st = pl.getInventory().getItem(i);
                if (!st.isEmpty()) inv.append(" [").append(i).append("] ").append(stack(st));
            }
            out.add(inv.toString());
        }
        StringBuilder fx = new StringBuilder("effects:");
        for (var ef : e.getActiveEffects()) fx.append(" ").append(ef.getEffect().getRegisteredName()).append("x").append(ef.getAmplifier() + 1).append("(").append(ef.getDuration()).append(")");
        out.add(fx.toString());
        // attributes: every registered attribute this entity has, with base, value and modifier count
        StringBuilder missing = new StringBuilder();
        int have = 0;
        for (Map.Entry<net.minecraft.resources.ResourceKey<Attribute>, Attribute> en : BuiltInRegistries.ATTRIBUTE.entrySet()) {
            var holder = BuiltInRegistries.ATTRIBUTE.getHolder(en.getKey()).orElse(null);
            if (holder == null) continue;
            AttributeInstance inst = e.getAttributes().hasAttribute(holder) ? e.getAttribute(holder) : null;
            if (inst == null) {
                missing.append(en.getKey().location()).append(' ');
                continue;
            }
            have++;
            if (inst.getModifiers().isEmpty() && inst.getValue() == inst.getBaseValue()) continue; // only interesting ones
            StringBuilder mods = new StringBuilder();
            for (AttributeModifier m : inst.getModifiers()) {
                mods.append(' ').append(m.id()).append(m.operation() == AttributeModifier.Operation.ADD_VALUE ? "+" : m.operation() == AttributeModifier.Operation.ADD_MULTIPLIED_BASE ? "*b" : "*t").append(String.format("%.3f", m.amount()));
            }
            out.add(String.format("attr %s base=%.4f value=%.4f mods:%s", en.getKey().location(), inst.getBaseValue(), inst.getValue(), mods));
        }
        out.add("attributes present on this entity: " + have + ", registered but ABSENT: " + (BuiltInRegistries.ATTRIBUTE.size() - have));
        out.add("absent list: " + missing);
        if (ModList.get().isLoaded("curios")) out.addAll(SpikeCurios.describe(e));
        return out;
    }

    static String stack(ItemStack s) {
        if (s.isEmpty()) return "-";
        StringBuilder comps = new StringBuilder();
        for (var en : s.getComponentsPatch().entrySet()) comps.append(en.getKey() == null ? "?" : BuiltInRegistries.DATA_COMPONENT_TYPE.getKey(en.getKey())).append(en.getValue().isEmpty() ? "(removed)" : "").append(",");
        return BuiltInRegistries.ITEM.getKey(s.getItem()) + (s.getCount() > 1 ? "x" + s.getCount() : "") + (comps.length() == 0 ? "" : "{" + comps + "}");
    }
}
