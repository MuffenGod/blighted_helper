package com.blighteddreams.helper;

import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;

/**
 * Live survival progression analysis and navigation facts for Emily.
 * Inspects player advancements, gear, key inventory items, and bed/home coordinates
 * to provide intelligent, contextual guidance and true in-game game awareness.
 */
public final class HelperProgression {
    private HelperProgression() {}

    public record NavigationInfo(
            boolean hasBed,
            int targetX, int targetY, int targetZ,
            String targetDimension,
            int distance,
            String direction,
            boolean sameDimension
    ) {}

    public static NavigationInfo getNavigation(ServerPlayer sp) {
        BlockPos currentPos = sp.blockPosition();
        String currentDim = sp.level().dimension().location().toString();

        BlockPos bedPos = sp.getRespawnPosition();
        ResourceKey<Level> bedDimKey = sp.getRespawnDimension();
        String bedDim = bedDimKey != null ? bedDimKey.location().toString() : "minecraft:overworld";

        if (bedPos != null) {
            boolean sameDim = bedDim.equals(currentDim);
            int dx = bedPos.getX() - currentPos.getX();
            int dz = bedPos.getZ() - currentPos.getZ();
            int dist = (int) Math.round(Math.sqrt((double) dx * dx + (double) dz * dz));
            String dir = getDirectionName(dx, dz);
            return new NavigationInfo(true, bedPos.getX(), bedPos.getY(), bedPos.getZ(), bedDim, dist, dir, sameDim);
        }

        // Fall back to world spawn
        BlockPos spawnPos = sp.serverLevel().getSharedSpawnPos();
        boolean sameDim = "minecraft:overworld".equals(currentDim);
        int dx = spawnPos.getX() - currentPos.getX();
        int dz = spawnPos.getZ() - currentPos.getZ();
        int dist = (int) Math.round(Math.sqrt((double) dx * dx + (double) dz * dz));
        String dir = getDirectionName(dx, dz);
        return new NavigationInfo(false, spawnPos.getX(), spawnPos.getY(), spawnPos.getZ(), "minecraft:overworld", dist, dir, sameDim);
    }

    public static String getDirectionName(double dx, double dz) {
        double angle = Math.toDegrees(Math.atan2(dz, dx));
        if (angle >= -22.5 && angle < 22.5) return "East";
        if (angle >= 22.5 && angle < 67.5) return "South-East";
        if (angle >= 67.5 && angle < 112.5) return "South";
        if (angle >= 112.5 && angle < 157.5) return "South-West";
        if (angle >= 157.5 || angle < -157.5) return "West";
        if (angle >= -157.5 && angle < -112.5) return "North-West";
        if (angle >= -112.5 && angle < -67.5) return "North";
        return "North-East";
    }

    public record ProgressionSummary(
            String stageName,
            String gearSummary,
            String inventoryKeyItems,
            String nextObjective,
            String tacticalAdvice
    ) {}

