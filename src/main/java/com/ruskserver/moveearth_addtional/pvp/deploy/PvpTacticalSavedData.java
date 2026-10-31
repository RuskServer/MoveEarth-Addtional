package com.ruskserver.moveearth_addtional.pvp.deploy;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/** The scanned tactical map of each PvP map, by map id. */
public final class PvpTacticalSavedData extends SavedData {
    private final Map<String, PvpTacticalMap> maps = new HashMap<>();

    public Optional<PvpTacticalMap> get(String mapId) {
        return Optional.ofNullable(maps.get(mapId));
    }

    public void put(PvpTacticalMap map) {
        maps.put(map.mapId(), map);
        setDirty();
    }

    public void remove(String mapId) {
        if (maps.remove(mapId) != null) setDirty();
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        CompoundTag all = new CompoundTag();
        maps.forEach((id, map) -> {
            CompoundTag entry = new CompoundTag();
            entry.putLong("Version", map.version());
            entry.putInt("OriginX", map.originX());
            entry.putInt("OriginZ", map.originZ());
            entry.putInt("CellSize", map.cellSize());
            entry.putInt("Width", map.width());
            entry.putInt("Depth", map.depth());
            entry.putInt("BaseY", map.baseY());
            entry.putByteArray("Data", map.compressed());
            all.put(id, entry);
        });
        tag.put("Maps", all);
        return tag;
    }

    public static PvpTacticalSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        PvpTacticalSavedData data = new PvpTacticalSavedData();
        CompoundTag all = tag.getCompound("Maps");
        for (String id : all.getAllKeys()) {
            CompoundTag entry = all.getCompound(id);
            try {
                data.maps.put(id, PvpTacticalMap.fromCompressed(id, entry.getLong("Version"), entry.getInt("OriginX"),
                        entry.getInt("OriginZ"), entry.getInt("CellSize"), entry.getInt("Width"),
                        entry.getInt("Depth"), entry.getInt("BaseY"), entry.getByteArray("Data")));
            } catch (IllegalArgumentException corrupt) {
                // A damaged scan is dropped; the map is scanned again before its next match.
            }
        }
        return data;
    }

    public static PvpTacticalSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(PvpTacticalSavedData::new, PvpTacticalSavedData::load, null),
                "moveearth_pvp_tactical");
    }
}
