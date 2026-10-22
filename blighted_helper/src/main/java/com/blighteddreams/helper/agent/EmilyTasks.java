package com.blighteddreams.helper.agent;

import com.blighteddreams.helper.HelperEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** The everyday tasks: go somewhere, explore, loot, deposit, guard, patrol, and build a nether portal. */
public final class EmilyTasks {
    private EmilyTasks() {
    }

    // ------------------------------------------------------------------------------------------------ travel

    public static class TravelTask implements EmilyTask {
        protected BlockPos target;
        protected ResourceKey<Level> dimension;
        protected final String baseLabel;
        protected final boolean allowTeleport;
        protected int elapsed;
        protected int stuck;
        protected int lastDistance;
        protected boolean failed;

        public TravelTask(BlockPos target, @Nullable ResourceKey<Level> dimension, String baseLabel, boolean allowTeleport) {
            this.target = target;
            this.dimension = dimension;
            this.baseLabel = baseLabel;
            this.allowTeleport = allowTeleport;
        }

        public TravelTask(BlockPos target, String baseLabel) {
            this(target, null, baseLabel, true);
        }

        public TravelTask(BlockPos target, String baseLabel, boolean allowTeleport) {
            this(target, null, baseLabel, allowTeleport);
        }

        @Override
        public String name() {
            return "travel";
        }

        @Override
        public String label() {
            return baseLabel + (lastDistance > 0 ? " (" + lastDistance + "m)" : "");
        }

        @Override
        public void start(HelperEntity helper, EmilyAgent agent) {
            if (target == null) {
                failed = true;
                return;
            }
            ServerLevel level = (ServerLevel) helper.level();
            if (dimension != null && level.dimension() != dimension) {
                if (allowTeleport && agent.tryTeleport(target, dimension, "travelling")) {
                    return;
                }
                failed = true;
                agent.say("I need a portal to get there.", "worried");
            }
        }

        @Override
        public Status tick(HelperEntity helper, EmilyAgent agent) {
            if (failed || target == null) return Status.FAILED;
            elapsed++;
            ServerLevel level = (ServerLevel) helper.level();
            if (dimension != null && level.dimension() != dimension) {
                if (allowTeleport && agent.tryTeleport(target, dimension, baseLabel)) {
                    return Status.RUNNING;
                }
                return Status.FAILED;
            }

            double distance = Math.sqrt(EmilyNav.distSqr(helper, target));
            lastDistance = (int) Math.round(distance);
            if (distance <= 2.4) {
                EmilyNav.stop(helper);
                return Status.DONE;
            }

            // Long trips are allowed to use her teleport when the config permits it. The planner keeps
            // local jobs local, so this mostly matters for structures and the owner being far away.
            if (allowTeleport && distance > 192 && agent.canTeleport()) {
                if (agent.tryTeleport(target, null, baseLabel)) {
                    return Status.RUNNING;
                }
            }

            boolean arrived = EmilyNav.moveTo(helper, target, 1.35, 2.4);
            if (arrived) return Status.DONE;
            if (helper.getNavigation().isDone()) {
                stuck++;
            } else {
                stuck = 0;
            }
            if (stuck > 80) {
                if (allowTeleport && distance > 12 && agent.canTeleport()) {
                    agent.tryTeleport(target, null, baseLabel);
                    stuck = 0;
                    return Status.RUNNING;
                }
                return Status.FAILED;
            }
            if (elapsed > 20 * 60 * 8) return Status.FAILED;
            return Status.RUNNING;
        }

        @Override
        public boolean roaming() {
            return true;
        }

        @Override
        public boolean crossDimension() {
            return true;
        }
    }

    public static class ReturnTask extends TravelTask {
        private final UUID ownerId;

        public ReturnTask(ServerPlayer owner) {
            super(owner.blockPosition(), owner.level().dimension(), "coming back to you", true);
            this.ownerId = owner.getUUID();
        }

        @Override
        public String name() {
            return "return";
        }

        @Override
        public Status tick(HelperEntity helper, EmilyAgent agent) {
            ServerPlayer owner = helper.getOwner();
            if (owner != null && (helper.tickCount % 20 == 0 || target == null)) {
                target = owner.blockPosition();
                dimension = owner.level().dimension();
            }
            return super.tick(helper, agent);
        }

