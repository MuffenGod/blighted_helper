package com.blighteddreams.helper.agent;

import com.blighteddreams.helper.HelperEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Predicate;

/** Small, real crafting and smelting: the vanilla recipe manager, the pack's recipes, and a furnace she actually uses. */
public final class EmilyCrafting {
    private EmilyCrafting() {
    }

    // ------------------------------------------------------------------------------------------------ crafting

    public static int craft(HelperEntity helper, String rawTarget, int amount) {
        if (!(helper.level() instanceof ServerLevel level)) return 0;
        Item target = resolveItem(rawTarget);
        if (target == null || target == Items.AIR) return 0;
        int wanted = Math.max(1, Math.min(128, amount));
        int made = 0;
        int attempts = 0;
        while (made < wanted && attempts++ < wanted * 3 + 8) {
            int n = craftOne(helper, level, target, 0, new HashSet<>());
            if (n <= 0) break;
            made += n;
        }
        if (made == 0 && targetPath(target).endsWith("_planks")) {
            for (Item candidate : BuiltInRegistries.ITEM) {
                if (!targetPath(candidate).endsWith("_planks")) continue;
                int n = craftOne(helper, level, candidate, 0, new HashSet<>());
                if (n > 0) {
                    made += n;
                    break;
                }
            }
        }
        if (made == 0) made = curated(helper, level, target, wanted);
        return made;
    }

    private static int craftOne(HelperEntity helper, ServerLevel level, Item target, int depth, Set<Item> visiting) {
        if (depth > 4 || target == Items.AIR) return 0;
        if (!visiting.add(target)) return 0;
        try {
            RecipeHolder<CraftingRecipe> best = null;
            List<Ingredient> bestIngredients = null;
            int bestMissing = Integer.MAX_VALUE;
            for (RecipeHolder<CraftingRecipe> holder : level.getRecipeManager().getAllRecipesFor(RecipeType.CRAFTING)) {
                CraftingRecipe recipe;
                try {
                    recipe = holder.value();
                    if (recipe.isSpecial()) continue;
                    ItemStack result = recipe.getResultItem(level.registryAccess());
                    if (!result.is(target)) continue;
                    List<Ingredient> ingredients = new ArrayList<>();
                    for (Ingredient ing : recipe.getIngredients()) {
                        if (!ing.isEmpty() && !ing.hasNoItems()) ingredients.add(ing);
                    }
                    if (ingredients.isEmpty()) continue;
                    int missing = countMissing(helper, ingredients);
                    if (missing < bestMissing) {
                        best = holder;
                        bestIngredients = ingredients;
                        bestMissing = missing;
                        if (missing == 0) break;
                    }
                } catch (Throwable ignored) {
                }
            }
            if (best == null || bestIngredients == null) return 0;

            int guard = 0;
            while (countMissing(helper, bestIngredients) > 0 && guard++ < 8) {
                boolean progressed = false;
                List<ItemStack> pool = bagPool(helper);
                for (Ingredient ing : bestIngredients) {
                    if (takeFromPool(pool, ing)) continue; // this occurrence is covered
                    for (ItemStack option : ing.getItems()) {
                        Item sub = option.getItem();
                        if (sub == Items.AIR || sub == target) continue;
                        if (craftOne(helper, level, sub, depth + 1, visiting) > 0) {
                            progressed = true;
                            break;
                        }
                    }
                    if (progressed) break;
                }
                if (!progressed) break;
            }
            if (countMissing(helper, bestIngredients) > 0) return 0;

            for (Ingredient ing : bestIngredients) {
                if (EmilyBlocks.consumeBag(helper, ing::test, 1) <= 0) return 0;
            }
            ItemStack result = best.value().getResultItem(level.registryAccess()).copy();
            helper.addToBagOrDrop(result);
            helper.triggerSwingAnim();
            level.playSound(null, helper.blockPosition(), SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.5F, 1.25F);
            return result.getCount();
        } catch (Throwable ignored) {
            return 0;
        } finally {
            visiting.remove(target);
        }
    }

    private static int countMissing(HelperEntity helper, List<Ingredient> ingredients) {
        List<ItemStack> pool = bagPool(helper);
        int missing = 0;
        for (Ingredient ing : ingredients) {
            if (!takeFromPool(pool, ing)) missing++;
        }
        return missing;
    }

