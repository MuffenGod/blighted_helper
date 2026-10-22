package com.blighteddreams.helper.agent;

import com.blighteddreams.helper.HelperEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;

/** Movement, safe-landing and teleport helpers for the agent. All of this runs on the server thread. */
public final class EmilyNav {
    private EmilyNav() {
    }

    public static double distSqr(HelperEntity helper, BlockPos pos) {
        return helper.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
    }

    public static double distSqr(HelperEntity helper, double x, double y, double z) {
        return helper.distanceToSqr(x, y, z);
    }

    public static boolean isNear(HelperEntity helper, BlockPos pos, double radius) {
        return distSqr(helper, pos) <= radius * radius;
    }

    public static boolean isNear(HelperEntity helper, Entity target, double radius) {
        return helper.distanceToSqr(target) <= radius * radius;
    }

    public static boolean inReach(HelperEntity helper, BlockPos pos, double reach) {
        return helper.getEyePosition().distanceToSqr(Vec3.atCenterOf(pos)) <= reach * reach;
    }

    /** Walks toward a block, repathing only when needed. Returns true once she is within {@code radius}. */
    public static boolean moveTo(HelperEntity helper, BlockPos pos, double speed, double radius) {
        if (isNear(helper, pos, radius)) {
            helper.getNavigation().stop();
            return true;
        }
        if (helper.getNavigation().isDone() || helper.tickCount % 20 == 0) {
            helper.getNavigation().moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, speed);
        }
        return false;
    }

    public static boolean moveTo(HelperEntity helper, Entity target, double speed, double radius) {
        if (helper.distanceToSqr(target) <= radius * radius) {
            helper.getNavigation().stop();
            return true;
        }
        if (helper.getNavigation().isDone() || helper.tickCount % 20 == 0) {
            helper.getNavigation().moveTo(target, speed);
        }
        return false;
    }

    /** A short path probe, useful for deciding whether a task can walk or must dig. */
    public static boolean canPathTo(HelperEntity helper, BlockPos pos) {
        return helper.getNavigation().createPath(pos, 1) != null;
    }

    public static void lookAt(HelperEntity helper, BlockPos pos) {
        helper.getLookControl().setLookAt(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 40.0F, 40.0F);
    }

    public static void lookAt(HelperEntity helper, Entity target) {
        helper.getLookControl().setLookAt(target, 40.0F, 40.0F);
    }

    public static void stop(HelperEntity helper) {
        helper.getNavigation().stop();
        helper.setDeltaMovement(0, helper.getDeltaMovement().y, 0);
    }

    /**
     * Puts her on solid ground near {@code around}. Searches a small area and returns the feet position actually used.
     * Never returns a spot inside a block, in lava, or in the void.
     */
    @Nullable
    public static BlockPos findSafeSpot(Level level, Entity entity, BlockPos around, int radius) {
        for (int r = 0; r <= radius; r++) {
            for (int dy = 3; dy >= -5; dy--) {
                for (int dx = -r; dx <= r; dx++) {
                    for (int dz = -r; dz <= r; dz++) {
                        if (r > 0 && Math.abs(dx) != r && Math.abs(dz) != r) continue;
                        BlockPos feet = around.offset(dx, dy, dz);
                        if (!isSafeStand(level, entity, feet)) continue;
                        return feet;
                    }
                }
            }
        }
        return null;
    }

    public static boolean isSafeStand(Level level, Entity entity, BlockPos feet) {
        BlockPos floor = feet.below();
        BlockPos head = feet.above();
        BlockState floorState = level.getBlockState(floor);
        if (!floorState.blocksMotion()) return false;
        if (!level.getBlockState(feet).getFluidState().isEmpty()) return false;
        if (level.getBlockState(feet).blocksMotion() || level.getBlockState(head).blocksMotion()) return false;
        if (!level.getBlockState(feet).getFluidState().isEmpty() || !level.getBlockState(head).getFluidState().isEmpty()) return false;
        if (entity == null) return true;
        double dx = feet.getX() + 0.5 - entity.getX();
        double dy = feet.getY() - entity.getY();
        double dz = feet.getZ() + 0.5 - entity.getZ();
        AABB moved = entity.getBoundingBox().move(dx, dy, dz);
        return level.noCollision(entity, moved);
    }

    /** Teleports her to a safe spot near the target, in the same dimension. */
    public static boolean teleportNear(HelperEntity helper, BlockPos target, int radius) {
        if (!(helper.level() instanceof ServerLevel level)) return false;
        BlockPos safe = findSafeSpot(level, helper, target, radius);
        if (safe == null) safe = target;
        helper.teleportTo(safe.getX() + 0.5, safe.getY(), safe.getZ() + 0.5);
        helper.getNavigation().stop();
        helper.setDeltaMovement(0, 0, 0);
        helper.fallDistance = 0.0F;
        level.sendParticles(ParticleTypes.PORTAL, helper.getX(), helper.getY(0.6), helper.getZ(),
                28, 0.35, 0.6, 0.35, 0.12);
        level.playSound(null, helper.getX(), helper.getY(), helper.getZ(),
                SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.7F, 1.15F);
        return true;
    }

    /** Teleports her to another dimension, keeping owner, inventory, level and memory. */
    public static boolean teleportTo(HelperEntity helper, ServerLevel targetLevel, BlockPos target) {
        BlockPos safe = findSafeSpot(targetLevel, helper, target, 8);
        BlockPos landing = safe == null ? target : safe;
        if (helper.level() == targetLevel) {
            return teleportNear(helper, landing, 3);
        }
        ServerLevel oldLevel = (ServerLevel) helper.level();
        oldLevel.sendParticles(ParticleTypes.REVERSE_PORTAL, helper.getX(), helper.getY(0.6), helper.getZ(),
                30, 0.3, 0.6, 0.3, 0.1);
        boolean moved = com.blighteddreams.helper.HelperManager.teleportTo(helper, targetLevel, landing);
        if (moved) {
            targetLevel.sendParticles(ParticleTypes.PORTAL, landing.getX() + 0.5, landing.getY() + 0.5, landing.getZ() + 0.5,
                    30, 0.3, 0.6, 0.3, 0.1);
            targetLevel.playSound(null, landing.getX(), landing.getY(), landing.getZ(),
                    SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.8F, 1.0F);
        }
        return moved;
    }
}