        @Override
        public boolean roaming() {
            return false;
        }
    }

    public static class StructureTask extends TravelTask {
        private final String query;

        public StructureTask(String query) {
            super(null, null, "looking for " + query, true);
            this.query = query;
        }

        @Override
        public String name() {
            return "structure";
        }

        @Override
        public void start(HelperEntity helper, EmilyAgent agent) {
            ServerLevel level = (ServerLevel) helper.level();
            BlockPos found = EmilyStructures.locate(level, query, helper.blockPosition());
            if (found == null) {
                failed = true;
                agent.say("I can't find a " + query + " from here. We may need a different dimension.", "worried");
                return;
            }
            target = found;
            dimension = level.dimension();
            lastDistance = (int) Math.sqrt(EmilyNav.distSqr(helper, found));
            agent.say("Found a " + query + " about " + lastDistance + " blocks away. Heading there now.", "cheerful");
            if (agent.ownerId() != null) {
                agent.memory().remember(agent.ownerId(), query, level.dimension(), found, "structure");
            }
            super.start(helper, agent);
        }
    }

    // ------------------------------------------------------------------------------------------------ explore

    public static class ExploreTask implements EmilyTask {
        private final String direction;
        private final int distance;
        private BlockPos goal;
        private TravelTask walker;
        private int startX, startZ;

        public ExploreTask(String direction, int distance) {
            this.direction = direction == null ? "" : direction.trim().toLowerCase(Locale.ROOT);
            this.distance = Math.max(32, Math.min(512, distance <= 0 ? 128 : distance));
        }

        @Override
        public String name() {
            return "explore";
        }

        @Override
        public String label() {
            return walker != null ? walker.label() : "exploring";
        }

        @Override
        public void start(HelperEntity helper, EmilyAgent agent) {
            ServerLevel level = (ServerLevel) helper.level();
            startX = helper.getX() > 0 ? (int) helper.getX() : 0;
            startZ = helper.getZ() > 0 ? (int) helper.getZ() : 0;
            Direction dir = pickDirection(helper);
            BlockPos raw = helper.blockPosition().relative(dir, distance);
            BlockPos safe = EmilyNav.findSafeSpot(level, helper, raw, 16);
            if (safe == null) safe = raw;
            goal = safe;
            walker = new TravelTask(goal, "exploring " + dir.getName(), true);
            walker.start(helper, agent);
        }

        private Direction pickDirection(HelperEntity helper) {
            switch (direction) {
                case "north" -> {
                    return Direction.NORTH;
                }
                case "south" -> {
                    return Direction.SOUTH;
                }
                case "east" -> {
                    return Direction.EAST;
                }
                case "west" -> {
                    return Direction.WEST;
                }
            }
            Direction[] dirs = Direction.Plane.HORIZONTAL.stream().toArray(Direction[]::new);
            return dirs[helper.getRandom().nextInt(dirs.length)];
        }

        @Override
        public Status tick(HelperEntity helper, EmilyAgent agent) {
            if (walker == null) return Status.FAILED;
            Status status = walker.tick(helper, agent);
            if (status == Status.DONE) {
                if (agent.ownerId() != null && goal != null) {
                    agent.memory().remember(agent.ownerId(), "explored_" + (helper.level().getGameTime() / 24000L),
                            helper.level().dimension(), goal, "explored");
                    agent.memory().note(agent.ownerId(), "Explored around " + goal.getX() + " " + goal.getZ() + ".");
                }
                agent.say("That was a good scouting run. I marked the spot in my memory.", "cheerful");
            } else if (status == Status.FAILED) {
                agent.say("The terrain blocked me before I got far. I'll try another route later.", "worried");
            }
            return status;
        }

        @Override
        public void stop(HelperEntity helper, EmilyAgent agent) {
            if (walker != null) walker.stop(helper, agent);
        }

        @Override
        public boolean quietOnDone() {
            return true;
        }
    }

    // ------------------------------------------------------------------------------------------------ containers

    public static class LootTask implements EmilyTask {
        private final int radius;
        private final boolean allowNearHome;
        private BlockPos container;
        private ItemEntity item;
        private int elapsed;
        private int taken;
        private boolean done;

        public LootTask(int radius, boolean allowNearHome) {
            this.radius = Math.max(6, radius);
            this.allowNearHome = allowNearHome;
        }

