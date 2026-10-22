package com.blighteddreams.helper.agent;

import com.blighteddreams.helper.HelperEntity;
import com.blighteddreams.helper.network.HelperPayloads;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.PacketDistributor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Emily's body. The model is her voice and her imagination; this class is the part that actually walks, mines, opens
 * chests, builds portals and remembers where home is. It runs entirely on the server thread and can be driven by the
 * player, by chat, or by {@link EmilyPlanner} when nobody has given her an order.
 */
public final class EmilyAgent {
    private static final Logger LOGGER = LoggerFactory.getLogger("BlightedHelper/Agent");

    public enum Autonomy {
        OFF(0),
        COMPANION(16),
        PARTNER(64),
        FREE(256);

        private final int radius;

        Autonomy(int radius) {
            this.radius = radius;
        }

        public int radius() {
            return radius;
        }

        public static Autonomy from(String value) {
            if (value == null || value.isBlank()) return PARTNER;
            return switch (value.trim().toLowerCase(Locale.ROOT)) {
                case "off", "none", "manual" -> OFF;
                case "companion", "close", "near" -> COMPANION;
                case "free", "wild", "independent" -> FREE;
                default -> PARTNER;
            };
        }
    }

    private final HelperEntity helper;
    private EmilyTask task;
    private Autonomy autonomy = Autonomy.PARTNER;
    private int taskTicks;
    private int decideCooldown = 100;
    private int teleportCooldown;
    private int speakCooldown;
    private int equipCooldown = 100;
    private String lastTaskName = "";
    private boolean memoryStarted;

    public EmilyAgent(HelperEntity helper) {
        this.helper = helper;
    }

    // ------------------------------------------------------------------------------------------------ accessors

    public HelperEntity helper() {
        return helper;
    }

    @Nullable
    public EmilyTask task() {
        return task;
    }

    public Autonomy autonomy() {
        return autonomy;
    }

    public void setAutonomy(Autonomy next) {
        if (next == null) next = Autonomy.PARTNER;
        this.autonomy = next;
        decideCooldown = 40;
        if (ownerId() != null) memory().setProfile(ownerId(), "autonomy", next.name().toLowerCase(Locale.ROOT));
    }

    public boolean isBusy() {
        return task != null;
    }

    public boolean isRoaming() {
        if (task == null) return false;
        // Any active job owns her position: mining, storing, smelting, guarding. Only the explicit return trip is
        // allowed to be interrupted by the normal owner-recall maintenance.
        if (task.name().equals("return")) return false;
        ServerPlayer owner = helper.getOwner();
        if (owner == null) return true;
        if (owner.getHealth() < owner.getMaxHealth() * 0.4F && helper.distanceToSqr(owner) > 64.0 * 64.0) return false;
        return true;
    }

    @Nullable
    public UUID ownerId() {
        return helper.getOwnerId();
    }

    public EmilyMemory memory() {
        if (helper.level() instanceof ServerLevel level) return EmilyMemory.get(level);
        throw new IllegalStateException("Emily's memory is server-side only");
    }

    // ------------------------------------------------------------------------------------------------ tick

    public void tick() {
        if (helper.level().isClientSide || helper.isRemoved()) return;
        if (speakCooldown > 0) speakCooldown--;
        if (teleportCooldown > 0) teleportCooldown--;

        if (helper.tickCount % 20 == 0) {
            if (!memoryStarted && initializeMemory()) {
                memoryStarted = true;
            }
            if (--equipCooldown <= 0) {
                equipCooldown = 180;
                if (task == null || task.name().equals("guard") || task.name().equals("patrol")
                        || task.name().equals("return") || task.name().equals("travel")) {
                    EmilyGear.autoEquip(helper);
                }
            }
            if (helper.getHealth() < helper.getMaxHealth() * 0.55F && helper.tickCount % 100 == 0) {
                EmilyGear.drinkPotion(helper, helper.getTarget() != null);
            }
        }

        if (helper.isDowned()) {
            abortTask(true);
            return;
        }

        if (task != null && helper.getTarget() != null && helper.getTarget().isAlive()) {
            helper.setTaskLabel(task.label() + " (fighting)");
            return; // combat goals own movement until the target is gone
        }

        if (task != null) {
            // If the owner is badly hurt and Emily is far away, drop the job and come home.
            ServerPlayer owner = helper.getOwner();
            if (task.roaming() && owner != null && owner.getHealth() < owner.getMaxHealth() * 0.45F
                    && helper.distanceToSqr(owner) > 32.0 * 32.0) {
                say("You're hurt. I'm coming back!", "worried");
                startTask(new EmilyTasks.ReturnTask(owner));
                return;
            }
            taskTicks++;
            EmilyTask.Status status;
            try {
                status = task.tick(helper, this);
            } catch (Throwable t) {
                LOGGER.warn("Emily task {} failed", task.name(), t);
                status = EmilyTask.Status.FAILED;
            }
            if (status != EmilyTask.Status.RUNNING) {
                finishTask(status);
            } else if (helper.tickCount % 10 == 0) {
                helper.setTaskLabel(task.label());
            }
            return;
        }

        helper.setTaskLabel(autonomy == Autonomy.OFF ? "resting" : "watching over you");
        if (autonomy == Autonomy.OFF) return;
        if (--decideCooldown > 0) return;
        decideCooldown = 100 + helper.getRandom().nextInt(180);
        EmilyTask auto = EmilyPlanner.choose(helper, this);
        if (auto != null) startTask(auto);
    }

