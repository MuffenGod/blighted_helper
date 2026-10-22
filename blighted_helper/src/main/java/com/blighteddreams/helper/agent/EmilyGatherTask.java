package com.blighteddreams.helper.agent;

import com.blighteddreams.helper.HelperEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Locale;

/** Chops trees, harvests crops and picks up what falls. */
public class EmilyGatherTask implements EmilyTask {
    private final String target;
    private final int desired;
    private int gathered;
    private int elapsed;
    private int scanCooldown;
    private BlockPos current;
    private BlockPos breakerPos;
    private int breakTicks;
    private int breakTotal;
    private boolean failed;

    public EmilyGatherTask(String target, int amount) {
        String t = target == null || target.isBlank() ? "logs" : target.trim().toLowerCase(Locale.ROOT);
        if (t.equals("wood") || t.equals("tree") || t.equals("trees") || t.equals("timber")) t = "logs";
        if (t.equals("food") || t.equals("farm") || t.equals("harvest")) t = "mature crops";
        this.target = t;
        this.desired = amount <= 0 ? (t.equals("logs") ? 32 : 16) : Math.min(256, amount);
    }

    @Override
    public String name() {
        return "gather";
    }

    @Override
    public String label() {
        return "gathering " + target + " (" + gathered + "/" + desired + ")";
    }

    @Override
    public void start(HelperEntity helper, EmilyAgent agent) {
        agent.say(target.equals("logs")
                ? "Getting some wood. I'll keep an eye out for anything interesting."
                : "Harvesting " + target + " now.", "calm");
    }

    @Override
    public Status tick(HelperEntity helper, EmilyAgent agent) {
        if (failed) return Status.FAILED;
        if (!(helper.level() instanceof ServerLevel level)) return Status.FAILED;
        elapsed++;
        if (elapsed > 20 * 60 * 6) return Status.FAILED;
        if (EmilyBlocks.freeBagSlots(helper) <= 1) {
            agent.say("My bag is full. I'm done gathering for now.", "calm");
            return Status.DONE;
        }

        if (helper.tickCount % 20 == 0) collectItems(helper, level);

        if (current != null) {
            BlockState state = level.getBlockState(current);
            if (state.isAir() || !matchesState(state)) {
                current = null;
                breakerPos = null;
                return Status.RUNNING;
            }
            double dist = helper.getEyePosition().distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(current));
            if (dist > 20.0) {
                if (!EmilyNav.moveTo(helper, current, 1.2, 2.0)) {
                    if (elapsed > 200) {
                        current = null;
                    }
                }
                return Status.RUNNING;
            }
            if (!EmilyBlocks.equipTool(helper, state)) {
                // Crops and flowers do not need a tool.
                if (state.getDestroySpeed(level, current) > 2.0F) {
                    agent.say("I need a better tool for this.", "worried");
                    failed = true;
                    return Status.FAILED;
                }
            }
            EmilyNav.stop(helper);
            EmilyNav.lookAt(helper, current);
            if (breakerPos == null || !breakerPos.equals(current)) {
                breakerPos = current;
                breakTicks = 0;
                breakTotal = Math.max(3, EmilyBlocks.miningTicks(level, current, state, helper.getMainHandItem()));
            }
            breakTicks++;
            helper.triggerSwingAnim();
            if (breakTicks % 4 == 0) {
                level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state),
                        current.getX() + 0.5, current.getY() + 0.5, current.getZ() + 0.5,
                        4, 0.2, 0.2, 0.2, 0.03);
                helper.playSound(state.getSoundType().getHitSound(), 0.5F, 0.9F);
            }
            if (breakTicks >= breakTotal) {
                EmilyBlocks.breakBlock(helper, level, current, helper.getMainHandItem());
                gathered++;
                current = null;
                breakerPos = null;
                breakTicks = 0;
                if (gathered >= desired) {
                    agent.say("Got " + gathered + " " + target + ". Taking them home.", "cheerful");
                    return Status.DONE;
                }
            }
            return Status.RUNNING;
        }

        if (--scanCooldown <= 0) {
            scanCooldown = 10;
            current = find(level, helper, agent);
            if (current == null) {
                if (gathered >= desired) return Status.DONE;
                if (elapsed > 80) {
                    agent.say("I can't find any more " + target + " nearby.", "worried");
                    return gathered > 0 ? Status.DONE : Status.FAILED;
                }
            }
        }
        return Status.RUNNING;
    }

    private BlockPos find(ServerLevel level, HelperEntity helper, EmilyAgent agent) {
        BlockPos center = helper.blockPosition();
        int radius = target.equals("logs") ? 24 : 16;
        int vertical = target.equals("logs") ? 16 : 3;
        BlockPos found = EmilyBlocks.findTarget(level, helper, center, target, radius, vertical, false, agent.ownerId());
        if (found != null) return found;
        // A tree trunk may be hidden behind leaves; look for any matching block as a fallback.
        return EmilyBlocks.findAny(level, center, target, radius, vertical);
    }

    private boolean matchesState(BlockState state) {
        if (target.equals("mature crops")) {
            return state.is(net.minecraft.tags.BlockTags.CROPS) && state.getBlock() instanceof CropBlock crop && crop.isMaxAge(state);
        }
        return EmilyBlocks.matches(target, state);
    }

    private void collectItems(HelperEntity helper, ServerLevel level) {
        for (net.minecraft.world.entity.item.ItemEntity item : level.getEntitiesOfClass(
                net.minecraft.world.entity.item.ItemEntity.class,
                new net.minecraft.world.phys.AABB(helper.blockPosition()).inflate(8.0),
                e -> e.isAlive() && !e.getItem().isEmpty())) {
            if (helper.distanceToSqr(item) <= 9.0) {
                ItemStack stack = item.getItem().copy();
                helper.addToBagOrDrop(stack);
                item.discard();
            }
        }
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