        @Override
        public String name() {
            return "loot";
        }

        @Override
        public String label() {
            return "looting" + (taken > 0 ? " (" + taken + " items)" : "");
        }

        @Override
        public void start(HelperEntity helper, EmilyAgent agent) {
            if (!(helper.level() instanceof ServerLevel level)) {
                done = true;
                return;
            }
            container = EmilyBlocks.findContainer(level, helper.blockPosition(), radius, allowNearHome, agent.ownerId());
            if (container == null) {
                item = EmilyBlocks.findItem(level, helper.blockPosition(), radius);
            }
            if (container == null && item == null) {
                done = true;
            }
        }

        @Override
        public Status tick(HelperEntity helper, EmilyAgent agent) {
            if (done) return Status.DONE;
            elapsed++;
            if (!(helper.level() instanceof ServerLevel level)) return Status.FAILED;

            if (container != null) {
                if (!(level.getBlockEntity(container) instanceof Container c)) {
                    container = null;
                    return Status.RUNNING;
                }
                if (!EmilyNav.inReach(helper, container, 4.5)) {
                    if (!EmilyNav.moveTo(helper, container, 1.2, 3.0)) {
                        if (elapsed > 400) return Status.FAILED;
                    }
                    return Status.RUNNING;
                }
                EmilyNav.stop(helper);
                EmilyNav.lookAt(helper, container);
                for (int i = 0; i < c.getContainerSize(); i++) {
                    ItemStack stack = c.getItem(i);
                    if (stack.isEmpty()) continue;
                    taken += stack.getCount();
                    helper.addToBagOrDrop(stack.copy());
                    c.setItem(i, ItemStack.EMPTY);
                }
                c.setChanged();
                level.playSound(null, container, SoundEvents.CHEST_OPEN, SoundSource.BLOCKS, 0.7F, 1.0F);
                agent.say("Found a chest. " + (taken > 0 ? "Took " + taken + " items." : "It was empty."), "smug");
                done = true;
                return Status.DONE;
            }

            if (item != null && item.isAlive()) {
                if (!EmilyNav.inReach(helper, item.blockPosition(), 3.0)) {
                    EmilyNav.moveTo(helper, item, 1.25, 2.0);
                } else {
                    ItemStack stack = item.getItem().copy();
                    taken += stack.getCount();
                    helper.addToBagOrDrop(stack);
                    item.discard();
                    item = EmilyBlocks.findItem(level, helper.blockPosition(), radius);
                    if (item == null) {
                        agent.say("Picked up " + taken + " dropped items.", "cheerful");
                        done = true;
                        return Status.DONE;
                    }
                }
                if (elapsed > 600) return Status.FAILED;
                return Status.RUNNING;
            }

            return Status.DONE;
        }

        @Override
        public boolean roaming() {
            return allowNearHome;
        }

        @Override
        public boolean quietOnDone() {
            return true;
        }
    }

    public static class DepositTask implements EmilyTask {
        private final int radius;
        private BlockPos container;
        private int elapsed;
        private int stored;
        private boolean done;

        public DepositTask(int radius) {
            this.radius = Math.max(8, radius);
        }

        @Override
        public String name() {
            return "deposit";
        }

        @Override
        public String label() {
            return "storing supplies" + (stored > 0 ? " (" + stored + ")" : "");
        }

        @Override
        public void start(HelperEntity helper, EmilyAgent agent) {
            if (!(helper.level() instanceof ServerLevel level)) {
                done = true;
                return;
            }
            container = EmilyBlocks.findContainer(level, helper.blockPosition(), radius, true, agent.ownerId());
            if (container == null && agent.ownerId() != null) {
                EmilyMemory.Waypoint home = agent.memory().find(agent.ownerId(), "home");
                if (home == null) home = agent.memory().find(agent.ownerId(), "base");
                if (home != null && home.dimension().equals(level.dimension().location().toString())) {
                    container = EmilyBlocks.findContainer(level, home.pos(), 24, true, agent.ownerId());
                }
            }
            if (container == null) {
                done = true;
            }
        }

