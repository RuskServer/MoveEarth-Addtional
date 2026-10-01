package com.ruskserver.moveearth_addtional.s2.nation;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Members who were offline when their nation was disbanded, and the nation's name, so they are told
 * at their next login. A player belongs to at most one nation, so one pending notice per player.
 */
public final class DisbandNoticeSavedData extends SavedData {
    private static final int MAX_NAME_LENGTH = 64;
    private final Map<UUID, String> pending = new LinkedHashMap<>();

    public void add(UUID playerId, String nationName) {
        if (playerId == null) return;
        pending.put(playerId, fit(nationName));
        setDirty();
    }

    /** Removes and returns the player's pending notice. */
    public Optional<String> take(UUID playerId) {
        String nationName = playerId == null ? null : pending.remove(playerId);
        if (nationName != null) setDirty();
        return Optional.ofNullable(nationName);
    }

    private static String fit(String value) {
        if (value == null) return "?";
        return value.length() > MAX_NAME_LENGTH ? value.substring(0, MAX_NAME_LENGTH) : value;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        pending.forEach((playerId, nationName) -> {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("Player", playerId);
            entry.putString("Nation", nationName);
            list.add(entry);
        });
        tag.put("Pending", list);
        return tag;
    }

    public static DisbandNoticeSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        DisbandNoticeSavedData data = new DisbandNoticeSavedData();
        ListTag list = tag.getList("Pending", Tag.TAG_COMPOUND);
        for (int index = 0; index < list.size(); index++) {
            CompoundTag entry = list.getCompound(index);
            if (entry.hasUUID("Player")) data.pending.put(entry.getUUID("Player"), fit(entry.getString("Nation")));
        }
        return data;
    }

    public static DisbandNoticeSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(DisbandNoticeSavedData::new, DisbandNoticeSavedData::load, null),
                "moveearth_disband_notices");
    }
}