    private static List<ItemStack> bagPool(HelperEntity helper) {
        List<ItemStack> pool = new ArrayList<>();
        var bag = helper.bag();
        for (int i = 0; i < bag.getSlots(); i++) {
            ItemStack stack = bag.getStackInSlot(i);
            if (!stack.isEmpty()) pool.add(stack.copy());
        }
        return pool;
    }

    private static boolean takeFromPool(List<ItemStack> pool, Ingredient ing) {
        for (ItemStack stack : pool) {
            if (stack.isEmpty() || stack.getCount() <= 0 || !ing.test(stack)) continue;
            stack.shrink(1);
            return true;
        }
        return false;
    }

    /** Hand-written fallbacks for the handful of recipes every companion needs before a pack's recipe manager is friendly. */
    private static int curated(HelperEntity helper, ServerLevel level, Item target, int wanted) {
        int made = 0;
        String path = targetPath(target);
        if (path.endsWith("_planks")) {
            for (int i = 0; i < helper.bag().getSlots() && made == 0; i++) {
                ItemStack log = helper.bag().getStackInSlot(i);
                if (log.isEmpty()) continue;
                String logPath = targetPath(log.getItem());
                if (!logPath.endsWith("_log") && !logPath.endsWith("_stem") && !logPath.endsWith("_wood")) continue;
                Item plank = plankFor(logPath);
                int crafts = Math.min(wanted / 4, log.getCount());
                if (crafts <= 0) continue;
                EmilyBlocks.consumeBag(helper, s -> s.is(log.getItem()), crafts);
                helper.addToBagOrDrop(new ItemStack(plank, crafts * 4));
                made += crafts * 4;
            }
            return made;
        }

        int times = 0;
        switch (path) {
            case "stick" -> {
                while (made < wanted && EmilyBlocks.countBag(helper, s -> s.is(Items.OAK_PLANKS) || s.is(Items.BIRCH_PLANKS)
                        || s.is(Items.SPRUCE_PLANKS) || s.is(Items.JUNGLE_PLANKS) || s.is(Items.ACACIA_PLANKS)
                        || s.is(Items.DARK_OAK_PLANKS) || s.is(Items.CHERRY_PLANKS) || s.is(Items.MANGROVE_PLANKS)) >= 2) {
                    consumeAnyPlanks(helper, 2);
                    helper.addToBagOrDrop(new ItemStack(Items.STICK, 4));
                    made += 4;
                    times++;
                    if (times > 32) break;
                }
            }
            case "crafting_table" -> {
                if (consumeAnyPlanks(helper, 4)) {
                    helper.addToBagOrDrop(new ItemStack(Items.CRAFTING_TABLE));
                    made = 1;
                }
            }
            case "chest" -> {
                if (consumeAnyPlanks(helper, 8)) {
                    helper.addToBagOrDrop(new ItemStack(Items.CHEST));
                    made = 1;
                }
            }
            case "furnace" -> {
                if (EmilyBlocks.consumeBag(helper, s -> s.is(Items.COBBLESTONE), 8) == 8) {
                    helper.addToBagOrDrop(new ItemStack(Items.FURNACE));
                    made = 1;
                }
            }
            case "torch" -> {
                if (hasBag(helper, s -> s.is(Items.STICK), 1)
                        && hasBag(helper, s -> s.is(Items.COAL) || s.is(Items.CHARCOAL), 1)) {
                    EmilyBlocks.consumeBag(helper, s -> s.is(Items.STICK), 1);
                    EmilyBlocks.consumeBag(helper, s -> s.is(Items.COAL) || s.is(Items.CHARCOAL), 1);
                    helper.addToBagOrDrop(new ItemStack(Items.TORCH, 4));
                    made = 4;
                }
            }
            case "iron_pickaxe" -> {
                if (hasBag(helper, s -> s.is(Items.IRON_INGOT), 3) && hasBag(helper, s -> s.is(Items.STICK), 2)) {
                    EmilyBlocks.consumeBag(helper, s -> s.is(Items.IRON_INGOT), 3);
                    EmilyBlocks.consumeBag(helper, s -> s.is(Items.STICK), 2);
                    helper.addToBagOrDrop(new ItemStack(Items.IRON_PICKAXE));
                    made = 1;
                }
            }
            case "iron_sword" -> {
                if (hasBag(helper, s -> s.is(Items.IRON_INGOT), 2) && hasBag(helper, s -> s.is(Items.STICK), 1)) {
                    EmilyBlocks.consumeBag(helper, s -> s.is(Items.IRON_INGOT), 2);
                    EmilyBlocks.consumeBag(helper, s -> s.is(Items.STICK), 1);
                    helper.addToBagOrDrop(new ItemStack(Items.IRON_SWORD));
                    made = 1;
                }
            }
            case "iron_axe" -> {
                if (hasBag(helper, s -> s.is(Items.IRON_INGOT), 3) && hasBag(helper, s -> s.is(Items.STICK), 2)) {
                    EmilyBlocks.consumeBag(helper, s -> s.is(Items.IRON_INGOT), 3);
                    EmilyBlocks.consumeBag(helper, s -> s.is(Items.STICK), 2);
                    helper.addToBagOrDrop(new ItemStack(Items.IRON_AXE));
                    made = 1;
                }
            }
            case "iron_shovel" -> {
                if (hasBag(helper, s -> s.is(Items.IRON_INGOT), 1) && hasBag(helper, s -> s.is(Items.STICK), 2)) {
                    EmilyBlocks.consumeBag(helper, s -> s.is(Items.IRON_INGOT), 1);
                    EmilyBlocks.consumeBag(helper, s -> s.is(Items.STICK), 2);
                    helper.addToBagOrDrop(new ItemStack(Items.IRON_SHOVEL));
                    made = 1;
                }
            }
            case "shield" -> {
                if (hasBag(helper, s -> s.is(Items.IRON_INGOT), 1)
                        && EmilyBlocks.countBag(helper, EmilyCrafting::isPlanks) >= 6) {
                    EmilyBlocks.consumeBag(helper, s -> s.is(Items.IRON_INGOT), 1);
                    consumeAnyPlanks(helper, 6);
                    helper.addToBagOrDrop(new ItemStack(Items.SHIELD));
                    made = 1;
                }
            }
            case "bucket" -> {
                if (EmilyBlocks.consumeBag(helper, s -> s.is(Items.IRON_INGOT), 3) == 3) {
                    helper.addToBagOrDrop(new ItemStack(Items.BUCKET));
                    made = 1;
                }
            }
            case "flint_and_steel" -> {
                if (hasBag(helper, s -> s.is(Items.IRON_INGOT), 1) && hasBag(helper, s -> s.is(Items.FLINT), 1)) {
                    EmilyBlocks.consumeBag(helper, s -> s.is(Items.IRON_INGOT), 1);
                    EmilyBlocks.consumeBag(helper, s -> s.is(Items.FLINT), 1);
                    helper.addToBagOrDrop(new ItemStack(Items.FLINT_AND_STEEL));
                    made = 1;
                }
            }
            case "arrow" -> {
                while (made < wanted
                        && hasBag(helper, s -> s.is(Items.FLINT), 1)
                        && hasBag(helper, s -> s.is(Items.STICK), 1)
                        && hasBag(helper, s -> s.is(Items.FEATHER), 1)) {
                    EmilyBlocks.consumeBag(helper, s -> s.is(Items.FLINT), 1);
                    EmilyBlocks.consumeBag(helper, s -> s.is(Items.STICK), 1);
                    EmilyBlocks.consumeBag(helper, s -> s.is(Items.FEATHER), 1);
                    helper.addToBagOrDrop(new ItemStack(Items.ARROW, 4));
                    made += 4;
                    if (++times > 32) break;
                }
            }
            case "bread" -> {
                while (made < wanted && EmilyBlocks.consumeBag(helper, s -> s.is(Items.WHEAT), 3) == 3) {
                    helper.addToBagOrDrop(new ItemStack(Items.BREAD));
                    made++;
                    if (++times > 32) break;
                }
            }
            case "blaze_powder" -> {
                while (made < wanted && EmilyBlocks.consumeBag(helper, s -> s.is(Items.BLAZE_ROD), 1) == 1) {
                    helper.addToBagOrDrop(new ItemStack(Items.BLAZE_POWDER, 2));
                    made += 2;
                    if (++times > 32) break;
                }
            }
            case "ender_eye" -> {
                while (made < wanted
                        && hasBag(helper, s -> s.is(Items.ENDER_PEARL), 1)
                        && hasBag(helper, s -> s.is(Items.BLAZE_POWDER), 1)) {
                    EmilyBlocks.consumeBag(helper, s -> s.is(Items.ENDER_PEARL), 1);
                    EmilyBlocks.consumeBag(helper, s -> s.is(Items.BLAZE_POWDER), 1);
                    helper.addToBagOrDrop(new ItemStack(Items.ENDER_EYE));
                    made++;
                    if (++times > 32) break;
                }
            }
            default -> {
                return 0;
            }
        }
        if (made > 0) {
            helper.triggerSwingAnim();
            level.playSound(null, helper.blockPosition(), SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.5F, 1.25F);
        }
        return made;
    }