        @Override
        public Status tick(HelperEntity helper, EmilyAgent agent) {
            if (done) return Status.DONE;
            elapsed++;
            if (!(helper.level() instanceof ServerLevel level)) return Status.FAILED;
            if (container == null || !(level.getBlockEntity(container) instanceof Container c)) {
                agent.say("I couldn't find a chest to store things in.", "worried");
                return Status.FAILED;
            }
            if (!EmilyNav.inReach(helper, container, 4.5)) {
                if (!EmilyNav.moveTo(helper, container, 1.2, 3.0) && elapsed > 400) return Status.FAILED;
                return Status.RUNNING;
            }
            EmilyNav.stop(helper);
            EmilyNav.lookAt(helper, container);

            var bag = helper.bag();
            for (int i = 0; i < bag.getSlots(); i++) {
                ItemStack stack = bag.getStackInSlot(i);
                if (stack.isEmpty() || keep(stack)) continue;
                int before = stack.getCount();
                ItemStack leftover = insertInto(c, stack);
                int moved = before - leftover.getCount();
                if (moved > 0) {
                    bag.extractItem(i, moved, false);
                    stored += moved;
                }
            }
            c.setChanged();
            level.playSound(null, container, SoundEvents.CHEST_CLOSE, SoundSource.BLOCKS, 0.7F, 1.0F);
            if (stored > 0) {
                agent.say("Stored " + stored + " items. The base is getting organized.", "cheerful");
            } else {
                agent.say("There was nothing worth storing.", "calm");
            }
            done = true;
            return Status.DONE;
        }

        private static ItemStack insertInto(Container c, ItemStack stack) {
            ItemStack left = stack.copy();
            for (int pass = 0; pass < 2 && !left.isEmpty(); pass++) {
                for (int j = 0; j < c.getContainerSize() && !left.isEmpty(); j++) {
                    ItemStack slot = c.getItem(j);
                    if (pass == 0) {
                        if (slot.isEmpty() || !ItemStack.isSameItemSameComponents(slot, left)) continue;
                        int room = Math.min(slot.getMaxStackSize(), c.getMaxStackSize()) - slot.getCount();
                        if (room <= 0) continue;
                        int move = Math.min(room, left.getCount());
                        slot.grow(move);
                        left.shrink(move);
                        c.setItem(j, slot);
                    } else {
                        if (!slot.isEmpty()) continue;
                        if (!c.canPlaceItem(j, left)) continue;
                        c.setItem(j, left.copy());
                        left = ItemStack.EMPTY;
                    }
                }
            }
            return left;
        }

        private static boolean keep(ItemStack stack) {
            var item = stack.getItem();
            if (item instanceof net.minecraft.world.item.ArmorItem
                    || item instanceof net.minecraft.world.item.SwordItem
                    || item instanceof net.minecraft.world.item.AxeItem
                    || item instanceof net.minecraft.world.item.PickaxeItem
                    || item instanceof net.minecraft.world.item.ShovelItem
                    || item instanceof net.minecraft.world.item.HoeItem
                    || item instanceof net.minecraft.world.item.ProjectileWeaponItem
                    || stack.has(net.minecraft.core.component.DataComponents.FOOD)
                    || stack.has(net.minecraft.core.component.DataComponents.POTION_CONTENTS)) return true;
            return stack.is(Items.SHIELD) || stack.is(Items.TOTEM_OF_UNDYING) || stack.is(Items.TORCH)
                    || stack.is(Items.FLINT_AND_STEEL) || stack.is(Items.ENDER_PEARL) || stack.is(Items.ENDER_EYE)
                    || stack.is(Items.BUCKET) || stack.is(Items.WATER_BUCKET) || stack.is(Items.ARROW)
                    || stack.is(Items.SPECTRAL_ARROW) || stack.is(Items.TIPPED_ARROW);
        }

        @Override
        public boolean roaming() {
            return false;
        }

        @Override
        public boolean quietOnDone() {
            return true;
        }
    }

    // ------------------------------------------------------------------------------------------------ guard / patrol

    public static class GuardTask implements EmilyTask {
        private final int duration;
        private BlockPos anchor;
        private int elapsed;
        private LivingEntity chosen;

        public GuardTask(int duration) {
            this.duration = Math.max(200, duration);
        }

        @Override
        public String name() {
            return "guard";
        }

        @Override
        public String label() {
            return "guarding" + (chosen != null && chosen.isAlive() ? " (" + chosen.getName().getString() + ")" : "");
        }

