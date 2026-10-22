package com.blighteddreams.helper.agent;

import com.blighteddreams.helper.HelperEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;
import java.util.Locale;

/**
 * Mining with a plan: look for exposed ore first, then walk down a staircase to the right depth, then drive a
 * two-high tunnel and cut small side branches. She breaks the blocks herself, picks up the drops and changes tools
 * when the rock changes. No Baritone required.
 */
public class EmilyMineTask implements EmilyTask {
    private final String target;
    private final int desired;
    private final boolean branch;
    private int mined;
    private int elapsed;
    private int scanCooldown;
    private int targetY;
    private boolean failed;
    private boolean announcedDepth;

    private BlockPos currentOre;
    private BlockPos breakerPos;
    private ItemStack breakerTool = ItemStack.EMPTY;
    private int breakTicks;
    private int breakTotal;

    private Direction tunnelDir;
    private int tunnelLength;
    private int branchCooldown;
    private boolean branchPhase;
    private int branchDepth;
    private BlockPos branchOrigin;

    public EmilyMineTask(String target, int desired) {
        this(target, desired, true);
    }

    public EmilyMineTask(String target, int desired, boolean branch) {
        this.target = target == null || target.isBlank() ? "any ore" : target.trim().toLowerCase(Locale.ROOT);
        this.branch = branch;
        int fallback = this.target.contains("stone") || this.target.contains("cobble") ? 32 : 16;
        this.desired = desired <= 0 ? fallback : Math.min(256, desired);
    }

    @Override
    public String name() {
        return "mine";
    }

    @Override
    public String label() {
        return "mining " + target + " (" + mined + "/" + desired + ")";
    }

    @Override
    public void start(HelperEntity helper, EmilyAgent agent) {
        if (!(helper.level() instanceof ServerLevel level)) {
            failed = true;
            return;
        }
        targetY = chooseDepth(helper, target);
        if (target.equals("ancient debris") || target.equals("debris") || target.equals("netherite")) {
            if (!level.dimension().location().getPath().contains("nether")) {
                agent.say("Ancient debris is in the Nether. We need a portal before I can mine it.", "worried");
                failed = true;
                return;
            }
        }
        if (!hasAnyPickaxe(helper)) {
            agent.say("I don't have a pickaxe. I can't mine like this.", "worried");
            failed = true;
            return;
        }
        tunnelDir = helper.getDirection();
        agent.say("Mining " + target + ". I'll call out if I find something good.", "calm");
    }

    private static int chooseDepth(HelperEntity helper, String target) {
        if (target.contains("debris") || target.contains("netherite")) return 15;
        if (target.contains("diamond")) return -54;
        if (target.contains("redstone")) return -54;
        if (target.contains("lapis")) return 0;
        if (target.contains("emerald")) return 90;
        if (target.contains("gold")) return -16;
        if (target.contains("iron")) return 16;
        if (target.contains("copper")) return 48;
        if (target.contains("coal")) return 64;
        if (target.contains("obsidian")) return 24;
        return Math.max(-60, Math.min(120, helper.blockPosition().getY()));
    }

    private static boolean hasAnyPickaxe(HelperEntity helper) {
        if (helper.getMainHandItem().getItem() instanceof net.minecraft.world.item.PickaxeItem) return true;
        if (helper.getOffhandItem().getItem() instanceof net.minecraft.world.item.PickaxeItem) return true;
        var bag = helper.bag();
        for (int i = 0; i < bag.getSlots(); i++) {
            if (bag.getStackInSlot(i).getItem() instanceof net.minecraft.world.item.PickaxeItem) return true;
        }
        return false;
    }

    @Override
    public Status tick(HelperEntity helper, EmilyAgent agent) {
        if (failed) return Status.FAILED;
        if (!(helper.level() instanceof ServerLevel level)) return Status.FAILED;
        elapsed++;
        if (elapsed > 20 * 60 * 12) return Status.FAILED;
        if (EmilyBlocks.freeBagSlots(helper) <= 1) {
            agent.say("My bag is full. I'm heading back with " + mined + " blocks.", "calm");
            return Status.DONE;
        }

        if (currentOre != null) return mineOre(helper, agent, level);

        if (--scanCooldown <= 0) {
            scanCooldown = 10;
            BlockPos found = EmilyBlocks.findTarget(level, helper, helper.blockPosition(), target,
                    12, 8, true, agent.ownerId());
            if (found != null) {
                currentOre = found;
                return Status.RUNNING;
            }
        }

        if (mined >= desired) return Status.DONE;

        // No exposed ore. If this is a branch job, go to the right depth and start a tunnel.
        if (!branch) {
            agent.say("I can't find any exposed " + target + " around here.", "worried");
            return Status.FAILED;
        }
        if (Math.abs(helper.blockPosition().getY() - targetY) > 2) {
            if (!announcedDepth) {
                announcedDepth = true;
                agent.say("Nothing exposed nearby. I'm digging down to Y " + targetY + ".", "calm");
            }
            return digStairs(helper, agent, level);
        }
        return digTunnel(helper, agent, level);
    }

