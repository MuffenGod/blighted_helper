package com.blighteddreams.helper.agent;

import com.blighteddreams.helper.HelperEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Predicate;

/** Block classification, target scanning, tool choice and careful block breaking. */
public final class EmilyBlocks {
    private EmilyBlocks() {
    }

    // ------------------------------------------------------------------------------------------------ classification

    public static boolean isOre(BlockState state) {
        String id = BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
        return id.endsWith("_ore") || id.equals("ancient_debris") || id.equals("nether_quartz_ore");
    }

    public static boolean matches(String rawTarget, BlockState state) {
        String t = rawTarget == null ? "" : rawTarget.trim().toLowerCase(Locale.ROOT).replace('_', ' ');
        if (t.isEmpty()) return false;
        Block block = state.getBlock();
        String id = BuiltInRegistries.BLOCK.getKey(block).getPath();

        switch (t) {
            case "any ore", "ores", "ore", "any_ore" -> {
                return isOre(state);
            }
            case "wood", "wooden", "log", "logs", "tree", "trees", "timber" -> {
                return state.is(BlockTags.LOGS);
            }
            case "crop", "crops", "food", "farm" -> {
                return state.is(BlockTags.CROPS);
            }
            case "mature crop", "mature crops", "harvest" -> {
                return state.is(BlockTags.CROPS) && state.getBlock() instanceof CropBlock crop && crop.isMaxAge(state);
            }
            case "stone", "cobblestone", "cobble" -> {
                return state.is(BlockTags.BASE_STONE_OVERWORLD) || state.is(Blocks.COBBLESTONE) || state.is(Blocks.DEEPSLATE);
            }
            case "obsidian" -> {
                return state.is(Blocks.OBSIDIAN) || state.is(Blocks.CRYING_OBSIDIAN);
            }
            case "ancient debris", "debris", "netherite" -> {
                return state.is(Blocks.ANCIENT_DEBRIS);
            }
            case "sand" -> {
                return state.is(BlockTags.SAND);
            }
            case "gravel" -> {
                return state.is(Blocks.GRAVEL);
            }
            case "clay" -> {
                return state.is(Blocks.CLAY);
            }
            case "dirt" -> {
                return state.is(BlockTags.DIRT) || state.is(Blocks.GRASS_BLOCK);
            }
            case "leaves" -> {
                return state.is(BlockTags.LEAVES);
            }
            case "chest", "barrel", "container" -> {
                return id.equals("chest") || id.equals("trapped_chest") || id.equals("barrel")
                        || id.equals("shulker_box") || id.endsWith("_shulker_box");
            }
            case "iron" -> {
                return id.equals("iron_ore") || id.equals("deepslate_iron_ore");
            }
            case "gold" -> {
                return id.equals("gold_ore") || id.equals("deepslate_gold_ore") || id.equals("nether_gold_ore");
            }
            case "diamond" -> {
                return id.equals("diamond_ore") || id.equals("deepslate_diamond_ore");
            }
            case "coal" -> {
                return id.equals("coal_ore") || id.equals("deepslate_coal_ore");
            }
            case "copper" -> {
                return id.equals("copper_ore") || id.equals("deepslate_copper_ore");
            }
            case "redstone" -> {
                return id.equals("redstone_ore") || id.equals("deepslate_redstone_ore");
            }
            case "lapis" -> {
                return id.equals("lapis_ore") || id.equals("deepslate_lapis_ore");
            }
            case "emerald" -> {
                return id.equals("emerald_ore") || id.equals("deepslate_emerald_ore");
            }
            case "quartz" -> {
                return id.equals("nether_quartz_ore");
            }
            default -> {
                // generic: "deepslate_diamond_ore", "oak_log", "wheat", ...
                if (id.equals(t.replace(' ', '_'))) return true;
                if (id.contains(t.replace(' ', '_'))) return true;
                String name = block.getName().getString().toLowerCase(Locale.ROOT);
                return name.contains(t);
            }
        }
    }

    /** True when at least one neighbour is air or a fluid, so she could actually see the block while mining. */
    public static boolean isExposed(Level level, BlockPos pos) {
        for (Direction dir : Direction.values()) {
            BlockPos n = pos.relative(dir);
            BlockState state = level.getBlockState(n);
            FluidState fluid = state.getFluidState();
            if (state.isAir() || !fluid.isEmpty()) return true;
            if (!state.isSolidRender(level, n)) return true;
        }
        return false;
    }

