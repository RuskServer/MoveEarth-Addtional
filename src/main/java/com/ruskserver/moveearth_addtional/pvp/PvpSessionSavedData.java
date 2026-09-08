package com.ruskserver.moveearth_addtional.pvp;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Crash-safe storage for inventories and player state replaced by an active PvP match. */
public final class PvpSessionSavedData extends SavedData {
    private final Map<UUID, PvpPlayerSnapshot> snapshots = new HashMap<>();

    public void put(UUID playerId, PvpPlayerSnapshot snapshot) {
        snapshots.put(playerId, snapshot);
        setDirty();
    }

    public PvpPlayerSnapshot get(UUID playerId) {
        return snapshots.get(playerId);
    }

    public boolean contains(UUID playerId) {
        return snapshots.containsKey(playerId);
    }

    public void remove(UUID playerId) {
        if (snapshots.remove(playerId) != null) setDirty();
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        CompoundTag all = new CompoundTag();
        snapshots.forEach((id, snapshot) -> all.put(id.toString(), snapshot.save(registries)));
        tag.put("Snapshots", all);
        return tag;
    }

    public static PvpSessionSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        PvpSessionSavedData data = new PvpSessionSavedData();
        CompoundTag all = tag.getCompound("Snapshots");
        for (String key : all.getAllKeys()) {
            try {
                data.snapshots.put(UUID.fromString(key), PvpPlayerSnapshot.load(all.getCompound(key), registries));
            } catch (IllegalArgumentException ignored) {
            }
        }
        return data;
    }

    public static PvpSessionSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(PvpSessionSavedData::new, PvpSessionSavedData::load, null),
                "moveearth_pvp_sessions");
    }
}