    private static boolean hasBag(HelperEntity helper, Predicate<ItemStack> predicate, int count) {
        return EmilyBlocks.countBag(helper, predicate) >= count;
    }

    private static boolean consumeAnyPlanks(HelperEntity helper, int count) {
        int have = EmilyBlocks.countBag(helper, EmilyCrafting::isPlanks);
        if (have < count) return false;
        return EmilyBlocks.consumeBag(helper, EmilyCrafting::isPlanks, count) == count;
    }

    private static boolean isPlanks(ItemStack stack) {
        return !stack.isEmpty() && targetPath(stack.getItem()).endsWith("_planks");
    }

    private static Item plankFor(String logPath) {
        String plankPath = logPath
                .replace("stripped_", "")
                .replace("_log", "_planks")
                .replace("_stem", "_planks")
                .replace("_wood", "_planks")
                .replace("_hyphae", "_planks");
        Item item = BuiltInRegistries.ITEM.get(ResourceLocation.withDefaultNamespace(plankPath));
        return item == null ? Items.OAK_PLANKS : item;
    }

    private static String targetPath(Item item) {
        ResourceLocation key = BuiltInRegistries.ITEM.getKey(item);
        return key == null ? "" : key.getPath();
    }

    // ------------------------------------------------------------------------------------------------ item names

