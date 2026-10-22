package com.blighteddreams.helper.agent;

import com.blighteddreams.helper.HelperEntity;
import com.blighteddreams.helper.HelperProgression;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.Items;

import javax.annotation.Nullable;

/**
 * Utility planner for Emily's free time. It looks at the player's actual progression stage, her own supplies and the
 * time of day, then picks one job that moves the pack forward. It deliberately returns null sometimes: a companion who
 * constantly announces a new task is exhausting.
 */
public final class EmilyPlanner {
    private EmilyPlanner() {
    }

    @Nullable
    public static EmilyTask choose(HelperEntity helper, EmilyAgent agent) {
        ServerPlayer owner = helper.getOwner();
        if (owner == null || helper.isDowned() || helper.isRebellious()) return null;
        if (helper.getTarget() != null && helper.getTarget().isAlive()) return null;
        if (owner.getLastHurtByMob() != null && owner.getLastHurtByMob().isAlive()
                && helper.distanceToSqr(owner.getLastHurtByMob()) < 256.0) return null;

        if (owner.level() != helper.level()) return new EmilyTasks.ReturnTask(owner);
        if (agent.autonomy() == EmilyAgent.Autonomy.COMPANION && helper.distanceToSqr(owner) > 24.0 * 24.0) {
            return new EmilyTasks.ReturnTask(owner);
        }
        if (owner.getHealth() < owner.getMaxHealth() * 0.45F && helper.distanceToSqr(owner) > 32.0 * 32.0) {
            return new EmilyTasks.ReturnTask(owner);
        }

        if (helper.getHealth() < helper.getMaxHealth() * 0.45F) {
            if (EmilyGear.eat(helper)) return new EmilyTasks.IdleTask(60);
        }

        if (EmilyBlocks.freeBagSlots(helper) <= 2) {
            return new EmilyTasks.DepositTask(32);
        }

        boolean hasPick = helper.hasPickaxe();
        if (!hasPick) {
            boolean hasWood = EmilyBlocks.countBag(helper, s -> s.is(ItemTags.LOGS)) > 0;
            if (!hasWood) return new EmilyGatherTask("logs", 24);
            return new EmilyCrafting.CraftTask("wooden_pickaxe", 1);
        }

        // Keep her own gear moving without wasting the player's good materials.
        int iron = EmilyBlocks.countBag(helper, s -> s.is(Items.IRON_INGOT));
        int diamonds = EmilyBlocks.countBag(helper, s -> s.is(Items.DIAMOND));
        int obsidian = EmilyBlocks.countBag(helper, s -> s.is(Items.OBSIDIAN));
        int coal = EmilyBlocks.countBag(helper, s -> s.is(Items.COAL) || s.is(Items.CHARCOAL));
        int logs = EmilyBlocks.countBag(helper, s -> s.is(ItemTags.LOGS));
        int rawIron = EmilyBlocks.countBag(helper, s -> s.is(Items.RAW_IRON));
        int sticks = EmilyBlocks.countBag(helper, s -> s.is(Items.STICK));

        // Ore drops are raw iron now. Without this step she would mine forever and never hold an ingot.
        if (iron < 8 && rawIron >= 3) {
            int cobble = EmilyBlocks.countBag(helper, s -> s.is(Items.COBBLESTONE));
            if (!helper.hasItem(Items.FURNACE) && cobble < 8) return new EmilyMineTask("stone", 8);
            if (coal <= 0 && logs <= 0 && sticks <= 0 && planks(helper) <= 0) return new EmilyMineTask("coal", 8);
            return new EmilyCrafting.SmeltTask("raw iron", Math.min(8, rawIron));
        }

        if (iron >= 3 && !helper.hasItem(Items.IRON_PICKAXE) && !helper.hasItem(Items.DIAMOND_PICKAXE)
                && EmilyBlocks.countBag(helper, s -> s.is(Items.STICK)) + planks(helper) / 2 >= 2) {
            return new EmilyCrafting.CraftTask("iron_pickaxe", 1);
        }
        // Obsidian, ancient debris and the good ores need a diamond pickaxe; without this she would mine forever and
        // never get a single drop.
        if (diamonds >= 3 && !helper.hasItem(Items.DIAMOND_PICKAXE)
                && EmilyBlocks.countBag(helper, s -> s.is(Items.STICK)) + planks(helper) / 2 >= 2) {
            return new EmilyCrafting.CraftTask("diamond_pickaxe", 1);
        }
        if (EmilyBlocks.countBag(helper, s -> s.is(Items.STICK)) < 2 && planks(helper) < 4) {
            if (logs > 0) return new EmilyCrafting.CraftTask("stick", 2);
            return new EmilyGatherTask("logs", 8);
        }

        int stage = stageOf(HelperProgression.analyze(owner).stageName());
        if (stage <= 1) {
            if (logs < 16) return new EmilyGatherTask("logs", 24);
            if (iron < 8) return new EmilyMineTask("iron", 12);
            if (coal < 8) return new EmilyMineTask("coal", 12);
            if (!hasFood(helper)) return new EmilyGatherTask("mature crops", 16);
        } else if (stage == 2) {
            if (diamonds < 3) return new EmilyMineTask("diamond", 6);
            if (iron < 16) return new EmilyMineTask("iron", 16);
            if (obsidian < 10) return new EmilyMineTask("obsidian", 10);
        } else if (stage == 3) {
            if (obsidian < 10) return new EmilyMineTask("obsidian", 10);
            if (!helper.hasItem(Items.FLINT_AND_STEEL)) {
                if (EmilyBlocks.countBag(helper, s -> s.is(Items.FLINT)) < 1) {
                    return new EmilyMineTask("gravel", 12);
                }
                return new EmilyCrafting.CraftTask("flint_and_steel", 1);
            }
            if (!hasNearbyPortal(helper)) return new EmilyTasks.PortalTask();
        } else if (stage == 4) {
            if (helper.level().dimension().location().getPath().contains("nether")) {
                return new EmilyTasks.StructureTask("fortress");
            }
            EmilyMemory.Waypoint portal = agent.ownerId() == null ? null
                    : agent.memory().find(agent.ownerId(), "nether_portal");
            if (portal != null) {
                return new EmilyTasks.TravelTask(portal.pos(), dimensionKey(portal.dimension()), "the nether portal", true);
            }
            if (obsidian >= 10) return new EmilyTasks.PortalTask();
            return new EmilyMineTask("obsidian", 10);
        } else if (stage == 5) {
            return new EmilyTasks.StructureTask("stronghold");
        } else if (stage == 6) {
            return new EmilyTasks.StructureTask("stronghold");
        } else {
            if (helper.level().dimension().location().getPath().equals("the_end")) {
                return new EmilyTasks.StructureTask("end_city");
            }
            return new EmilyTasks.StructureTask("stronghold");
        }

        // Nothing urgent. Pick a calm hobby, but not every single time.
        int roll = helper.getRandom().nextInt(100);
        if (roll < 30) return null;
        if (helper.level().isNight() || helper.level().isThundering()) {
            return new EmilyTasks.GuardTask(1200);
        }
        if (roll < 55) {
            if (EmilyBlocks.findContainer((ServerLevel) helper.level(), helper.blockPosition(), 20, false, agent.ownerId()) != null) {
                return new EmilyTasks.LootTask(20, false);
            }
        }
        if (roll < 75) return new EmilyTasks.ExploreTask("", 96);
        return null;
    }