    private boolean initializeMemory() {
        ServerPlayer owner = helper.getOwner();
        if (owner == null || ownerId() == null) return false;
        try {
            EmilyMemory mem = memory();
            if (mem.find(ownerId(), "home") == null && owner.getRespawnPosition() != null) {
                mem.remember(ownerId(), "home", owner.getRespawnDimension(), owner.getRespawnPosition(), "home");
            }
            if (mem.find(ownerId(), "base") == null && helper.distanceToSqr(owner) < 400.0) {
                mem.remember(ownerId(), "base", helper.level().dimension(), helper.blockPosition(), "base");
            }
            mem.setProfile(ownerId(), "last_dimension", helper.level().dimension().location().toString());
            mem.setProfile(ownerId(), "autonomy", autonomy.name().toLowerCase(Locale.ROOT));
            return true;
        } catch (Throwable t) {
            LOGGER.debug("Could not initialize Emily memory", t);
            return false;
        }
    }

    // ------------------------------------------------------------------------------------------------ tasks

    public void startTask(EmilyTask next) {
        if (next == null) return;
        if (task != null) {
            try {
                task.stop(helper, this);
            } catch (Throwable ignored) {
            }
        }
        task = next;
        taskTicks = 0;
        lastTaskName = next.name();
        helper.setTaskLabel(next.label());
        try {
            next.start(helper, this);
        } catch (Throwable t) {
            LOGGER.warn("Emily task {} could not start", next.name(), t);
            task = null;
            helper.setTaskLabel("idle");
            say("I couldn't get started on that.", "worried");
        }
    }

    private void finishTask(EmilyTask.Status status) {
        EmilyTask finished = task;
        if (finished == null) return;
        task = null;
        taskTicks = 0;
        try {
            finished.stop(helper, this);
        } catch (Throwable ignored) {
        }
        helper.setTaskLabel("idle");
        if (ownerId() != null) {
            try {
                if (status == EmilyTask.Status.DONE) {
                    memory().note(ownerId(), "Finished " + finished.name() + ".");
                } else {
                    memory().note(ownerId(), "Could not finish " + finished.name() + ".");
                }
            } catch (Throwable ignored) {
            }
        }
        if (status == EmilyTask.Status.FAILED) {
            say("I couldn't finish " + finished.name() + ". I'll try another way later.", "worried");
        } else if (!finished.quietOnDone()) {
            say(doneLine(finished), "cheerful");
        }
        decideCooldown = 120 + helper.getRandom().nextInt(200);
        if (helper.getHealth() < helper.getMaxHealth() * 0.65F) EmilyGear.eat(helper);
    }

    private static String doneLine(EmilyTask task) {
        return switch (task.name()) {
            case "mine" -> "Mining run finished. Check the bag.";
            case "gather" -> "Gathering done.";
            case "travel" -> "I'm here.";
            case "return" -> "I'm back.";
            case "structure" -> "We made it. This is the place.";
            case "loot" -> "Loot secured.";
            case "deposit" -> "Everything is stored.";
            case "craft" -> "Crafting done.";
            case "smelt" -> "Smelting done.";
            case "portal" -> "The portal is ready.";
            case "guard" -> "All clear.";
            case "patrol" -> "Patrol finished.";
            case "explore" -> "Scouting run complete.";
            default -> "Done.";
        };
    }

    public void abortTask(boolean silent) {
        if (task != null) {
            try {
                task.stop(helper, this);
            } catch (Throwable ignored) {
            }
            if (!silent) say("Stopping what I was doing.", "calm");
        }
        task = null;
        taskTicks = 0;
        helper.setTaskLabel("idle");
        decideCooldown = 100;
    }

