package com.blighteddreams.helper.agent;

import com.mojang.datafixers.util.Pair;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.StructureTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.levelgen.structure.Structure;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Finds vanilla and modded structures by name, using the world generator's own locator. */
public final class EmilyStructures {
    private EmilyStructures() {
    }

    @Nullable
    public static BlockPos locate(ServerLevel level, String rawQuery, BlockPos from) {
        if (rawQuery == null || rawQuery.isBlank()) return null;
        String query = rawQuery.trim().toLowerCase(Locale.ROOT).replace(' ', '_');
        String dimension = level.dimension().location().getPath();

        TagKey<Structure> tag = tagFor(query, dimension);
        if (tag != null) {
            BlockPos found = level.findNearestMapStructure(tag, from, 100, false);
            if (found != null) return found;
        }

        // Modded (and some vanilla) structures are easiest to find by registry key.
        List<Holder<Structure>> holders = new ArrayList<>();
        var registry = level.registryAccess().registryOrThrow(Registries.STRUCTURE);
        for (Holder.Reference<Structure> holder : registry.holders().toList()) {
            String path = holder.key().location().getPath().toLowerCase(Locale.ROOT);
            String namespace = holder.key().location().getNamespace().toLowerCase(Locale.ROOT);
            if (matchesQuery(query, path) || matchesQuery(query, namespace + "_" + path)) {
                holders.add(holder);
            }
        }
        if (holders.isEmpty()) {
            // Try the common short names against the full registry path as a final fallback.
            for (Holder.Reference<Structure> holder : registry.holders().toList()) {
                if (holder.key().location().getPath().contains(query)) holders.add(holder);
            }
        }
        if (holders.isEmpty()) return null;

        Pair<BlockPos, Holder<Structure>> result = level.getChunkSource().getGenerator()
                .findNearestMapStructure(level, HolderSet.direct(holders), from, 100, false);
        return result == null ? null : result.getFirst();
    }

    @Nullable
    private static TagKey<Structure> tagFor(String query, String dimension) {
        return switch (query) {
            case "village", "villages" -> StructureTags.VILLAGE;
            case "mineshaft", "mineshafts", "mine" -> StructureTags.MINESHAFT;
            case "shipwreck", "shipwrecks" -> StructureTags.SHIPWRECK;
            case "ruined_portal", "portal", "portals" -> StructureTags.RUINED_PORTAL;
            case "ocean_ruin", "ocean_ruins", "underwater_ruin" -> StructureTags.OCEAN_RUIN;
            case "stronghold", "strongholds" -> StructureTags.EYE_OF_ENDER_LOCATED;
            case "treasure", "buried_treasure" -> StructureTags.ON_TREASURE_MAPS;
            case "trial_chambers", "trial_chamber" -> StructureTags.ON_TRIAL_CHAMBERS_MAPS;
            default -> null;
        };
    }

    private static boolean matchesQuery(String query, String path) {
        if (path.equals(query)) return true;
        if (path.endsWith("_" + query)) return true;
        if (path.startsWith(query + "_")) return true;
        if (path.contains(query)) return true;
        // "end city" -> end_city, "ancient city" -> ancient_city, "bastion" -> bastion_remnant
        if (query.endsWith("s") && path.contains(query.substring(0, query.length() - 1))) return true;
        return false;
    }
}