    public static boolean isFluidNear(Level level, BlockPos pos) {
        for (Direction dir : Direction.values()) {
            if (!level.getBlockState(pos.relative(dir)).getFluidState().isEmpty()) return true;
        }
        return false;
    }

    // ------------------------------------------------------------------------------------------------ scanning

    @Nullable
    public static BlockPos findTarget(ServerLevel level, HelperEntity helper, BlockPos center, String target,
                                      int radius, int vertical, boolean exposedOnly, UUID owner) {
        List<BlockPos> hits = new ArrayList<>();
        BlockPos min = center.offset(-radius, -vertical, -radius);
        BlockPos max = center.offset(radius, vertical, radius);
        for (BlockPos pos : BlockPos.betweenClosed(min, max)) {
            BlockState state = level.getBlockState(pos);
            if (!matches(target, state)) continue;
            if (state.isAir()) continue;
            if (exposedOnly && !isExposed(level, pos)) continue;
            if (isProtected(level, pos, owner, 10)) continue;
            if (isFluidNear(level, pos)) continue;
            hits.add(pos.immutable());
        }
        if (hits.isEmpty()) return null;
        hits.sort(Comparator.comparingDouble(p -> p.distSqr(center)));
        // prefer the closest few, but do not pick a block she cannot stand near
        for (int i = 0; i < Math.min(hits.size(), 12); i++) {
            BlockPos p = hits.get(i);
            if (EmilyNav.isNear(helper, p, 24) || EmilyNav.canPathTo(helper, p)) return p;
        }
        return hits.get(0);
    }