    public static ProgressionSummary analyze(ServerPlayer sp) {
        // Advancements inspection
        boolean hasSmeltIron = hasAdvancement(sp, "story/smelt_iron");
        boolean hasDiamond = hasAdvancement(sp, "story/mine_diamond");
        boolean hasShinyGear = hasAdvancement(sp, "story/shiny_gear");
        boolean hasNether = hasAdvancement(sp, "story/enter_the_nether");
        boolean hasFortress = hasAdvancement(sp, "nether/find_fortress");
        boolean hasBlazeRod = hasAdvancement(sp, "nether/obtain_blaze_rod");
        boolean hasBrewed = hasAdvancement(sp, "nether/brew_potion");
        boolean hasFollowEye = hasAdvancement(sp, "story/follow_ender_eye");
        boolean hasEnterEnd = hasAdvancement(sp, "story/enter_the_end");
        boolean hasKillDragon = hasAdvancement(sp, "end/kill_dragon");
        boolean hasElytra = hasAdvancement(sp, "end/elytra");

        // Inventory scan
        int ironCount = 0;
        int diamondCount = 0;
        int obsidianCount = 0;
        int blazeRodCount = 0;
        int enderPearlCount = 0;
        int netheriteCount = 0;
        int foodCount = 0;

        for (ItemStack item : sp.getInventory().items) {
            if (item.isEmpty()) continue;
            if (item.is(Items.IRON_INGOT) || item.is(Items.RAW_IRON)) ironCount += item.getCount();
            else if (item.is(Items.DIAMOND)) diamondCount += item.getCount();
            else if (item.is(Items.OBSIDIAN)) obsidianCount += item.getCount();
            else if (item.is(Items.BLAZE_ROD)) blazeRodCount += item.getCount();
            else if (item.is(Items.ENDER_PEARL) || item.is(Items.ENDER_EYE)) enderPearlCount += item.getCount();
            else if (item.is(Items.NETHERITE_INGOT) || item.is(Items.ANCIENT_DEBRIS)) netheriteCount += item.getCount();
            if (item.has(DataComponents.FOOD)) foodCount += item.getCount();
        }

        // Equipped gear tier
        String armorTier = "No armor";
        ItemStack chest = sp.getItemBySlot(EquipmentSlot.CHEST);
        if (!chest.isEmpty()) {
            String name = chest.getHoverName().getString().toLowerCase(java.util.Locale.ROOT);
            if (name.contains("netherite")) armorTier = "Netherite armor";
            else if (name.contains("diamond")) armorTier = "Diamond armor";
            else if (name.contains("iron")) armorTier = "Iron armor";
            else if (name.contains("chainmail")) armorTier = "Chainmail armor";
            else if (name.contains("golden") || name.contains("gold")) armorTier = "Gold armor";
            else if (name.contains("leather")) armorTier = "Leather armor";
            else armorTier = chest.getHoverName().getString();
        }

        List<String> keyItems = new ArrayList<>();
        if (diamondCount > 0) keyItems.add(diamondCount + " diamonds");
        if (ironCount > 0) keyItems.add(ironCount + " iron");
        if (obsidianCount > 0) keyItems.add(obsidianCount + " obsidian");
        if (blazeRodCount > 0) keyItems.add(blazeRodCount + " blaze rods");
        if (enderPearlCount > 0) keyItems.add(enderPearlCount + " ender pearls");
        if (netheriteCount > 0) keyItems.add(netheriteCount + " netherite");
        if (foodCount > 0) keyItems.add(foodCount + " food");
        String invSummary = keyItems.isEmpty() ? "Bare inventory" : String.join(", ", keyItems);

        // Determine Milestone Stage
        if (hasKillDragon) {
            String obj = hasElytra ? "Mine Ancient Debris in the Nether for full Netherite upgrades and master enchanted gear!"
                    : "Toss an ender pearl into the End Gateway to explore the outer End islands for End Cities and an Elytra!";
            return new ProgressionSummary("Stage 7: Post-Game & End City Exploration", armorTier, invSummary, obj,
                    "The dragon is slain! Now we rule the skies. Let's find an End Ship for that Elytra.");
        } else if (hasEnterEnd || hasFollowEye) {
            String obj = "Gear up with bows, arrows, and water buckets, enter the End Portal, shoot down the healing crystals, and take down the Ender Dragon!";
            return new ProgressionSummary("Stage 6: The Ender Dragon Showdown", armorTier, invSummary, obj,
                    "Endgame time. Keep your distance on the pillars and watch out for dragon breath!");
        } else if (hasBlazeRod || blazeRodCount > 0) {
            String obj = enderPearlCount >= 12
                    ? "Craft Eyes of Ender (blaze powder + ender pearls), throw them across the Overworld to locate the Stronghold, and activate the End Portal!"
                    : "Barter with Piglins or hunt in Warped Forests to collect at least 12 Ender Pearls, then craft Eyes of Ender to find the Stronghold!";
            return new ProgressionSummary("Stage 5: Stronghold Hunting & Eye of Ender Preparation", armorTier, invSummary, obj,
                    "We have blaze rods! Time to brew potions and track down the portal room.");
        } else if (hasNether || sp.level().dimension().equals(Level.NETHER)) {
            String obj = "Explore the Nether to locate a Nether Fortress, defeat Blazes to harvest Blaze Rods, and trade gold ingots with Piglins for Ender Pearls.";
            return new ProgressionSummary("Stage 4: Nether Fortress & Blaze Rod Expedition", armorTier, invSummary, obj,
                    "Stay alert in the Nether. Keep gold armor equipped so Piglins stay neutral, and watch out for Wither Skeletons!");
        } else if (hasDiamond || diamondCount >= 3 || hasShinyGear) {
            String obj = obsidianCount >= 10
                    ? "Build a 4x5 Nether portal frame with our obsidian, light it with flint and steel, craft a piece of gold armor, and enter the Nether!"
                    : "Mine 10 blocks of obsidian using a diamond pickaxe (or cast obsidian with lava and water) to build a Nether portal!";
            return new ProgressionSummary("Stage 3: Nether Portal Construction & Prep", armorTier, invSummary, obj,
                    "We've got diamonds! Let's get our portal up and prepare for the Nether.");
        } else if (hasSmeltIron || ironCount >= 10) {
            return new ProgressionSummary("Stage 2: Deepslate Diamond Mining", armorTier, invSummary,
                    "Head down to deepslate layer (between Y= -54 and -58) to mine diamonds, craft a diamond pickaxe, and secure obsidian.",
                    "We have iron gear, but we need diamonds to progress. Let's dig deep!");
        } else {
            return new ProgressionSummary("Stage 1: Basic Survival & Iron Gathering", armorTier, invSummary,
                    "Gather surface wood and coal, hunt or harvest food, and mine for iron to craft a full set of iron armor, shield, and tools.",
                    "We're just getting started. Let's make sure we don't starve and get some iron armor on before nightfall!");
        }
    }

