package com.blighteddreams.helper.agent;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Emily's long-term memory: named places per owner, a small journal of things that happened, and a few profile
 * preferences. Stored in the overworld's data folder as {@code blighted_emily_memory.dat}, so it survives restarts,
 * deaths and dimension changes.
 *
 * <p>This is deliberately separate from {@code HelperMemory} (the chat-shaped memories that go into the model prompt).
 * This one is the body's memory: where home is, where the good mine is, where the last fortress was.</p>
 */
public final class EmilyMemory extends SavedData {
    private static final String FILE_ID = "blighted_emily_memory";
    private static final int MAX_WAYPOINTS = 80;
    private static final int MAX_JOURNAL = 40;

    private static final SavedData.Factory<EmilyMemory> FACTORY =
            new SavedData.Factory<>(EmilyMemory::new, EmilyMemory::load);

    /** One remembered place. {@code kind} is a loose label such as home, base, mine, portal, chest, structure. */
    public record Waypoint(String name, String dimension, int x, int y, int z, String kind, long created) {
        public BlockPos pos() {
            return new BlockPos(x, y, z);
        }

        public String dimensionPath() {
            int cut = dimension.indexOf(':');
            return cut >= 0 ? dimension.substring(cut + 1) : dimension;
        }

        public String shortLine() {
            return name + " [" + kind + "] " + x + " " + y + " " + z + " (" + dimensionPath() + ")";
        }

        CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putString("name", name);
            tag.putString("dim", dimension);
            tag.putInt("x", x);
            tag.putInt("y", y);
            tag.putInt("z", z);
            tag.putString("kind", kind);
            tag.putLong("created", created);
            return tag;
        }