    // ------------------------------------------------------------------------------------------------ orders

    public void assign(EmilyDirective directive) {
        if (directive == null || directive.verb().isBlank()) return;
        String verb = directive.verb();
        String target = directive.target();
        int amount = directive.amount();

        switch (verb) {
            case "stop", "halt", "cancel" -> {
                abortTask(true);
                helper.stopAttacking();
                say("Stopping.", "calm");
            }
            case "follow" -> {
                abortTask(true);
                helper.setStance(HelperEntity.Stance.FOLLOW);
                say("Right behind you.", "cheerful");
            }
            case "stay", "wait", "hold" -> {
                abortTask(true);
                helper.setStance(HelperEntity.Stance.STAY);
                say("I'll hold this spot.", "calm");
            }
            case "aggressive" -> {
                abortTask(true);
                helper.setStance(HelperEntity.Stance.AGGRESSIVE);
                say("Hunting mode. I'll take the fight to them.", "battle");
            }
            case "melee", "sword", "weapon" -> {
                helper.setCombatTactic(HelperEntity.CombatTactic.MELEE);
                say("Focusing on melee combat! I'll close the distance and strike with my weapon.", "battle");
            }
            case "magic", "spells", "caster" -> {
                helper.setCombatTactic(HelperEntity.CombatTactic.MAGIC);
                say("Switching to spellcaster focus. I'll maintain distance and sling spells.", "mystic");
            }
            case "hybrid", "battlemage" -> {
                helper.setCombatTactic(HelperEntity.CombatTactic.HYBRID);
                say("Hybrid battlemage mode. Spells at range, steel up close!", "battle");
            }
            case "tactic", "focus" -> {
                String sub = target.toLowerCase(java.util.Locale.ROOT);
                if (sub.contains("melee") || sub.contains("sword") || sub.contains("spear")) {
                    helper.setCombatTactic(HelperEntity.CombatTactic.MELEE);
                    say("Focusing on melee combat!", "battle");
                } else if (sub.contains("magic") || sub.contains("spell")) {
                    helper.setCombatTactic(HelperEntity.CombatTactic.MAGIC);
                    say("Focusing on spellcasting!", "mystic");
                } else if (sub.contains("hybrid")) {
                    helper.setCombatTactic(HelperEntity.CombatTactic.HYBRID);
                    say("Switched to hybrid tactics!", "battle");
                } else {
                    helper.setCombatTactic(HelperEntity.CombatTactic.AUTO);
                    say("Tactics set to auto-adapt to my weapon.", "cheerful");
                }
            }
            case "come", "return" -> {
                ServerPlayer owner = helper.getOwner();
                if (owner != null) startTask(new EmilyTasks.ReturnTask(owner));
                else say("I can't find you right now.", "worried");
            }
            case "mine" -> {
                if (target.equalsIgnoreCase("wood") || target.equalsIgnoreCase("logs") || target.equalsIgnoreCase("tree")) {
                    startTask(new EmilyGatherTask("logs", amount <= 0 ? 32 : amount));
                } else {
                    startTask(new EmilyMineTask(target.isBlank() ? "any ore" : target, amount));
                }
            }
            case "chop", "wood" -> startTask(new EmilyGatherTask("logs", amount <= 0 ? 32 : amount));
            case "gather", "harvest", "collect" -> startTask(new EmilyGatherTask(target.isBlank() ? "mature crops" : target, amount));
            case "loot", "search" -> startTask(new EmilyTasks.LootTask(amount <= 0 ? 24 : amount, false));
            case "deposit", "store", "unload" -> startTask(new EmilyTasks.DepositTask(amount <= 0 ? 32 : amount));
            case "explore", "scout" -> startTask(new EmilyTasks.ExploreTask(directive.extra(), amount));
            case "find", "locate" -> {
                if (target.isBlank()) {
                    say("What should I look for?", "worried");
                } else {
                    startTask(new EmilyTasks.StructureTask(target));
                }
            }
            case "portal", "nether_portal" -> startTask(new EmilyTasks.PortalTask());
            case "craft", "make" -> {
                if (target.isBlank()) say("What should I make?", "worried");
                else startTask(new EmilyCrafting.CraftTask(target, amount <= 0 ? 1 : amount));
            }
            case "smelt", "cook" -> {
                if (target.isBlank()) say("What should I smelt?", "worried");
                else startTask(new EmilyCrafting.SmeltTask(target, amount <= 0 ? 8 : amount));
            }
            case "spell", "cast" -> startTask(new EmilySpells.SpellTask(target));
            case "guard", "protect" -> startTask(new EmilyTasks.GuardTask(amount <= 0 ? 1200 : amount * 20));
            case "patrol" -> startTask(new EmilyTasks.PatrolTask(amount <= 0 ? 1200 : amount * 20,
                    parseRadius(directive.extra())));
            case "goto", "travel", "walk", "go" -> {
                Place place = resolvePlace(directive);
                if (place == null) say("I don't know where that is. Remember it first, or give me coordinates.", "worried");
                else startTask(new EmilyTasks.TravelTask(place.pos(), place.dimension(), "going to " + place.name(), true));
            }
            case "teleport", "tp", "warp" -> {
                Place place = resolvePlace(directive);
                if (place == null) {
                    say("I don't know that place yet.", "worried");
                } else if (forceTeleport(place)) {
                    say("Travelling to " + place.name() + ".", "calm");
                }
            }
            case "remember", "save" -> {
                String name = target.isBlank() ? (directive.extra().isBlank() ? "here" : directive.extra()) : target;
                String kind = name.equalsIgnoreCase("home") || name.equalsIgnoreCase("base") ? name.toLowerCase(Locale.ROOT) : "place";
                if (ownerId() != null) {
                    EmilyMemory.Waypoint w = memory().remember(ownerId(), name, helper.level().dimension(),
                            helper.blockPosition(), kind);
                    say("Remembered " + w.name() + " at " + w.x() + " " + w.y() + " " + w.z() + ".", "cheerful");
                }
            }
            case "home" -> {
                Place place = resolveHome();
                if (place == null) say("I don't know where home is yet. Sleep in a bed or tell me to remember this place as home.", "worried");
                else startTask(new EmilyTasks.TravelTask(place.pos(), place.dimension(), "heading home", true));
            }
            case "equip", "gear" -> {
                EmilyGear.autoEquip(helper);
                say("Checking my gear. I'll use the best of what we have.", "calm");
            }
            case "eat", "food" -> {
                if (EmilyGear.eat(helper)) say("That hit the spot.", "cheerful");
                else say("I don't have any food.", "worried");
            }
            case "status", "report" -> say(statusText(), "calm");
            case "autonomy", "mode" -> {
                Autonomy next = Autonomy.from(target);
                setAutonomy(next);
                say("Autonomy set to " + next.name().toLowerCase(Locale.ROOT) + ".", "calm");
            }
            case "idle", "rest" -> abortTask(false);
            default -> say("I don't know how to '" + verb + "' yet.", "worried");
        }
    }