        @Override
        public void start(HelperEntity helper, EmilyAgent agent) {
            anchor = helper.blockPosition();
            agent.say("I'll keep watch here. Nothing gets past me.", "battle");
        }

        @Override
        public Status tick(HelperEntity helper, EmilyAgent agent) {
            elapsed++;
            ServerPlayer owner = helper.getOwner();
            if (owner != null && helper.distanceToSqr(owner) > 400.0) {
                EmilyNav.moveTo(helper, owner, 1.3, 3.0);
                return Status.RUNNING;
            }
            LivingEntity target = helper.getTarget();
            if (target == null || !target.isAlive()) {
                chosen = findThreat(helper);
                if (chosen != null) helper.setTarget(chosen);
            } else {
                chosen = target;
            }
            if (anchor != null && helper.blockPosition().distSqr(anchor) > 144) {
                EmilyNav.moveTo(helper, anchor, 1.1, 4.0);
            } else if (owner != null) {
                EmilyNav.lookAt(helper, owner);
            }
            if (elapsed >= duration) {
                agent.say("Watch is over. I'm still in one piece.", "calm");
                return Status.DONE;
            }
            return Status.RUNNING;
        }

        @Nullable
        private static LivingEntity findThreat(HelperEntity helper) {
            List<Mob> mobs = helper.level().getEntitiesOfClass(Mob.class,
                    new AABB(helper.blockPosition()).inflate(16.0),
                    e -> e.isAlive() && e instanceof net.minecraft.world.entity.monster.Enemy
                            && !(e instanceof net.minecraft.world.entity.monster.Creeper)
                            && !(e instanceof net.minecraft.world.entity.monster.EnderMan)
                            && helper.canAttack(e));
            return mobs.stream()
                    .filter(e -> e.distanceToSqr(helper) <= 144.0
                            && (e.getTarget() == helper || helper.isOwner(e.getTarget())
                            || e.getTarget() == null || e.getTarget() == helper.getOwner()))
                    .min(Comparator.comparingDouble(helper::distanceToSqr))
                    .orElse(null);
        }

        @Override
        public void stop(HelperEntity helper, EmilyAgent agent) {
            if (chosen != null && helper.getTarget() == chosen) helper.setTarget(null);
        }

        @Override
        public boolean roaming() {
            return false;
        }

        @Override
        public boolean quietOnDone() {
            return true;
        }
    }

    public static class PatrolTask implements EmilyTask {
        private final int duration;
        private final int radius;
        private BlockPos anchor;
        private BlockPos waypoint;
        private int elapsed;

        public PatrolTask(int duration, int radius) {
            this.duration = Math.max(200, duration);
            this.radius = Math.max(8, radius);
        }

        @Override
        public String name() {
            return "patrol";
        }

        @Override
        public String label() {
            return "patrolling";
        }

        @Override
        public void start(HelperEntity helper, EmilyAgent agent) {
            anchor = helper.blockPosition();
            agent.say("Patrolling the area. Call out if you see anything.", "calm");
        }

        @Override
        public Status tick(HelperEntity helper, EmilyAgent agent) {
            elapsed++;
            if (!(helper.level() instanceof ServerLevel level)) return Status.FAILED;
            if (waypoint == null || EmilyNav.isNear(helper, waypoint, 2.0) || helper.tickCount % 200 == 0) {
                waypoint = randomPoint(level, helper, anchor, radius);
            }
            if (waypoint != null) EmilyNav.moveTo(helper, waypoint, 1.05, 2.0);
            if (elapsed >= duration) {
                agent.say("Patrol finished. All quiet.", "calm");
                return Status.DONE;
            }
            return Status.RUNNING;
        }

        @Nullable
        private BlockPos randomPoint(ServerLevel level, HelperEntity helper, BlockPos center, int radius) {
            for (int i = 0; i < 12; i++) {
                int dx = helper.getRandom().nextInt(radius * 2 + 1) - radius;
                int dz = helper.getRandom().nextInt(radius * 2 + 1) - radius;
                BlockPos raw = center.offset(dx, 0, dz);
                BlockPos safe = EmilyNav.findSafeSpot(level, helper, raw, 5);
                if (safe != null) return safe;
            }
            return null;
        }

        @Override
        public boolean roaming() {
            return false;
        }

        @Override
        public boolean quietOnDone() {
            return true;
        }
    }

    public static class IdleTask implements EmilyTask {
        private final int duration;
        private int elapsed;