        static Waypoint load(CompoundTag tag) {
            return new Waypoint(
                    tag.getString("name"),
                    tag.getString("dim"),
                    tag.getInt("x"),
                    tag.getInt("y"),
                    tag.getInt("z"),
                    tag.getString("kind"),
                    tag.getLong("created"));
        }
    }

    private final Map<UUID, List<Waypoint>> waypoints = new HashMap<>();
    private final Map<UUID, List<String>> journal = new HashMap<>();
    private final Map<UUID, CompoundTag> profiles = new HashMap<>();

    public EmilyMemory() {
    }

    public static EmilyMemory get(ServerLevel anyLevel) {
        ServerLevel overworld = anyLevel.getServer().overworld();
        return overworld.getDataStorage().computeIfAbsent(FACTORY, FILE_ID);
    }

    // ------------------------------------------------------------------------------------------------ waypoints

    public synchronized List<Waypoint> all(UUID owner) {
        List<Waypoint> list = waypoints.get(owner);
        return list == null ? List.of() : List.copyOf(list);
    }

    public synchronized Waypoint find(UUID owner, String query) {
        if (query == null || query.isBlank()) return null;
        String q = query.trim().toLowerCase(Locale.ROOT);
        List<Waypoint> list = waypoints.get(owner);
        if (list == null || list.isEmpty()) return null;

        for (Waypoint w : list) {
            if (w.name().equalsIgnoreCase(q)) return w;
        }
        for (Waypoint w : list) {
            if (w.name().toLowerCase(Locale.ROOT).startsWith(q)) return w;
        }
        for (Waypoint w : list) {
            if (w.name().toLowerCase(Locale.ROOT).contains(q)) return w;
        }
        for (Waypoint w : list) {
            if (w.kind().equalsIgnoreCase(q)) return w;
        }
        for (Waypoint w : list) {
            if (w.kind().toLowerCase(Locale.ROOT).contains(q)) return w;
        }
        return null;
    }

    public synchronized Waypoint remember(UUID owner, String name, ResourceKey<Level> dimension, BlockPos pos, String kind) {
        String cleanName = (name == null || name.isBlank() ? kind : name).trim();
        if (cleanName.length() > 40) cleanName = cleanName.substring(0, 40);
        String dim = dimension.location().toString();
        List<Waypoint> list = waypoints.computeIfAbsent(owner, k -> new ArrayList<>());
        Waypoint fresh = new Waypoint(cleanName, dim, pos.getX(), pos.getY(), pos.getZ(),
                kind == null || kind.isBlank() ? "place" : kind.trim().toLowerCase(Locale.ROOT),
                System.currentTimeMillis());

        for (int i = 0; i < list.size(); i++) {
            Waypoint old = list.get(i);
            if (old.name().equalsIgnoreCase(cleanName)) {
                list.set(i, fresh);
                setDirty();
                return fresh;
            }
        }
        list.add(fresh);
        if (list.size() > MAX_WAYPOINTS) {
            list.sort(Comparator.comparingLong(Waypoint::created));
            while (list.size() > MAX_WAYPOINTS) {
                list.remove(0);
            }
        }
        setDirty();
        return fresh;
    }

    public synchronized boolean forget(UUID owner, String name) {
        List<Waypoint> list = waypoints.get(owner);
        if (list == null) return false;
        boolean removed = list.removeIf(w -> w.name().equalsIgnoreCase(name));
        if (removed) setDirty();
        return removed;
    }

    @Nullable
    public synchronized Waypoint nearest(UUID owner, String dimension, BlockPos pos, String kind) {
        List<Waypoint> list = waypoints.get(owner);
        if (list == null || list.isEmpty()) return null;
        String k = kind == null ? "" : kind.toLowerCase(Locale.ROOT);
        return list.stream()
                .filter(w -> dimension == null || w.dimension().equals(dimension))
                .filter(w -> k.isBlank() || w.kind().contains(k) || w.name().toLowerCase(Locale.ROOT).contains(k))
                .min(Comparator.comparingDouble(w -> w.pos().distSqr(pos)))
                .orElse(null);
    }

    // ------------------------------------------------------------------------------------------------ journal + profile

    public synchronized void note(UUID owner, String text) {
        if (text == null || text.isBlank()) return;
        List<String> list = journal.computeIfAbsent(owner, k -> new ArrayList<>());
        list.add(text.length() > 180 ? text.substring(0, 180) : text);
        while (list.size() > MAX_JOURNAL) {
            list.remove(0);
        }
        setDirty();
    }

    public synchronized List<String> recentNotes(UUID owner, int max) {
        List<String> list = journal.get(owner);
        if (list == null || list.isEmpty()) return List.of();
        int from = Math.max(0, list.size() - Math.max(1, max));
        return List.copyOf(list.subList(from, list.size()));
    }

    public synchronized void setProfile(UUID owner, String key, String value) {
        CompoundTag tag = profiles.computeIfAbsent(owner, k -> new CompoundTag());
        tag.putString(key, value == null ? "" : value);
        setDirty();
    }

    public synchronized String profile(UUID owner, String key, String fallback) {
        CompoundTag tag = profiles.get(owner);
        if (tag == null || !tag.contains(key)) return fallback;
        String value = tag.getString(key);
        return value.isBlank() ? fallback : value;
    }

    // ------------------------------------------------------------------------------------------------ save / load

    @Override
    public synchronized CompoundTag save(CompoundTag root, HolderLookup.Provider registries) {
        CompoundTag owners = new CompoundTag();
        for (Map.Entry<UUID, List<Waypoint>> entry : waypoints.entrySet()) {
            ListTag list = new ListTag();
            for (Waypoint w : entry.getValue()) list.add(w.save());
            owners.put(entry.getKey().toString(), list);
        }
        root.put("waypoints", owners);

        CompoundTag journals = new CompoundTag();
        for (Map.Entry<UUID, List<String>> entry : journal.entrySet()) {
            ListTag list = new ListTag();
            for (String line : entry.getValue()) {
                CompoundTag t = new CompoundTag();
                t.putString("line", line);
                list.add(t);
            }
            journals.put(entry.getKey().toString(), list);
        }
        root.put("journal", journals);

        CompoundTag prof = new CompoundTag();
        for (Map.Entry<UUID, CompoundTag> entry : profiles.entrySet()) {
            prof.put(entry.getKey().toString(), entry.getValue().copy());
        }
        root.put("profiles", prof);
        return root;
    }

    private static EmilyMemory load(CompoundTag root, HolderLookup.Provider registries) {
        EmilyMemory data = new EmilyMemory();
        CompoundTag owners = root.getCompound("waypoints");
        for (String id : owners.getAllKeys()) {
            try {
                UUID owner = UUID.fromString(id);
                ListTag list = owners.getList(id, Tag.TAG_COMPOUND);
                List<Waypoint> places = new ArrayList<>();
                for (int i = 0; i < list.size(); i++) {
                    places.add(Waypoint.load(list.getCompound(i)));
                }
                data.waypoints.put(owner, places);
            } catch (IllegalArgumentException ignored) {
                // a damaged id should never take the whole memory down
            }
        }
        CompoundTag journals = root.getCompound("journal");
        for (String id : journals.getAllKeys()) {
            try {
                UUID owner = UUID.fromString(id);
                ListTag list = journals.getList(id, Tag.TAG_COMPOUND);
                List<String> lines = new ArrayList<>();
                for (int i = 0; i < list.size(); i++) {
                    lines.add(list.getCompound(i).getString("line"));
                }
                data.journal.put(owner, lines);
            } catch (IllegalArgumentException ignored) {
            }
        }
        CompoundTag prof = root.getCompound("profiles");
        for (String id : prof.getAllKeys()) {
            try {
                data.profiles.put(UUID.fromString(id), prof.getCompound(id).copy());
            } catch (IllegalArgumentException ignored) {
            }
        }
        return data;
    }

    // ------------------------------------------------------------------------------------------------ prompt text

    /** A compact block for the model prompt: where she is and what she remembers. */
    public synchronized String describe(UUID owner, @Nullable String currentDimension, BlockPos near, int max) {
        StringBuilder sb = new StringBuilder();
        List<Waypoint> list = waypoints.get(owner);
        if (list != null && !list.isEmpty()) {
            List<Waypoint> sorted = new ArrayList<>(list);
            if (currentDimension != null) {
                sorted.sort(Comparator
                        .comparing((Waypoint w) -> !w.dimension().equals(currentDimension))
                        .thenComparingDouble(w -> near == null ? 0 : w.pos().distSqr(near)));
            }
            sb.append("Known places: ");
            int used = 0;
            for (Waypoint w : sorted) {
                if (used++ >= Math.max(1, max)) break;
                if (used > 1) sb.append("; ");
                sb.append(w.shortLine());
            }
            sb.append('\n');
        }
        List<String> notes = recentNotes(owner, 4);
        if (!notes.isEmpty()) {
            sb.append("Recent memories: ");
            sb.append(String.join(" | ", notes));
            sb.append('\n');
        }
        return sb.toString();
    }
}
