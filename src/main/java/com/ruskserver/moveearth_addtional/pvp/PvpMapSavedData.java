package com.ruskserver.moveearth_addtional.pvp;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.*;

public final class PvpMapSavedData extends SavedData {
    private final LinkedHashMap<String, PvpMapDefinition> maps = new LinkedHashMap<>();

    public PvpMapSavedData() {
        if (maps.isEmpty()) {
            resetToDefaults();
        }
    }

    public List<PvpMapDefinition> getAll() {
        return List.copyOf(maps.values());
    }

    public List<PvpMapDefinition> getEnabledAndConfigured() {
        return maps.values().stream()
                .filter(PvpMapDefinition::enabled)
                .filter(PvpMapDefinition::isConfigured)
                .toList();
    }

    public Optional<PvpMapDefinition> getById(String id) {
        if (id == null) return Optional.empty();
        return Optional.ofNullable(maps.get(id.toLowerCase(Locale.ROOT)));
    }

    public PvpMapDefinition getOrDefault(String id) {
        return getById(id).orElseGet(() -> maps.values().stream().findFirst().orElseGet(PvpMapDefinition::createDefault));
    }

    public void addOrUpdate(PvpMapDefinition map) {
        maps.put(map.id(), map);
        setDirty();
    }

    public boolean delete(String id) {
        if (id == null) return false;
        String key = id.toLowerCase(Locale.ROOT);
        if (maps.size() <= 1 && maps.containsKey(key)) {
            return false; // 最低1つは維持
        }
        if (maps.remove(key) != null) {
            setDirty();
            return true;
        }
        return false;
    }

    public void resetToDefaults() {
        maps.clear();
        addOrUpdate(PvpMapDefinition.createDefault());
        setDirty();
    }

    public void importFromArenaDataIfDefault(PvpArenaSavedData arenaData) {
        PvpMapDefinition def = maps.get("default");
        if (def != null && arenaData.configured()) {
            PvpMapDefinition updated = new PvpMapDefinition(
                    "default",
                    "標準アリーナ",
                    def.description(),
                    arenaData.redSpawn(),
                    arenaData.blueSpawn(),
                    def.extraRedSpawns(),
                    def.extraBlueSpawns(),
                    arenaData.hillMin(),
                    arenaData.hillMax(),
                    def.cardColor(),
                    true
            );
            maps.put("default", updated);
            setDirty();
        }
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (PvpMapDefinition map : maps.values()) {
            list.add(map.save());
        }
        tag.put("Maps", list);
        return tag;
    }

    public static PvpMapSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        PvpMapSavedData data = new PvpMapSavedData();
        data.maps.clear();
        ListTag list = tag.getList("Maps", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            PvpMapDefinition map = PvpMapDefinition.load(list.getCompound(i));
            data.maps.put(map.id(), map);
        }
        if (data.maps.isEmpty()) {
            data.resetToDefaults();
        }
        return data;
    }

    public static PvpMapSavedData get(MinecraftServer server) {
        PvpMapSavedData data = server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(PvpMapSavedData::new, PvpMapSavedData::load, null),
                "moveearth_pvp_maps");
        // 既存のPvpArenaSavedDataがあれば座標を引き継ぐ
        PvpArenaSavedData arena = PvpArenaSavedData.get(server);
        if (arena.configured()) {
            data.importFromArenaDataIfDefault(arena);
        }
        return data;
    }
}