    /** Finds the nearest block of a target, exposed or not (used by loot/containers and branch mining). */
    @Nullable
    public static BlockPos findAny(ServerLevel level, BlockPos center, String target, int radius, int vertical) {
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-radius, -vertical, -radius), center.offset(radius, vertical, radius))) {
            if (matches(target, level.getBlockState(pos))) return pos.immutable();
        }
        return null;
    }

    @Nullable
    public static BlockPos findContainer(ServerLevel level, BlockPos center, int radius, boolean allowNearHome, UUID owner) {
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-radius, -4, -radius), center.offset(radius, 4, radius))) {
            BlockEntity be = level.getBlockEntity(pos);
            if (!(be instanceof Container)) continue;
            if (!allowNearHome && isProtected(level, pos, owner, 28)) continue;
            double d = pos.distSqr(center);
            if (d < bestDist) {
                bestDist = d;
                best = pos.immutable();
            }
        }
        return best;
    }

    @Nullable
    public static ItemEntity findItem(ServerLevel level, BlockPos center, int radius) {
        List<ItemEntity> items = level.getEntitiesOfClass(ItemEntity.class,
                new net.minecraft.world.phys.AABB(center).inflate(radius),
                e -> e.isAlive() && !e.getItem().isEmpty());
        return items.stream().min(Comparator.comparingDouble(e -> e.distanceToSqr(
                center.getX() + 0.5, center.getY() + 0.5, center.getZ() + 0.5))).orElse(null);
    }

    /** Blocks near home/base are left alone unless the player explicitly asked for them. */
    public static boolean isProtected(ServerLevel level, BlockPos pos, @Nullable UUID owner, int radius) {
        if (owner == null) return false;
        EmilyMemory memory = EmilyMemory.get(level);
        String dim = level.dimension().location().toString();
        for (String kind : new String[]{"home", "base"}) {
            EmilyMemory.Waypoint w = memory.nearest(owner, dim, pos, kind);
            if (w != null && w.pos().distSqr(pos) <= (double) radius * radius) return true;
        }
        return false;
    }

    // ------------------------------------------------------------------------------------------------ tools

    /** -2 = current main hand, 0..n = bag slot, -1 = no suitable tool. */
    public static int bestTool(HelperEntity helper, BlockState state) {
        ItemStack main = helper.getMainHandItem();
        float bestSpeed = canHarvest(main, state) ? main.getDestroySpeed(state) : -1.0F;
        int bestSlot = bestSpeed > 1.0F ? -2 : -1;
        var bag = helper.bag();
        for (int i = 0; i < bag.getSlots(); i++) {
            ItemStack stack = bag.getStackInSlot(i);
            if (stack.isEmpty()) continue;
            if (!canHarvest(stack, state)) continue;
            float speed = stack.getDestroySpeed(state);
            if (speed > bestSpeed) {
                bestSpeed = speed;
                bestSlot = i;
            }
        }
        return bestSlot;
    }

    public static boolean canHarvest(ItemStack stack, BlockState state) {
        return !stack.isEmpty() && (stack.isCorrectToolForDrops(state) || stack.getDestroySpeed(state) > 1.0F);
    }

    /** Makes sure the best tool for this block is in her main hand. */
    public static boolean equipTool(HelperEntity helper, BlockState state) {
        int slot = bestTool(helper, state);
        if (slot == -2) return true;
        if (slot < 0) {
            // Soft blocks (dirt, sand, gravel, crops) can be broken by hand or with whatever she is holding.
            float hardness = state.getDestroySpeed(helper.level(), helper.blockPosition());
            return hardness >= 0.0F && hardness <= 1.5F;
        }
        var bag = helper.bag();
        ItemStack tool = bag.extractItem(slot, 1, false);
        if (tool.isEmpty()) return false;
        ItemStack old = helper.getMainHandItem();
        helper.setItemSlot(EquipmentSlot.MAINHAND, tool);
        if (!old.isEmpty()) {
            ItemStack rest = old.copy();
            for (int i = 0; i < bag.getSlots() && !rest.isEmpty(); i++) {
                rest = bag.insertItem(i, rest, false);
            }
            if (!rest.isEmpty()) helper.spawnAtLocation(rest);
        }
        return true;
    }

    /** Approximate ticks to break a block with this tool, following the vanilla progress formula. */
    public static int miningTicks(Level level, BlockPos pos, BlockState state, ItemStack tool) {
        float hardness = state.getDestroySpeed(level, pos);
        if (hardness < 0) return -1;
        if (hardness == 0) return 1;
        float speed = Math.max(1.0F, tool.getDestroySpeed(state));
        if (tool.isEnchanted()) speed *= 1.35F; // efficiency, as a sensible approximation
        boolean correct = tool.isCorrectToolForDrops(state);
        float ticks = hardness * (correct ? 30.0F : 100.0F) / speed;
        return Math.max(2, Math.min(240, Math.round(ticks)));
    }

    /** Breaks the block and puts its drops in her bag, damaging the tool. */
    public static boolean breakBlock(HelperEntity helper, ServerLevel level, BlockPos pos, ItemStack tool) {
        BlockState state = level.getBlockState(pos);
        if (state.isAir()) return true;
        BlockEntity be = level.getBlockEntity(pos);
        List<ItemStack> drops = Block.getDrops(state, level, pos, be, helper, tool);
        level.destroyBlock(pos, false, helper);
        for (ItemStack drop : drops) {
            helper.addToBagOrDrop(drop);
        }
        if (tool.isDamageableItem()) {
            tool.hurtAndBreak(1, helper, EquipmentSlot.MAINHAND);
        }
        level.playSound(null, pos, state.getSoundType().getBreakSound(), SoundSource.BLOCKS, 0.85F,
                0.9F + helper.getRandom().nextFloat() * 0.2F);
        return true;
    }

    // ------------------------------------------------------------------------------------------------ items

    public static boolean has(HelperEntity helper, Item item) {
        return helper.hasItem(item);
    }

    public static int count(HelperEntity helper, Predicate<ItemStack> predicate) {
        int count = 0;
        if (predicate.test(helper.getMainHandItem())) count += helper.getMainHandItem().getCount();
        if (predicate.test(helper.getOffhandItem())) count += helper.getOffhandItem().getCount();
        var bag = helper.bag();
        for (int i = 0; i < bag.getSlots(); i++) {
            ItemStack stack = bag.getStackInSlot(i);
            if (predicate.test(stack)) count += stack.getCount();
        }
        return count;
    }

    public static int count(HelperEntity helper, Item item) {
        return count(helper, s -> s.is(item));
    }

    /** Removes up to {@code amount} matching items, offhand and bag first, then main hand. */
    public static int consume(HelperEntity helper, Predicate<ItemStack> predicate, int amount) {
        int left = amount;
        ItemStack off = helper.getOffhandItem();
        if (left > 0 && predicate.test(off)) {
            int take = Math.min(left, off.getCount());
            off.shrink(take);
            left -= take;
            if (off.isEmpty()) helper.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
        }
        var bag = helper.bag();
        for (int i = 0; i < bag.getSlots() && left > 0; i++) {
            ItemStack stack = bag.getStackInSlot(i);
            if (!predicate.test(stack)) continue;
            int take = Math.min(left, stack.getCount());
            bag.extractItem(i, take, false);
            left -= take;
        }
        if (left > 0 && predicate.test(helper.getMainHandItem())) {
            int take = Math.min(left, helper.getMainHandItem().getCount());
            helper.getMainHandItem().shrink(take);
            left -= take;
        }
        return amount - left;
    }

    public static int consume(HelperEntity helper, Item item, int amount) {
        return consume(helper, s -> s.is(item), amount);
    }

    public static int countBag(HelperEntity helper, Predicate<ItemStack> predicate) {
        int count = 0;
        var bag = helper.bag();
        for (int i = 0; i < bag.getSlots(); i++) {
            ItemStack stack = bag.getStackInSlot(i);
            if (predicate.test(stack)) count += stack.getCount();
        }
        return count;
    }

    /** Bag-only consumption, so crafting never eats the tool in her hand. */
    public static int consumeBag(HelperEntity helper, Predicate<ItemStack> predicate, int amount) {
        // All-or-nothing: a recipe that silently eats three iron and then discovers it has no sticks is worse than
        // one that simply reports it cannot craft yet.
        if (countBag(helper, predicate) < amount) return 0;
        int left = amount;
        var bag = helper.bag();
        for (int i = 0; i < bag.getSlots() && left > 0; i++) {
            ItemStack stack = bag.getStackInSlot(i);
            if (!predicate.test(stack)) continue;
            int take = Math.min(left, stack.getCount());
            bag.extractItem(i, take, false);
            left -= take;
        }
        return amount - left;
    }

    public static ItemStack findStack(HelperEntity helper, Predicate<ItemStack> predicate) {
        if (predicate.test(helper.getOffhandItem())) return helper.getOffhandItem();
        var bag = helper.bag();
        for (int i = 0; i < bag.getSlots(); i++) {
            if (predicate.test(bag.getStackInSlot(i))) return bag.getStackInSlot(i);
        }
        if (predicate.test(helper.getMainHandItem())) return helper.getMainHandItem();
        return ItemStack.EMPTY;
    }

    public static int freeBagSlots(HelperEntity helper) {
        int free = 0;
        var bag = helper.bag();
        for (int i = 0; i < bag.getSlots(); i++) {
            ItemStack stack = bag.getStackInSlot(i);
            if (stack.isEmpty() || stack.getCount() < stack.getMaxStackSize()) free++;
        }
        return free;
    }

    // ------------------------------------------------------------------------------------------------ placing

    public static boolean place(HelperEntity helper, ServerLevel level, BlockPos pos, BlockState state, Item required) {
        if (!level.getBlockState(pos).canBeReplaced()) return false;
        if (!state.canSurvive(level, pos)) return false;
        if (consume(helper, required, 1) <= 0) return false;
        level.setBlock(pos, state, 3);
        level.playSound(null, pos, state.getSoundType().getPlaceSound(), SoundSource.BLOCKS, 0.9F, 1.0F);
        helper.triggerSwingAnim();
        return true;
    }

    public static boolean placeTorch(HelperEntity helper, ServerLevel level, BlockPos feet) {
        BlockPos pos = feet;
        if (level.getBlockState(pos).canBeReplaced() && level.getBlockState(pos.below()).isSolidRender(level, pos.below())) {
            if (consume(helper, Items.TORCH, 1) <= 0) return false;
            level.setBlock(pos, Blocks.TORCH.defaultBlockState(), 3);
            level.playSound(null, pos, net.minecraft.sounds.SoundEvents.WOOD_PLACE, SoundSource.BLOCKS, 0.8F, 1.0F);
            return true;
        }
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            BlockPos wall = feet.relative(dir);
            if (level.getBlockState(feet).canBeReplaced() && level.getBlockState(wall).isSolidRender(level, wall)) {
                BlockState torch = Blocks.WALL_TORCH.defaultBlockState().setValue(
                        net.minecraft.world.level.block.WallTorchBlock.FACING, dir.getOpposite());
                if (torch.canSurvive(level, feet)) {
                    if (consume(helper, Items.TORCH, 1) <= 0) return false;
                    level.setBlock(feet, torch, 3);
                    level.playSound(null, feet, net.minecraft.sounds.SoundEvents.WOOD_PLACE, SoundSource.BLOCKS, 0.8F, 1.0F);
                    return true;
                }
            }
        }
        return false;
    }

    /** Returns the block state of a floor torch position, or null. */
    @Nullable
    public static BlockPos torchSpot(ServerLevel level, BlockPos feet) {
        if (level.getBlockState(feet).canBeReplaced()) return feet.immutable();
        return null;
    }
}