    private static int planks(HelperEntity helper) {
        return EmilyBlocks.countBag(helper, s -> s.is(ItemTags.PLANKS));
    }

    private static boolean hasFood(HelperEntity helper) {
        return EmilyBlocks.countBag(helper, s -> s.has(net.minecraft.core.component.DataComponents.FOOD)) > 0;
    }

    private static boolean hasNearbyPortal(HelperEntity helper) {
        if (!(helper.level() instanceof ServerLevel level)) return false;
        for (net.minecraft.core.BlockPos pos : net.minecraft.core.BlockPos.betweenClosed(
                helper.blockPosition().offset(-16, -8, -16), helper.blockPosition().offset(16, 8, 16))) {
            if (level.getBlockState(pos).is(net.minecraft.world.level.block.Blocks.NETHER_PORTAL)) return true;
        }
        return false;
    }

    private static int stageOf(String stageName) {
        if (stageName == null) return 1;
        for (int i = 0; i < stageName.length(); i++) {
            char c = stageName.charAt(i);
            if (Character.isDigit(c)) {
                try {
                    return Integer.parseInt(String.valueOf(c));
                } catch (NumberFormatException ignored) {
                    return 1;
                }
            }
        }
        return 1;
    }

    private static net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimensionKey(String dimension) {
        var rl = net.minecraft.resources.ResourceLocation.tryParse(dimension);
        return rl == null ? net.minecraft.world.level.Level.OVERWORLD
                : net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION, rl);
    }

}