    private static int parseRadius(String extra) {
        if (extra == null || extra.isBlank()) return 16;
        try {
            return Math.max(8, Math.min(64, Integer.parseInt(extra.trim().split("\\s+")[0])));
        } catch (NumberFormatException e) {
            return 16;
        }
    }

    public String statusText() {
        StringBuilder sb = new StringBuilder();
        if (task != null) sb.append("I'm ").append(task.label());
        else sb.append("I'm ").append(autonomy == Autonomy.OFF ? "resting" : "staying close");
        sb.append(". Autonomy: ").append(autonomy.name().toLowerCase(Locale.ROOT)).append('.');
        ServerPlayer owner = helper.getOwner();
        if (owner != null) {
            sb.append(" You're ").append(Math.round(Math.sqrt(helper.distanceToSqr(owner)))).append(" blocks away.");
        }
        if (ownerId() != null) {
            try {
                sb.append(" I remember ").append(memory().all(ownerId()).size()).append(" places.");
            } catch (Throwable ignored) {
            }
        }
        return sb.toString();
    }

    // ------------------------------------------------------------------------------------------------ places

    public record Place(BlockPos pos, @Nullable ResourceKey<Level> dimension, String name) {
    }

    @Nullable
    private Place resolvePlace(EmilyDirective directive) {
        int[] coords = directive.coordinates();
        if (coords == null) coords = parseCoords(directive.target());
        if (coords != null) {
            return new Place(new BlockPos(coords[0], coords[1], coords[2]), helper.level().dimension(),
                    coords[0] + " " + coords[1] + " " + coords[2]);
        }
        String query = directive.target().isBlank() ? directive.extra() : directive.target();
        if (query == null || query.isBlank()) return null;
        String lower = query.toLowerCase(Locale.ROOT);
        if (lower.equals("me") || lower.equals("owner") || lower.equals("you")) {
            ServerPlayer owner = helper.getOwner();
            if (owner == null) return null;
            return new Place(owner.blockPosition(), owner.level().dimension(), "you");
        }
        if (lower.equals("home") || lower.equals("base")) {
            Place home = resolveHome(lower);
            if (home != null) return home;
        }
        if (ownerId() == null) return null;
        EmilyMemory.Waypoint w = memory().find(ownerId(), query);
        if (w == null) return null;
        return new Place(w.pos(), dimensionKey(w.dimension()), w.name());
    }