    @Nullable
    public static Item resolveItem(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String s = raw.trim().toLowerCase(Locale.ROOT)
                .replace('_', ' ')
                .replaceAll("^(a|an|some|the) ", "")
                .trim();
        if (s.isEmpty()) return null;

        String alias = switch (s) {
            case "iron", "iron ingot" -> "iron_ingot";
            case "gold", "gold ingot" -> "gold_ingot";
            case "copper", "copper ingot" -> "copper_ingot";
            case "netherite" -> "netherite_ingot";
            case "scrap" -> "netherite_scrap";
            case "planks", "wood", "wooden planks" -> "oak_planks";
            case "pickaxe" -> "iron_pickaxe";
            case "sword" -> "iron_sword";
            case "axe" -> "iron_axe";
            case "shovel" -> "iron_shovel";
            case "helmet" -> "iron_helmet";
            case "chestplate" -> "iron_chestplate";
            case "leggings" -> "iron_leggings";
            case "boots" -> "iron_boots";
            case "eye", "ender eye", "eye of ender" -> "ender_eye";
            case "blaze powder" -> "blaze_powder";
            case "flint and steel" -> "flint_and_steel";
            case "crafting table", "workbench" -> "crafting_table";
            case "food", "cooked food", "meal" -> "bread";
            default -> s.replace(' ', '_');
        };

        ResourceLocation rl = ResourceLocation.tryParse(alias);
        if (rl != null) {
            Item item = BuiltInRegistries.ITEM.get(rl);
            if (item != Items.AIR) return item;
        }
        Item item = BuiltInRegistries.ITEM.get(ResourceLocation.withDefaultNamespace(alias));
        if (item != Items.AIR) return item;

        // Last resort: a registry substring match, so modded names work too.
        for (ResourceLocation key : BuiltInRegistries.ITEM.keySet()) {
            if (key.getPath().equals(alias)) {
                Item found = BuiltInRegistries.ITEM.get(key);
                if (found != Items.AIR) return found;
            }
        }
        String needle = alias.replace('_', ' ');
        for (ResourceLocation key : BuiltInRegistries.ITEM.keySet()) {
            if (key.getPath().replace('_', ' ').contains(needle)) return BuiltInRegistries.ITEM.get(key);
        }
        return null;
    }

    // ------------------------------------------------------------------------------------------------ tasks

    public static class CraftTask implements EmilyTask {
        private final String target;
        private final int amount;
        private int delay = 12;
        private boolean done;
        private int made;

        public CraftTask(String target, int amount) {
            String t = target == null ? "" : target.trim();
            int n = Math.max(1, amount);
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("^(\\d{1,3})\\s+(.+)$").matcher(t);
            if (m.matches()) {
                try {
                    n = Math.max(1, Integer.parseInt(m.group(1)));
                } catch (NumberFormatException ignored) {
                }
                t = m.group(2).trim();
            }
            this.target = t;
            this.amount = n;
        }