    // ------------------------------------------------------------------------------------------------ ore

    private Status mineOre(HelperEntity helper, EmilyAgent agent, ServerLevel level) {
        BlockPos pos = currentOre;
        if (pos == null) return Status.RUNNING;
        BlockState state = level.getBlockState(pos);
        if (state.isAir() || !EmilyBlocks.matches(target, state)) {
            currentOre = null;
            return Status.RUNNING;
        }
        if (EmilyBlocks.isProtected(level, pos, agent.ownerId(), 10) && !target.equals("obsidian")) {
            currentOre = null;
            return Status.RUNNING;
        }

        double dist = helper.getEyePosition().distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(pos));
        if (dist > 20.0) {
            if (!EmilyNav.moveTo(helper, pos, 1.2, 2.5)) {
                // Can't path to it: dig toward it instead.
                digToward(helper, level, pos);
            }
            return Status.RUNNING;
        }

        if (!EmilyBlocks.equipTool(helper, state)) {
            agent.say("I don't have the right tool for " + state.getBlock().getName().getString() + ".", "worried");
            failed = true;
            return Status.FAILED;
        }
        EmilyNav.stop(helper);
        EmilyNav.lookAt(helper, pos);
        if (breakerPos == null || !breakerPos.equals(pos)) {
            breakerPos = pos;
            breakerTool = helper.getMainHandItem().copy();
            breakTicks = 0;
            breakTotal = EmilyBlocks.miningTicks(level, pos, state, helper.getMainHandItem());
            if (breakTotal < 0) {
                currentOre = null;
                return Status.RUNNING;
            }
        }

