package com.ruskserver.moveearth_addtional.pvp;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * PvPロードアウト一覧をワールドデータとして永続化するSavedData。
 */
public final class PvpLoadoutSavedData extends SavedData {

    public static final String DATA_NAME = "moveearth_pvp_loadouts";

    private final Map<String, PvpLoadoutDefinition> loadouts = new LinkedHashMap<>();

    public PvpLoadoutSavedData() {
        // 新規作成時はデフォルトプリセットを投入
        resetToDefaults();
    }

    public synchronized List<PvpLoadoutDefinition> getAll() {
        return Collections.unmodifiableList(new ArrayList<>(loadouts.values()));
    }

    public synchronized Optional<PvpLoadoutDefinition> getById(String id) {
        return Optional.ofNullable(loadouts.get(id));
    }

    public synchronized PvpLoadoutDefinition getOrDefault(String id) {
        PvpLoadoutDefinition def = loadouts.get(id);
        if (def != null) return def;
        if (!loadouts.isEmpty()) return loadouts.values().iterator().next();
        return PvpLoadoutPreset.defaultPreset().toDefinition();
    }

    public synchronized void addOrUpdate(PvpLoadoutDefinition definition) {
        loadouts.put(definition.id(), definition);
        setDirty();
    }

    public synchronized boolean delete(String id) {
        if (loadouts.size() <= 1) {
            // 最低1つは残す
            return false;
        }
        boolean removed = loadouts.remove(id) != null;
        if (removed) {
            setDirty();
        }
        return removed;
    }

    public synchronized void reorder(List<String> orderedIds) {
        Map<String, PvpLoadoutDefinition> reordered = new LinkedHashMap<>();
        for (String id : orderedIds) {
            PvpLoadoutDefinition def = loadouts.get(id);
            if (def != null) {
                reordered.put(id, def);
            }
        }
        // 残り（リストに含まれなかったもの）を末尾に追加
        for (Map.Entry<String, PvpLoadoutDefinition> entry : loadouts.entrySet()) {
            reordered.putIfAbsent(entry.getKey(), entry.getValue());
        }
        loadouts.clear();
        loadouts.putAll(reordered);
        setDirty();
    }

    public synchronized void resetToDefaults() {
        loadouts.clear();
        for (PvpLoadoutDefinition def : PvpLoadoutPreset.createDefaultDefinitions()) {
            loadouts.put(def.id(), def);
        }
        setDirty();
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (PvpLoadoutDefinition def : loadouts.values()) {
            list.add(def.save(registries));
        }
        tag.put("Loadouts", list);
        return tag;
    }

    public static PvpLoadoutSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        PvpLoadoutSavedData data = new PvpLoadoutSavedData();
        data.loadouts.clear();
        if (tag.contains("Loadouts", Tag.TAG_LIST)) {
            ListTag list = tag.getList("Loadouts", Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                PvpLoadoutDefinition def = PvpLoadoutDefinition.load(list.getCompound(i), registries);
                data.loadouts.put(def.id(), def);
            }
        }
        if (data.loadouts.isEmpty()) {
            data.resetToDefaults();
        }
        return data;
    }

    public static PvpLoadoutSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(PvpLoadoutSavedData::new, PvpLoadoutSavedData::load, null),
                DATA_NAME);
    }
}
