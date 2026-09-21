package com.ruskserver.moveearth_addtional.region;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Which regions each player has stood in.
 *
 * <p>The hub tells a player what their region holds and what its neighbours
 * hold, and by default only for regions they have actually been to. That is
 * the difference between a map and a wiki: a nation that wants to know where
 * the uranium is has to go and look, or ask someone who did, and both of those
 * are the point of dividing the world up in the first place.
 *
 * <p>Per player rather than per nation. Knowledge that arrived with a member
 * and left with them would be strange, and a nation that recruits a traveller
 * gaining their map is a reasonable thing to happen.
 */
public final class RegionVisitSavedData extends SavedData {

    private static final String KEY = "moveearth_region_visits";

    private final Map<UUID, Set<Integer>> visited = new LinkedHashMap<>();

    /** Records a region as seen. True when this was the first time. */
    public boolean record(UUID player, int region) {
        if (player == null || region <= 0) {
            return false;
        }
        boolean added = visited.computeIfAbsent(player, key -> new LinkedHashSet<>()).add(region);
        if (added) {
            setDirty();
        }
        return added;
    }

    /** The regions this player knows, in the order they first saw them. */
    public Set<Integer> known(UUID player) {
        Set<Integer> regions = visited.get(player);
        return regions == null ? Set.of() : Set.copyOf(regions);
    }

    public boolean knows(UUID player, int region) {
        Set<Integer> regions = visited.get(player);
        return regions != null && regions.contains(region);
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag players = new ListTag();
        visited.forEach((player, regions) -> {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("Player", player);
            entry.putIntArray("Regions", regions.stream().mapToInt(Integer::intValue).toArray());
            players.add(entry);
        });
        tag.put("Players", players);
        return tag;
    }

    private static RegionVisitSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        RegionVisitSavedData data = new RegionVisitSavedData();
        ListTag players = tag.getList("Players", Tag.TAG_COMPOUND);
        for (int index = 0; index < players.size(); index++) {
            CompoundTag entry = players.getCompound(index);
            if (!entry.hasUUID("Player")) {
                continue;
            }
            Set<Integer> regions = new LinkedHashSet<>();
            for (int region : entry.getIntArray("Regions")) {
                if (region > 0) {
                    regions.add(region);
                }
            }
            data.visited.put(entry.getUUID("Player"), regions);
        }
        return data;
    }

    public static RegionVisitSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(RegionVisitSavedData::new, RegionVisitSavedData::load, null),
                KEY);
    }
}