        breakTicks++;
        helper.triggerSwingAnim();
        if (breakTicks % 4 == 0) {
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state),
                    pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 4, 0.22, 0.22, 0.22, 0.03);
            helper.playSound(state.getSoundType().getHitSound(), 0.55F, 0.9F);
        }
        if (breakTicks >= breakTotal) {
            ItemStack tool = helper.getMainHandItem();
            EmilyBlocks.breakBlock(helper, level, pos, tool);
            mined++;
            currentOre = null;
            breakerPos = null;
            breakTicks = 0;
            if (mined % 8 == 0) {
                agent.say("That's " + mined + " " + target + " so far.", "cheerful");
            }
        }
        return Status.RUNNING;
    }

    /** Digs a straight, two-high corridor toward a block she cannot path to. */
    private void digToward(HelperEntity helper, ServerLevel level, BlockPos targetPos) {
        BlockPos feet = helper.blockPosition();
        int dx = targetPos.getX() - feet.getX();
        int dz = targetPos.getZ() - feet.getZ();
        Direction dir = Math.abs(dx) >= Math.abs(dz)
                ? (dx >= 0 ? Direction.EAST : Direction.WEST)
                : (dz >= 0 ? Direction.SOUTH : Direction.NORTH);
        BlockPos next = feet.relative(dir);
        breakForTunnel(helper, level, next);
        if (helper.blockPosition().distSqr(next) > 1.0) {
            if (!EmilyNav.moveTo(helper, next, 1.0, 1.0)) {
                helper.getMoveControl().setWantedPosition(next.getX() + 0.5, next.getY(), next.getZ() + 0.5, 1.0);
            }
        }
    }

    // ------------------------------------------------------------------------------------------------ stairs

    private Status digStairs(HelperEntity helper, EmilyAgent agent, ServerLevel level) {
        BlockPos feet = helper.blockPosition();
        boolean down = feet.getY() > targetY;
        Direction dir = tunnelDir == null ? helper.getDirection() : tunnelDir;
        if (helper.getNavigation().isDone() && helper.getRandom().nextInt(40) == 0) {
            dir = dir.getClockWise();
        }
        BlockPos next = feet.relative(dir);
        BlockPos step = down ? next : next.above();
        if (!safeToDig(level, step, agent.ownerId())) {
            tunnelDir = dir.getClockWise();
            return Status.RUNNING;
        }
        breakForTunnel(helper, level, step);
        if (down) {
            BlockPos below = next.below();
            if (!level.getBlockState(below).isAir() && level.getBlockState(below).getDestroySpeed(level, below) <= 30) {
                EmilyBlocks.breakBlock(helper, level, below, helper.getMainHandItem());
            }
        }
        helper.getLookControl().setLookAt(step.getX() + 0.5, step.getY() + 0.5, step.getZ() + 0.5, 40.0F, 40.0F);
        if (!EmilyNav.moveTo(helper, step, 1.0, 1.0)) {
            helper.getMoveControl().setWantedPosition(step.getX() + 0.5, step.getY(), step.getZ() + 0.5, 1.0);
            if (down) helper.setDeltaMovement(helper.getDeltaMovement().x, -0.15, helper.getDeltaMovement().z);
        }
        if (helper.tickCount % 30 == 0 && helper.hasItem(Items.TORCH)) {
            EmilyBlocks.placeTorch(helper, level, helper.blockPosition());
        }
        return Status.RUNNING;
    }

    // ------------------------------------------------------------------------------------------------ tunnel

    private Status digTunnel(HelperEntity helper, EmilyAgent agent, ServerLevel level) {
        if (tunnelDir == null) tunnelDir = helper.getDirection();
        if (branchPhase) return digBranch(helper, agent, level);

        BlockPos feet = helper.blockPosition();
        BlockPos next = feet.relative(tunnelDir);
        if (!safeToDig(level, next, agent.ownerId())) {
            tunnelDir = tunnelDir.getClockWise();
            if (tunnelLength == 0) return Status.RUNNING;
            return Status.FAILED;
        }

        breakForTunnel(helper, level, next);
        helper.getLookControl().setLookAt(next.getX() + 0.5, next.getY() + 0.5, next.getZ() + 0.5, 40.0F, 40.0F);
        if (!EmilyNav.moveTo(helper, next, 1.0, 1.0)) {
            helper.getMoveControl().setWantedPosition(next.getX() + 0.5, next.getY(), next.getZ() + 0.5, 1.0);
        }
        tunnelLength++;
        if (tunnelLength % 8 == 0 && helper.hasItem(Items.TORCH)) {
            EmilyBlocks.placeTorch(helper, level, helper.blockPosition());
        }

        // Cut a side branch every few blocks, then come back to the main tunnel.
        if (branchCooldown > 0) branchCooldown--;
        if (branchCooldown <= 0 && tunnelLength % 6 == 0) {
            branchPhase = true;
            branchDepth = 0;
            branchOrigin = feet;
            branchCooldown = 14;
        }

        // Occasionally scan the tunnel walls for ores the scan radius may have missed.
        if (tunnelLength % 4 == 0) {
            BlockPos ore = EmilyBlocks.findTarget(level, helper, helper.blockPosition(), target, 8, 5, true, agent.ownerId());
            if (ore != null) currentOre = ore;
        }
        return Status.RUNNING;
    }

    private Status digBranch(HelperEntity helper, EmilyAgent agent, ServerLevel level) {
        if (branchOrigin == null) {
            branchPhase = false;
            return Status.RUNNING;
        }
        Direction side = tunnelDir == null ? helper.getDirection() : tunnelDir.getClockWise();
        BlockPos next = helper.blockPosition().relative(side);
        if (branchDepth >= 3 || !safeToDig(level, next, agent.ownerId())) {
            branchPhase = false;
            if (!EmilyNav.moveTo(helper, branchOrigin, 1.0, 2.0)) {
                // walk back to the main tunnel
                helper.getNavigation().moveTo(branchOrigin.getX() + 0.5, branchOrigin.getY(), branchOrigin.getZ() + 0.5, 1.0);
            }
            return Status.RUNNING;
        }
        breakForTunnel(helper, level, next);
        if (!EmilyNav.moveTo(helper, next, 1.0, 1.0)) {
            helper.getMoveControl().setWantedPosition(next.getX() + 0.5, next.getY(), next.getZ() + 0.5, 1.0);
        }
        branchDepth++;
        BlockPos ore = EmilyBlocks.findTarget(level, helper, helper.blockPosition(), target, 6, 4, true, agent.ownerId());
        if (ore != null) currentOre = ore;
        return Status.RUNNING;
    }

    private void breakForTunnel(HelperEntity helper, ServerLevel level, BlockPos feet) {
        BlockPos head = feet.above();
        for (BlockPos pos : new BlockPos[]{head, feet}) {
            BlockState state = level.getBlockState(pos);
            if (state.isAir()) continue;
            if (state.getDestroySpeed(level, pos) > 60 && !target.contains("obsidian")) continue;
            if (!EmilyBlocks.equipTool(helper, state)) continue;
            EmilyBlocks.breakBlock(helper, level, pos, helper.getMainHandItem());
        }
    }

    private boolean safeToDig(ServerLevel level, BlockPos feet, @Nullable java.util.UUID owner) {
        if (EmilyBlocks.isProtected(level, feet, owner, 10)) return false;
        BlockState floor = level.getBlockState(feet.below());
        BlockState at = level.getBlockState(feet);
        BlockState head = level.getBlockState(feet.above());
        if (!at.getFluidState().isEmpty() || !head.getFluidState().isEmpty()) return false;
        if (at.getBlock() == Blocks.LAVA || head.getBlock() == Blocks.LAVA) return false;
        if (at.getBlock() == Blocks.BEDROCK || head.getBlock() == Blocks.BEDROCK) return false;
        if (!target.contains("obsidian")
                && (at.getDestroySpeed(level, feet) > 60 || head.getDestroySpeed(level, feet.above()) > 60)) return false;
        return floor.blocksMotion() || at.getFluidState().isEmpty();
    }
}
