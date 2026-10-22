package com.blighteddreams.helper.spike;

import com.blighteddreams.helper.HelperEntity;
import com.blighteddreams.helper.util.ServerScheduler;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

/** Commands (/bdspike ...) and measurement hooks for the body spike. Registered only when Spike.ENABLED. */
public final class SpikeEvents {
    private SpikeEvents() {}

    // ---- measurement ----------------------------------------------------------------------------------------------
    private static final int RING = 1200;
    private static final long[] msptRing = new long[RING];
    private static int msptPos;
    private static long msptCount;
    private static long serverTickStart;

    private static final long[] bRing = new long[RING];
    private static int bPos;
    private static long bCount;
    private static long bStart;

    @SubscribeEvent
    public static void onServerTickPre(ServerTickEvent.Pre e) {
        serverTickStart = System.nanoTime();
    }

    @SubscribeEvent
    public static void onServerTickPost(ServerTickEvent.Post e) {
        long d = System.nanoTime() - serverTickStart;
        msptRing[msptPos] = d;
        msptPos = (msptPos + 1) % RING;
        msptCount++;
        MinecraftServer server = e.getServer();
        if (bcLoop && server.getTickCount() % 16 == 0 && ModList.get().isLoaded("bettercombat")) {
            for (SpikePlayer ap : SpikeBodies.A.values()) SpikeBetterCombat.swing(ap, bcCombo++);
        }
        if (server.getTickCount() % 1200 == 0 && (!SpikeBodies.A.isEmpty() || anyB(server))) {
            Spike.LOG.info("[SPIKE] minute report: {}", msptSummary());
            for (SpikePlayer p : SpikeBodies.A.values()) {
                Spike.LOG.info("[SPIKE] minute report A: {}; packets she was sent: {} (top {})", p.perfSummary(), p.net.sink().total.get(), top(p.net.sink().byType, 6));
            }
            if (bCount > 0) Spike.LOG.info("[SPIKE] minute report B: {}", bSummary());
        }
    }

