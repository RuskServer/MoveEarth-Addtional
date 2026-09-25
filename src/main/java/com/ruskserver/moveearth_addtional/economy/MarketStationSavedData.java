package com.ruskserver.moveearth_addtional.economy;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.List;

/** One physical hand-off point per nation; IDs survive chunk unloads and server restarts. */
public final class MarketStationSavedData extends SavedData {
    private final Map<UUID, Station> byNation = new LinkedHashMap<>();

    public Optional<Station> forNation(UUID nationId) {
        return Optional.ofNullable(byNation.get(nationId));
    }

    public Optional<Station> byId(UUID stationId) {
        return byNation.values().stream().filter(station -> station.id().equals(stationId)).findFirst();
    }

    public List<Station> all() { return List.copyOf(byNation.values()); }

    public boolean register(Station station) {
        if (station == null || byNation.containsKey(station.nationId())) return false;
        byNation.put(station.nationId(), station);
        setDirty();
        return true;
    }

    public boolean remove(UUID nationId, UUID stationId) {
        Station current = byNation.get(nationId);
        if (current == null || !current.id().equals(stationId)) return false;
        byNation.remove(nationId);
        setDirty();
        return true;
    }

    @Override public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        byNation.values().forEach(station -> {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("Id", station.id());
            entry.putUUID("Nation", station.nationId());
            entry.putString("Dimension", station.dimension().toString());
            entry.putLong("Pos", station.pos().asLong());
            list.add(entry);
        });
        tag.put("Stations", list);
        return tag;
    }

    public static MarketStationSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        MarketStationSavedData data = new MarketStationSavedData();
        ListTag list = tag.getList("Stations", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            if (!entry.hasUUID("Id") || !entry.hasUUID("Nation")) continue;
            ResourceLocation dimension = ResourceLocation.tryParse(entry.getString("Dimension"));
            if (dimension == null) continue;
            UUID nation = entry.getUUID("Nation");
            data.byNation.putIfAbsent(nation, new Station(entry.getUUID("Id"), nation,
                    dimension, BlockPos.of(entry.getLong("Pos"))));
        }
        return data;
    }

    public static MarketStationSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(MarketStationSavedData::new, MarketStationSavedData::load, null),
                "moveearth_market_stations");
    }

    public record Station(UUID id, UUID nationId, ResourceLocation dimension, BlockPos pos) {
        public Station {
            if (id == null || nationId == null || dimension == null || pos == null)
                throw new IllegalArgumentException("Incomplete market station");
            pos = pos.immutable();
        }
    }
}