        public IdleTask(int duration) {
            this.duration = Math.max(20, duration);
        }

        @Override
        public String name() {
            return "idle";
        }

        @Override
        public String label() {
            return "taking a moment";
        }

        @Override
        public Status tick(HelperEntity helper, EmilyAgent agent) {
            elapsed++;
            EmilyNav.stop(helper);
            if (helper.tickCount % 40 == 0) {
                helper.getLookControl().setLookAt(helper.getX() + helper.getRandom().nextDouble() * 4 - 2,
                        helper.getEyeY(), helper.getZ() + helper.getRandom().nextDouble() * 4 - 2, 20.0F, 20.0F);
            }
            return elapsed >= duration ? Status.DONE : Status.RUNNING;
        }
    }

    // ------------------------------------------------------------------------------------------------ nether portal

    public static class PortalTask implements EmilyTask {
        private BlockPos base;
        private Direction.Axis axis = Direction.Axis.X;
        private final List<BlockPos> frame = new ArrayList<>();
        private int cursor;
        private int delay;
        private int elapsed;
        private int phase; // 0 = build, 1 = clear, 2 = light, 3 = verify
        private boolean done;
        private boolean failed;

        @Override
        public String name() {
            return "portal";
        }

        @Override
        public String label() {
            return phase < 2 ? "building a nether portal" : "lighting the portal";
        }

        @Override
        public void start(HelperEntity helper, EmilyAgent agent) {
            if (!(helper.level() instanceof ServerLevel level)) {
                failed = true;
                return;
            }
            if (findPortal(level, helper.blockPosition(), 12) != null) {
                agent.say("There is already a portal right here.", "cheerful");
                done = true;
                return;
            }
            if (EmilyBlocks.count(helper, Items.OBSIDIAN) < 10) {
                agent.say("I need ten obsidian blocks to build a portal. Do we have any?", "worried");
                failed = true;
                return;
            }
            if (!helper.hasItem(Items.FLINT_AND_STEEL)) {
                agent.say("I have the obsidian, but I need flint and steel to light it.", "worried");
                failed = true;
                return;
            }
            if (!chooseSite(level, helper)) {
                agent.say("I can't find a flat, clear spot for the portal here.", "worried");
                failed = true;
                return;
            }
            frame.clear();
            frame.add(framePos(base, axis, 1, 0));
            frame.add(framePos(base, axis, 2, 0));
            for (int y = 1; y <= 3; y++) {
                frame.add(framePos(base, axis, 0, y));
                frame.add(framePos(base, axis, 3, y));
            }
            frame.add(framePos(base, axis, 1, 4));
            frame.add(framePos(base, axis, 2, 4));
            cursor = 0;
            agent.say("Building a nether portal. Stand back a little.", "calm");
        }

        private boolean chooseSite(ServerLevel level, HelperEntity helper) {
            BlockPos feet = helper.blockPosition();
            for (Direction.Axis candidate : new Direction.Axis[]{Direction.Axis.X, Direction.Axis.Z}) {
                for (int attempt = 0; attempt < 8; attempt++) {
                    BlockPos origin = feet.offset(helper.getRandom().nextInt(9) - 4, 0,
                            helper.getRandom().nextInt(9) - 4);
                    if (fits(level, origin, candidate)) {
                        base = origin;
                        axis = candidate;
                        return true;
                    }
                }
            }
            // Last resort: build at her feet if there is air above.
            if (fits(level, feet, Direction.Axis.X)) {
                base = feet;
                axis = Direction.Axis.X;
                return true;
            }
            return false;
        }

        private boolean fits(ServerLevel level, BlockPos origin, Direction.Axis candidate) {
            for (int x = 0; x <= 3; x++) {
                for (int y = 0; y <= 4; y++) {
                    BlockPos pos = candidate == Direction.Axis.X ? origin.offset(x, y, 0) : origin.offset(0, y, x);
                    var state = level.getBlockState(pos);
                    boolean inside = x >= 1 && x <= 2 && y >= 1 && y <= 3;
                    if (!inside && y == 0) continue;
                    if (state.isAir() || state.canBeReplaced()) continue;
                    if (inside && state.getDestroySpeed(level, pos) <= 20) continue;
                    return false;
                }
            }
            return !level.getBlockState(origin.below()).getFluidState().isEmpty() ? false : true;
        }