    private static boolean hasAdvancement(ServerPlayer sp, String path) {
        try {
            var advManager = sp.server.getAdvancements();
            AdvancementHolder holder = advManager.get(ResourceLocation.withDefaultNamespace(path));
            if (holder == null) return false;
            return sp.getAdvancements().getOrStartProgress(holder).isDone();
        } catch (Exception ignored) {
            return false;
        }
    }

    public static String build(ServerPlayer sp) {
        NavigationInfo nav = getNavigation(sp);
        ProgressionSummary prog = analyze(sp);

        StringBuilder sb = new StringBuilder();
        sb.append("[Home & Navigation]\n");
        BlockPos ppos = sp.blockPosition();
        sb.append("Current Location: X: ").append(ppos.getX()).append(", Y: ").append(ppos.getY()).append(", Z: ").append(ppos.getZ())
                .append(" (").append(sp.level().dimension().location().getPath()).append(")\n");
        if (nav.hasBed()) {
            sb.append("Bed Respawn: X: ").append(nav.targetX()).append(", Y: ").append(nav.targetY()).append(", Z: ").append(nav.targetZ())
                    .append(" (").append(nav.targetDimension()).append(")");
            if (nav.sameDimension()) {
                sb.append(" - Distance: ").append(nav.distance()).append(" blocks ").append(nav.direction());
            } else {
                sb.append(" - In another dimension! Need a portal first.");
            }
            sb.append("\n");
        } else {
            sb.append("Bed Respawn: No bed spawn set. World Spawn: X: ").append(nav.targetX()).append(", Y: ").append(nav.targetY()).append(", Z: ").append(nav.targetZ())
                    .append(" - Distance: ").append(nav.distance()).append(" blocks ").append(nav.direction()).append("\n");
        }

        // Direct nav tag for zero-latency client regex extraction
        sb.append("NAV_DATA: bed=").append(nav.hasBed() ? 1 : 0)
                .append(" x=").append(nav.targetX())
                .append(" y=").append(nav.targetY())
                .append(" z=").append(nav.targetZ())
                .append(" dist=").append(nav.distance())
                .append(" dir=").append(nav.direction())
                .append(" sameDim=").append(nav.sameDimension() ? 1 : 0)
                .append("\n\n");

        sb.append("[Progression Status & Next Goal]\n");
        sb.append("Current Stage: ").append(prog.stageName()).append("\n");
        sb.append("Player Armor & Gear: ").append(prog.gearSummary()).append("\n");
        sb.append("Inventory Supplies: ").append(prog.inventoryKeyItems()).append("\n");
        sb.append("Recommended Next Objective: ").append(prog.nextObjective()).append("\n");
        sb.append("Tactical Advice: ").append(prog.tacticalAdvice());

        return sb.toString();
    }
}