    @Nullable
    private Place resolveHome() {
        return resolveHome("home");
    }

    @Nullable
    private Place resolveHome(String preferred) {
        if (ownerId() != null) {
            EmilyMemory mem = memory();
            EmilyMemory.Waypoint w = mem.find(ownerId(), preferred);
            if (w == null && !preferred.equals("base")) w = mem.find(ownerId(), "base");
            if (w != null) return new Place(w.pos(), dimensionKey(w.dimension()), w.name());
        }
        ServerPlayer owner = helper.getOwner();
        if (owner != null && owner.getRespawnPosition() != null) {
            return new Place(owner.getRespawnPosition(), owner.getRespawnDimension(), "home");
        }
        return null;
    }

    @Nullable
    private static int[] parseCoords(String text) {
        if (text == null) return null;
        String s = text.replace(',', ' ').trim();
        String[] parts = s.split("\\s+");
        if (parts.length < 3) return null;
        try {
            return new int[]{Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2])};
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static ResourceKey<Level> dimensionKey(String dimension) {
        ResourceLocation rl = ResourceLocation.tryParse(dimension);
        return rl == null ? Level.OVERWORLD : ResourceKey.create(Registries.DIMENSION, rl);
    }

    // ------------------------------------------------------------------------------------------------ teleport

    public boolean canTeleport() {
        return teleportCooldown <= 0;
    }

    public boolean tryTeleport(BlockPos pos, @Nullable ResourceKey<Level> dimension, String reason) {
        if (teleportCooldown > 0) return false;
        boolean moved = teleportInternal(pos, dimension);
        if (moved) {
            teleportCooldown = 200;
            if (ownerId() != null) {
                try {
                    memory().note(ownerId(), "Travelled to " + reason + ".");
                } catch (Throwable ignored) {
                }
            }
        }
        return moved;
    }

    private boolean forceTeleport(Place place) {
        boolean moved = teleportInternal(place.pos(), place.dimension());
        if (moved) teleportCooldown = 100;
        return moved;
    }

    private boolean teleportInternal(BlockPos pos, @Nullable ResourceKey<Level> dimension) {
        if (!(helper.level() instanceof ServerLevel current)) return false;
        ServerLevel target = dimension == null ? current : current.getServer().getLevel(dimension);
        if (target == null) {
            say("That dimension isn't loaded.", "worried");
            return false;
        }
        return EmilyNav.teleportTo(helper, target, pos);
    }

    // ------------------------------------------------------------------------------------------------ voice

    public void say(String text, String mood) {
        if (text == null || text.isBlank() || helper.level().isClientSide) return;
        ServerPlayer owner = helper.getOwner();
        if (owner == null) return;
        if (helper.distanceToSqr(owner) > 128.0 * 128.0) return;
        if (speakCooldown > 0) return;
        speakCooldown = 30;
        try {
            PacketDistributor.sendToPlayer(owner, new HelperPayloads.AgentSay(text, mood == null ? "calm" : mood));
        } catch (Throwable t) {
            LOGGER.debug("Could not send Emily's line", t);
        }
        owner.displayClientMessage(Component.literal("§7[" + helper.getName().getString() + "] §f" + text).withStyle(ChatFormatting.GRAY), true);
    }

    // ------------------------------------------------------------------------------------------------ save / load

    public void save(CompoundTag tag) {
        tag.putInt("EmilyAutonomy", autonomy.ordinal());
        tag.putString("EmilyLastTask", lastTaskName == null ? "" : lastTaskName);
    }

    public void load(CompoundTag tag) {
        if (tag.contains("EmilyAutonomy")) {
            int ordinal = tag.getInt("EmilyAutonomy");
            Autonomy[] values = Autonomy.values();
            if (ordinal >= 0 && ordinal < values.length) autonomy = values[ordinal];
        }
        if (tag.contains("EmilyLastTask")) lastTaskName = tag.getString("EmilyLastTask");
    }

    /** Ask the planner to reconsider soon, used after an order is cancelled or a task ends. */
    public void nudgePlanner(int ticks) {
        decideCooldown = Math.max(20, ticks);
    }

    public List<String> recentMemories() {
        if (ownerId() == null) return List.of();
        try {
            return memory().recentNotes(ownerId(), 4);
        } catch (Throwable t) {
            return List.of();
        }
    }
}