        private static BlockPos framePos(BlockPos origin, Direction.Axis axis, int x, int y) {
            return axis == Direction.Axis.X ? origin.offset(x, y, 0) : origin.offset(0, y, x);
        }

        private BlockPos framePos(int index) {
            return frame.get(index);
        }

        @Override
        public Status tick(HelperEntity helper, EmilyAgent agent) {
            if (done) return Status.DONE;
            if (failed || base == null) return Status.FAILED;
            if (!(helper.level() instanceof ServerLevel level)) return Status.FAILED;
            elapsed++;
            if (elapsed > 20 * 60 * 4) return Status.FAILED;
            if (delay-- > 0) return Status.RUNNING;

            if (phase == 0) {
                if (cursor >= frame.size()) {
                    phase = 1;
                    cursor = 0;
                    return Status.RUNNING;
                }
                BlockPos pos = framePos(cursor++);
                if (!level.getBlockState(pos).is(Blocks.OBSIDIAN)) {
                    if (!EmilyBlocks.place(helper, level, pos, Blocks.OBSIDIAN.defaultBlockState(), Items.OBSIDIAN)) {
                        agent.say("I'm out of obsidian.", "worried");
                        return Status.FAILED;
                    }
                }
                helper.triggerSwingAnim();
                EmilyNav.lookAt(helper, pos);
                delay = 3;
                return Status.RUNNING;
            }

            if (phase == 1) {
                // Clear the 2x3 interior.
                if (cursor >= 6) {
                    phase = 2;
                    cursor = 0;
                    return Status.RUNNING;
                }
                int x = 1 + (cursor % 2);
                int y = 1 + (cursor / 2);
                BlockPos pos = axis == Direction.Axis.X ? base.offset(x, y, 0) : base.offset(0, y, x);
                cursor++;
                var state = level.getBlockState(pos);
                if (!state.isAir()) {
                    if (state.getDestroySpeed(level, pos) > 20) {
                        agent.say("There is something too hard in the way of the portal.", "worried");
                        return Status.FAILED;
                    }
                    level.destroyBlock(pos, false, helper);
                }
                delay = 2;
                return Status.RUNNING;
            }

            if (phase == 2) {
                BlockPos light = axis == Direction.Axis.X ? base.offset(1, 1, 0) : base.offset(0, 1, 1);
                ItemStack flint = EmilyBlocks.findStack(helper, s -> s.is(Items.FLINT_AND_STEEL));
                if (flint.isEmpty()) {
                    agent.say("My flint and steel is gone.", "worried");
                    return Status.FAILED;
                }
                if (BaseFireBlock.canBePlacedAt(level, light, Direction.UP)) {
                    level.setBlock(light, BaseFireBlock.getState(level, light), 11);
                    if (flint.isDamageableItem()) flint.hurtAndBreak(1, helper, EquipmentSlot.MAINHAND);
                    level.playSound(null, light, SoundEvents.FLINTANDSTEEL_USE, SoundSource.BLOCKS, 1.0F, 1.0F);
                }
                phase = 3;
                delay = 30;
                return Status.RUNNING;
            }

            // phase 3: verify
            BlockPos portal = findPortal(level, base, 8);
            if (portal == null) {
                agent.say("The frame didn't catch. I may need to rebuild a block.", "worried");
                return Status.FAILED;
            }
            if (agent.ownerId() != null) {
                agent.memory().remember(agent.ownerId(), "nether_portal", level.dimension(), portal, "portal");
                agent.memory().note(agent.ownerId(), "Built a nether portal at " + portal.getX() + " " + portal.getY() + " " + portal.getZ() + ".");
            }
            agent.say("Portal's lit. The Nether is waiting.", "battle");
            done = true;
            return Status.DONE;
        }

        @Nullable
        private static BlockPos findPortal(ServerLevel level, BlockPos center, int radius) {
            for (BlockPos pos : BlockPos.betweenClosed(center.offset(-radius, -6, -radius), center.offset(radius, 6, radius))) {
                if (level.getBlockState(pos).is(Blocks.NETHER_PORTAL)) return pos.immutable();
            }
            return null;
        }

        @Override
        public boolean roaming() {
            return true;
        }

        @Override
        public boolean quietOnDone() {
            return true;
        }
    }
}