    private static boolean anyB(MinecraftServer server) {
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (p instanceof SpikePlayer) continue;
            if (SpikeBodies.b(p) != null) return true;
        }
        return false;
    }

    @SubscribeEvent
    public static void onEntityTickPre(EntityTickEvent.Pre e) {
        if (e.getEntity() instanceof HelperEntity) bStart = System.nanoTime();
    }

    @SubscribeEvent
    public static void onEntityTickPost(EntityTickEvent.Post e) {
        if (e.getEntity() instanceof HelperEntity) {
            bRing[bPos] = System.nanoTime() - bStart;
            bPos = (bPos + 1) % RING;
            bCount++;
        }
    }

    private static String ringSummary(long[] ring, long count) {
        int n = (int) Math.min(count, RING);
        if (n == 0) return "no samples";
        long[] c = Arrays.copyOf(ring, n);
        Arrays.sort(c);
        long sum = 0;
        for (long v : c) sum += v;
        return String.format("n=%d avg %.1f us, median %.1f us, p99 %.1f us, max %.1f us", n, sum / (double) n / 1000.0, c[n / 2] / 1000.0, c[Math.min(n - 1, (int) (n * 0.99))] / 1000.0, c[n - 1] / 1000.0);
    }

    static String msptSummary() {
        return "whole server tick (ServerTickEvent Pre..Post): " + ringSummary(msptRing, msptCount).replace("us", "us").replace(" us", " us");
    }

    static String bSummary() {
        return "HelperEntity tick (EntityTickEvent Pre..Post): " + ringSummary(bRing, bCount);
    }

    private static String top(Map<String, java.util.concurrent.atomic.AtomicLong> m, int n) {
        return m.entrySet().stream().sorted((a, b) -> Long.compare(b.getValue().get(), a.getValue().get())).limit(n)
                .map(en -> en.getKey() + "=" + en.getValue().get()).collect(Collectors.joining(", "));
    }

    // ---- logging hooks --------------------------------------------------------------------------------------------

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent e) {
        if (e.getEntity() instanceof SpikePlayer sp) {
            Spike.LOG.info("[SPIKE] PlayerLoggedInEvent fired for the fake player {} (every login handler in the pack just ran for her)", sp.getGameProfile().getName());
            return;
        }
        if (!(e.getEntity() instanceof ServerPlayer owner)) return;
        // optional unattended spawn: game dir file spike_auto containing a, b or ab
        try {
            Path f = FMLPaths.GAMEDIR.get().resolve("spike_auto");
            if (Files.isRegularFile(f)) {
                String mode = Files.readString(f).trim();
                ServerScheduler.later(owner.server, 100, () -> {
                    if (mode.contains("a")) { SpikePlayer ap = SpikeBodies.spawnA(owner); ap.followOwner = mode.contains("f"); }
                    if (mode.contains("b")) SpikeBodies.spawnB(owner);
                    if (mode.contains("t")) { try { SpikeBodies.ghostJoin(owner); } catch (ReflectiveOperationException ex) { Spike.LOG.warn("ghost tab failed", ex); } }
                });
            }
        } catch (Exception ex) {
            Spike.LOG.warn("[SPIKE] auto spawn check failed", ex);
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent e) {
        if (e.getEntity() instanceof SpikePlayer sp) {
            Spike.LOG.info("[SPIKE] PlayerLoggedOutEvent for fake player {}", sp.getGameProfile().getName());
        }
    }

    @SubscribeEvent
    public static void onDamage(LivingDamageEvent.Post e) {
        var src = e.getSource().getEntity();
        boolean srcSpike = src instanceof SpikePlayer || src instanceof HelperEntity;
        boolean tgtSpike = e.getEntity() instanceof SpikePlayer || e.getEntity() instanceof HelperEntity;
        if (srcSpike || tgtSpike) {
            Spike.LOG.info("[SPIKE] damage: {} -> {} for {} (type {}), target health now {}", src == null ? "?" : src.getName().getString(), e.getEntity().getName().getString(),
                    String.format("%.2f", e.getNewDamage()), e.getSource().type().msgId(), String.format("%.1f", e.getEntity().getHealth()));
        }
    }

    private static int bcCombo;
    private static boolean bcLoop;

    // ---- commands -------------------------------------------------------------------------------------------------

    @SubscribeEvent
    public static void onCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> d = event.getDispatcher();
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("bdspike").requires(s -> s.hasPermission(2));

        LiteralArgumentBuilder<CommandSourceStack> a = Commands.literal("a");
        a.then(Commands.literal("spawn").executes(c -> {
            SpikePlayer p = SpikeBodies.spawnA(owner(c));
            return say(c, "body A placed: " + p.getName().getString() + " at " + p.blockPosition().toShortString());
        }));
        a.then(Commands.literal("remove").executes(c -> {
            SpikeBodies.removeA(owner(c));
            return say(c, "body A removed");
        }));
        a.then(Commands.literal("stop").executes(c -> {
            SpikePlayer p = needA(c);
            p.walkTo = null;
            p.followOwner = false;
            return say(c, "stopped");
        }));
        a.then(Commands.literal("follow").executes(c -> {
            SpikePlayer p = needA(c);
            p.followOwner = true;
            p.sprint = false;
            return say(c, "following owner");
        }).then(Commands.literal("sprint").executes(c -> {
            SpikePlayer p = needA(c);
            p.followOwner = true;
            p.sprint = true;
            return say(c, "following owner, sprinting");
        })));
        a.then(Commands.literal("lookatme").executes(c -> {
            SpikePlayer p = needA(c);
            p.lookAtOwner = true;
            return say(c, "will look at the owner when idle");
        }));
        a.then(Commands.literal("walk").then(Commands.argument("x", DoubleArgumentType.doubleArg()).then(Commands.argument("z", DoubleArgumentType.doubleArg())
                .executes(c -> walk(c, false))
                .then(Commands.argument("sprint", BoolArgumentType.bool()).executes(c -> walk(c, BoolArgumentType.getBool(c, "sprint")))))));
        a.then(Commands.literal("swing").executes(c -> {
            SpikePlayer p = needA(c);
            p.swing(InteractionHand.MAIN_HAND, true);
            return say(c, "swung main hand (swinging=" + p.swinging + ")");
        }));
        a.then(Commands.literal("bcswing").executes(c -> {
            SpikePlayer p = needA(c);
            if (!ModList.get().isLoaded("bettercombat")) return say(c, "bettercombat not loaded");
            return say(c, SpikeBetterCombat.swing(p, bcCombo++));
        }));
        a.then(Commands.literal("bcloop").then(Commands.argument("on", BoolArgumentType.bool()).executes(c -> {
            bcLoop = BoolArgumentType.getBool(c, "on");
            return say(c, "Better Combat swing loop " + (bcLoop ? "on" : "off"));
        })));
        a.then(Commands.literal("attack").executes(c -> {
            SpikePlayer p = needA(c);
            AABB box = p.getBoundingBox().inflate(3.5);
            LivingEntity best = null;
            for (LivingEntity e : p.level().getEntitiesOfClass(LivingEntity.class, box, e -> e != p && e.isAlive() && !(e instanceof ServerPlayer))) {
                if (best == null || e.distanceToSqr(p) < best.distanceToSqr(p)) best = e;
            }
            if (best == null) return say(c, "no living target within 3.5 blocks");
            p.faceTowards(best.getEyePosition(), 180f);
            float scale = p.getAttackStrengthScale(0.5F);
            float before = best.getHealth();
            p.attack(best);
            p.swing(InteractionHand.MAIN_HAND, true);
            return say(c, String.format("attacked %s: attack strength was %.2f, target health %.1f -> %.1f (see [SPIKE] damage log)", best.getName().getString(), scale, before, best.getHealth()));
        }));
        a.then(Commands.literal("break").then(Commands.argument("x", com.mojang.brigadier.arguments.IntegerArgumentType.integer()).then(Commands.argument("y", com.mojang.brigadier.arguments.IntegerArgumentType.integer())
                .then(Commands.argument("z", com.mojang.brigadier.arguments.IntegerArgumentType.integer()).executes(c -> {
                    SpikePlayer p = needA(c);
                    net.minecraft.core.BlockPos pos = new net.minecraft.core.BlockPos(com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(c, "x"), com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(c, "y"), com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(c, "z"));
                    var before = p.level().getBlockState(pos);
                    boolean ok = p.gameMode.destroyBlock(pos);
                    return say(c, "destroyBlock(" + pos.toShortString() + ") " + before.getBlock().getDescriptionId() + " -> returned " + ok + ", block now " + p.level().getBlockState(pos).getBlock().getDescriptionId());
                })))));
        a.then(Commands.literal("chat").then(Commands.argument("text", StringArgumentType.greedyString()).executes(c -> {
            SpikeBodies.chatAs(needA(c), StringArgumentType.getString(c, "text"));
            return 1;
        })));
        a.then(Commands.literal("info").executes(c -> dump(c, needA(c))));
        a.then(Commands.literal("perf").executes(c -> {
            SpikePlayer p = needA(c);
            say(c, p.perfSummary());
            say(c, msptSummary());
            say(c, "packets the server tried to send her: " + p.net.sink().total.get() + " (top: " + top(p.net.sink().byType, 8) + ")");
            Spike.LOG.info("[SPIKE] perf A: {} | {} | packets {} {}", p.perfSummary(), msptSummary(), p.net.sink().total.get(), top(p.net.sink().byType, 12));
            return 1;
        }));
        a.then(Commands.literal("resetperf").executes(c -> {
            needA(c).resetPerf();
            msptCount = 0;
            msptPos = 0;
            return say(c, "perf counters reset");
        }));
        root.then(a);

        LiteralArgumentBuilder<CommandSourceStack> b = Commands.literal("b");
        b.then(Commands.literal("spawn").executes(c -> {
            HelperEntity h = SpikeBodies.spawnB(owner(c));
            return say(c, "body B (HelperEntity) ready: " + (h == null ? "FAILED" : h.blockPosition().toShortString()));
        }));
        b.then(Commands.literal("info").executes(c -> {
            HelperEntity h = SpikeBodies.b(owner(c));
            if (h == null) return say(c, "no body B");
            return dump(c, h);
        }));
        b.then(Commands.literal("perf").executes(c -> {
            say(c, bSummary());
            Spike.LOG.info("[SPIKE] perf B: {}", bSummary());
            return 1;
        }));
        b.then(Commands.literal("resetperf").executes(c -> {
            bCount = 0;
            bPos = 0;
            return say(c, "B perf counters reset");
        }));
        b.then(Commands.literal("give").then(Commands.argument("item", StringArgumentType.string()).executes(c -> {
            HelperEntity h = SpikeBodies.b(owner(c));
            if (h == null) return say(c, "no body B");
            var rl = net.minecraft.resources.ResourceLocation.tryParse(StringArgumentType.getString(c, "item"));
            if (rl == null || !net.minecraft.core.registries.BuiltInRegistries.ITEM.containsKey(rl)) return say(c, "unknown item");
            var inv = h.getData(SpikeAttachments.INVENTORY.get());
            var left = net.neoforged.neoforge.items.ItemHandlerHelper.insertItem(inv, new net.minecraft.world.item.ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(rl), 5), false);
            return say(c, "gave 5x " + rl + " to her inventory attachment, leftover " + left.getCount());
        })));
        b.then(Commands.literal("inv").executes(c -> {
            HelperEntity h = SpikeBodies.b(owner(c));
            if (h == null) return say(c, "no body B");
            var inv = h.getData(SpikeAttachments.INVENTORY.get());
            StringBuilder sb = new StringBuilder("her inventory attachment (" + inv.getSlots() + " slots):");
            for (int i = 0; i < inv.getSlots(); i++) if (!inv.getStackInSlot(i).isEmpty()) sb.append(" [").append(i).append("] ").append(SpikeBodies.stack(inv.getStackInSlot(i)));
            return say(c, sb.toString());
        }));
        b.then(Commands.literal("tab").then(Commands.literal("on").executes(c -> {
            try {
                SpikeBodies.ghostJoin(owner(c));
            } catch (ReflectiveOperationException e) {
                return say(c, "ghost tab failed: " + e);
            }
            return say(c, "ghost tab entry sent (no entity or player behind it)");
        })).then(Commands.literal("off").executes(c -> {
            SpikeBodies.ghostLeave(owner(c));
            return say(c, "ghost tab entry removed");
        })));
        b.then(Commands.literal("chat").then(Commands.argument("text", StringArgumentType.greedyString()).executes(c -> {
            SpikeBodies.ghostChat(owner(c), StringArgumentType.getString(c, "text"));
            return 1;
        })));
        root.then(b);

        root.then(Commands.literal("near").executes(c -> near(c, 24)).then(Commands.argument("r", DoubleArgumentType.doubleArg(1, 128)).executes(c -> near(c, DoubleArgumentType.getDouble(c, "r")))));
        root.then(Commands.literal("resetperf").executes(c -> {
            msptCount = 0;
            msptPos = 0;
            bCount = 0;
            bPos = 0;
            SpikeBodies.A.values().forEach(SpikePlayer::resetPerf);
            return say(c, "all perf counters reset");
        }));

        root.then(Commands.literal("curio").then(Commands.argument("who", StringArgumentType.word()).then(Commands.argument("slot", StringArgumentType.word())
                .then(Commands.argument("item", StringArgumentType.string()).executes(c -> {
                    if (!ModList.get().isLoaded("curios")) return say(c, "curios not loaded");
                    String who = StringArgumentType.getString(c, "who");
                    LivingEntity target = who.equalsIgnoreCase("a") ? SpikeBodies.a(owner(c)) : who.equalsIgnoreCase("o") ? owner(c) : SpikeBodies.b(owner(c));
                    if (target == null) return say(c, "no body " + who);
                    String err = SpikeCurios.equip(target, StringArgumentType.getString(c, "slot"), StringArgumentType.getString(c, "item"));
                    return say(c, err == null ? "equipped" : "FAILED: " + err);
                })))));
        root.then(Commands.literal("o").then(Commands.literal("info").executes(c -> dump(c, owner(c)))));
        root.then(Commands.literal("mspt").executes(c -> say(c, msptSummary())));
        root.then(Commands.literal("report").executes(c -> {
            ServerPlayer o = owner(c);
            MinecraftServer server = o.server;
            Spike.LOG.info("[SPIKE] ===== report ===== server players: {}", server.getPlayerList().getPlayers().stream().map(p -> p.getGameProfile().getName() + (p instanceof SpikePlayer ? "(fake)" : "")).collect(Collectors.joining(", ")));
            Spike.LOG.info("[SPIKE] {}", msptSummary());
            SpikePlayer p = SpikeBodies.a(o);
            if (p != null) {
                for (String s : SpikeBodies.describe(p)) Spike.LOG.info("[SPIKE][A] {}", s);
                Spike.LOG.info("[SPIKE][A] {}", p.perfSummary());
                Spike.LOG.info("[SPIKE][A] packets: total {}, by type: {}", p.net.sink().total.get(), top(p.net.sink().byType, 40));
                if (ModList.get().isLoaded("ftbteams")) Spike.LOG.info("[SPIKE][A] FTB team: {}", Ftb.teamOf(p));
            }
            HelperEntity h = SpikeBodies.b(o);
            if (h != null) {
                for (String s : SpikeBodies.describe(h)) Spike.LOG.info("[SPIKE][B] {}", s);
                Spike.LOG.info("[SPIKE][B] {}", bSummary());
            }
            if (ModList.get().isLoaded("ftbteams")) Spike.LOG.info("[SPIKE][owner] FTB team: {}", Ftb.teamOf(o));
            return say(c, "report written to the log");
        }));
        d.register(root);
    }

    private static final class Ftb {
        static String teamOf(ServerPlayer p) {
            try {
                Class<?> apiClass = Class.forName("dev.ftb.mods.ftbteams.api.FTBTeamsAPI");
                Object api = apiClass.getMethod("api").invoke(null);
                boolean loaded = (boolean) api.getClass().getMethod("isManagerLoaded").invoke(api);
                if (!loaded) return "manager not loaded";
                Object mgr = api.getClass().getMethod("getManager").invoke(api);
                Object t = mgr.getClass().getMethod("getTeamForPlayer", net.minecraft.world.entity.player.Player.class).invoke(mgr, p);
                return String.valueOf(t);
            } catch (Throwable t) {
                return "error " + t;
            }
        }
    }

    // ---- helpers --------------------------------------------------------------------------------------------------

    private static ServerPlayer owner(CommandContext<CommandSourceStack> c) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        if (p instanceof SpikePlayer sp && sp.owner != null) {
            ServerPlayer o = p.server.getPlayerList().getPlayer(sp.owner);
            if (o != null) return o;
        }
        return p;
    }

    private static SpikePlayer needA(CommandContext<CommandSourceStack> c) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        SpikePlayer p = SpikeBodies.a(owner(c));
        if (p == null) throw new com.mojang.brigadier.exceptions.SimpleCommandExceptionType(Component.literal("spawn body A first: /bdspike a spawn")).create();
        return p;
    }

    private static int walk(CommandContext<CommandSourceStack> c, boolean sprint) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        SpikePlayer p = needA(c);
        p.followOwner = false;
        p.walkTo = new net.minecraft.world.phys.Vec3(DoubleArgumentType.getDouble(c, "x"), p.getY(), DoubleArgumentType.getDouble(c, "z"));
        p.sprint = sprint;
        return say(c, "walking to " + p.walkTo.x + "," + p.walkTo.z + (sprint ? " (sprint)" : ""));
    }

    private static int dump(CommandContext<CommandSourceStack> c, LivingEntity e) {
        for (String line : SpikeBodies.describe(e)) {
            Spike.LOG.info("[SPIKE][info] {}", line);
        }
        // chat gets the short lines only; the full dump is in the log
        var lines = SpikeBodies.describe(e);
        for (String line : lines) {
            if (line.startsWith("absent list")) continue;
            say(c, line.length() > 220 ? line.substring(0, 220) + "..." : line);
        }
        return 1;
    }

    private static int near(CommandContext<CommandSourceStack> c, double r) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer o = owner(c);
        var counts = new java.util.TreeMap<String, Integer>();
        for (var e : o.level().getEntities(o, o.getBoundingBox().inflate(r))) {
            counts.merge(net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).toString(), 1, Integer::sum);
        }
        return say(c, "entities within " + r + ": " + counts);
    }

    private static int say(CommandContext<CommandSourceStack> c, String s) {
        Spike.LOG.info("[SPIKE] {}", s);
        c.getSource().sendSuccess(() -> Component.literal("[spike] " + s), false);
        return 1;
    }
}