        @Override
        public String name() {
            return "craft";
        }

        @Override
        public String label() {
            return "crafting " + target;
        }

        @Override
        public void start(HelperEntity helper, EmilyAgent agent) {
            agent.say("Let me see what I can make with what we have.", "calm");
        }

        @Override
        public Status tick(HelperEntity helper, EmilyAgent agent) {
            if (done) return Status.DONE;
            if (--delay > 0) return Status.RUNNING;
            made = craft(helper, target, amount);
            done = true;
            if (made > 0) {
                agent.say("Made " + made + " " + target + ".", "cheerful");
                return Status.DONE;
            }
            agent.say("I don't have the materials or the recipe for " + target + ".", "worried");
            return Status.FAILED;
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

    public record Smelt(Item input, Item output) {
    }

    public static class SmeltTask implements EmilyTask {
        private final String target;
        private final int amount;
        private Smelt smelt;
        private BlockPos furnace;
        private int elapsed;
        private int made;
        private boolean failed;

        public SmeltTask(String target, int amount) {
            String t = target == null ? "" : target.trim();
            int n = Math.max(1, amount);
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("^(\\d{1,3})\\s+(.+)$").matcher(t);
            if (m.matches()) {
                try {
                    n = Math.max(1, Integer.parseInt(m.group(1)));
                } catch (NumberFormatException ignored) {
                }
                t = m.group(2).trim();
            }
            this.target = t;
            this.amount = n;
        }

        @Override
        public String name() {
            return "smelt";
        }

        @Override
        public String label() {
            return "smelting " + target + " (" + made + "/" + amount + ")";
        }

        @Override
        public void start(HelperEntity helper, EmilyAgent agent) {
            if (!(helper.level() instanceof ServerLevel level)) {
                failed = true;
                return;
            }
            smelt = findSmelt(level, helper, target);
            if (smelt == null) {
                agent.say("I can't figure out how to smelt " + target + ".", "worried");
                failed = true;
                return;
            }
            furnace = findFurnace(level, helper.blockPosition(), 16);
            if (furnace == null && !placeFurnace(helper, level, agent)) {
                agent.say("I need a furnace. We don't have one nearby and I can't make one.", "worried");
                failed = true;
                return;
            }
            agent.say("Smelting " + target + ". I'll tend the furnace.", "calm");
        }

        @Override
        public Status tick(HelperEntity helper, EmilyAgent agent) {
            if (failed) return Status.FAILED;
            if (!(helper.level() instanceof ServerLevel level)) return Status.FAILED;
            elapsed++;
            if (elapsed > 20 * 60 * 4) return Status.FAILED;
            if (furnace == null) return Status.FAILED;
            BlockEntity be = level.getBlockEntity(furnace);
            if (!(be instanceof Container c)) {
                agent.say("The furnace is gone.", "worried");
                return Status.FAILED;
            }
            if (!EmilyNav.inReach(helper, furnace, 4.5)) {
                if (!EmilyNav.moveTo(helper, furnace, 1.1, 3.0) && elapsed > 400) return Status.FAILED;
                return Status.RUNNING;
            }
            EmilyNav.stop(helper);
            EmilyNav.lookAt(helper, furnace);

            ItemStack result = c.getItem(2);
            if (!result.isEmpty()) {
                made += result.getCount();
                helper.addToBagOrDrop(result.copy());
                c.setItem(2, ItemStack.EMPTY);
                c.setChanged();
                level.playSound(null, furnace, SoundEvents.ITEM_PICKUP, SoundSource.BLOCKS, 0.6F, 1.0F);
                if (made >= amount) {
                    agent.say("Smelted " + made + " " + new ItemStack(smelt.output()).getHoverName().getString() + ".", "cheerful");
                    return Status.DONE;
                }
            }

            if (!c.getItem(0).isEmpty() && !c.getItem(0).is(smelt.input())) {
                // Something else is in the input slot. Take it out so it does not smelt into the wrong item.
                helper.addToBagOrDrop(c.getItem(0).copy());
                c.setItem(0, ItemStack.EMPTY);
                c.setChanged();
            }
            if (c.getItem(0).isEmpty()) {
                int have = EmilyBlocks.countBag(helper, s -> s.is(smelt.input()));
                if (have <= 0) {
                    if (made > 0) return Status.DONE;
                    agent.say("I'm out of " + new ItemStack(smelt.input()).getHoverName().getString() + ".", "worried");
                    return Status.FAILED;
                }
                int n = Math.min(have, Math.min(8, amount - made + 2));
                if (EmilyBlocks.consumeBag(helper, s -> s.is(smelt.input()), n) > 0) {
                    c.setItem(0, new ItemStack(smelt.input(), n));
                    c.setChanged();
                }
            }

            if (c.getItem(1).isEmpty()) {
                Item fuel = findFuel(helper);
                if (fuel != null) {
                    int n = Math.min(8, EmilyBlocks.countBag(helper, s -> s.is(fuel)));
                    if (n > 0 && EmilyBlocks.consumeBag(helper, s -> s.is(fuel), n) > 0) {
                        c.setItem(1, new ItemStack(fuel, n));
                        c.setChanged();
                    }
                }
            }
            return Status.RUNNING;
        }

        private boolean placeFurnace(HelperEntity helper, ServerLevel level, EmilyAgent agent) {
            if (!helper.hasItem(Items.FURNACE)) {
                int madeFurnace = craft(helper, "furnace", 1);
                if (madeFurnace <= 0) return false;
            }
            BlockPos spot = EmilyNav.findSafeSpot(level, helper, helper.blockPosition().relative(helper.getDirection(), 2), 4);
            if (spot == null) spot = helper.blockPosition().relative(helper.getDirection(), 2);
            if (!EmilyBlocks.place(helper, level, spot, Blocks.FURNACE.defaultBlockState(), Items.FURNACE)) return false;
            furnace = spot;
            return true;
        }

        @Nullable
        private static BlockPos findFurnace(ServerLevel level, BlockPos center, int radius) {
            for (BlockPos pos : BlockPos.betweenClosed(center.offset(-radius, -6, -radius), center.offset(radius, 6, radius))) {
                if (level.getBlockEntity(pos) instanceof Container c && c.getContainerSize() == 3) {
                    return pos.immutable();
                }
            }
            return null;
        }

        @Nullable
        private static Item findFuel(HelperEntity helper) {
            if (EmilyBlocks.countBag(helper, s -> s.is(Items.COAL)) > 0) return Items.COAL;
            if (EmilyBlocks.countBag(helper, s -> s.is(Items.CHARCOAL)) > 0) return Items.CHARCOAL;
            if (EmilyBlocks.countBag(helper, s -> s.is(Items.BLAZE_ROD)) > 0) return Items.BLAZE_ROD;
            if (EmilyBlocks.countBag(helper, s -> s.is(Items.STICK)) > 0) return Items.STICK;
            for (int i = 0; i < helper.bag().getSlots(); i++) {
                ItemStack stack = helper.bag().getStackInSlot(i);
                if (stack.isEmpty()) continue;
                String id = targetPath(stack.getItem());
                if (id.endsWith("_planks") || id.endsWith("_log") || id.endsWith("_wood") || id.endsWith("_stem")) {
                    return stack.getItem();
                }
            }
            return null;
        }

        @Override
        public boolean quietOnDone() {
            return true;
        }

        @Override
        public boolean roaming() {
            return false;
        }
    }

    @Nullable
    public static Smelt findSmelt(ServerLevel level, HelperEntity helper, String raw) {
        Item target = resolveItem(raw);
        boolean foodRequest = raw != null && (raw.contains("food") || raw.contains("meat") || raw.contains("cook"));
        for (RecipeHolder<?> holder : level.getRecipeManager().getAllRecipesFor(RecipeType.SMELTING)) {
            try {
                ItemStack result = holder.value().getResultItem(level.registryAccess());
                if (result.isEmpty()) continue;
                if (target != null && !result.is(target)) {
                    if (!foodRequest || !result.has(DataComponents.FOOD)) continue;
                }
                if (target == null && !result.has(DataComponents.FOOD)) continue;
                List<Ingredient> ingredients = holder.value().getIngredients();
                if (ingredients.isEmpty()) continue;
                List<ItemStack> options = new ArrayList<>();
                for (Ingredient ing : ingredients) {
                    if (ing == null || ing.isEmpty()) continue;
                    for (ItemStack option : ing.getItems()) {
                        if (!option.isEmpty()) options.add(option);
                    }
                }
                for (ItemStack option : options) {
                    if (EmilyBlocks.countBag(helper, s -> s.is(option.getItem())) > 0) {
                        return new Smelt(option.getItem(), result.getItem());
                    }
                }
            } catch (Throwable ignored) {
            }
        }
        return null;
    }
}
