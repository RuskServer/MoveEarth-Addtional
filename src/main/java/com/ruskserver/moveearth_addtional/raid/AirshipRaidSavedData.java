package com.ruskserver.moveearth_addtional.raid;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class AirshipRaidSavedData extends SavedData {
    private boolean automaticEnabled;
    private long lastAutomaticCheck;
    private int nextRaidId = 1;
    private final Map<UUID, Long> playerCooldowns = new HashMap<>();

    public boolean isAutomaticEnabled() {
        return automaticEnabled;
    }

    public void setAutomaticEnabled(boolean enabled) {
        if (automaticEnabled != enabled) {
            automaticEnabled = enabled;
            setDirty();
        }
    }

    public long getLastAutomaticCheck() {
        return lastAutomaticCheck;
    }

    public void setLastAutomaticCheck(long gameTime) {
        lastAutomaticCheck = gameTime;
        setDirty();
    }

    public int allocateRaidId() {
        int id = nextRaidId++;
        setDirty();
        return id;
    }

    public long getLastRaidTime(UUID playerId) {
        return playerCooldowns.getOrDefault(playerId, Long.MIN_VALUE);
    }

    public void recordRaid(UUID playerId, long gameTime) {
        playerCooldowns.put(playerId, gameTime);
        setDirty();
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putBoolean("AutomaticEnabled", automaticEnabled);
        tag.putLong("LastAutomaticCheck", lastAutomaticCheck);
        tag.putInt("NextRaidId", nextRaidId);
        CompoundTag cooldownTag = new CompoundTag();
        playerCooldowns.forEach((id, time) -> cooldownTag.putLong(id.toString(), time));
        tag.put("PlayerCooldowns", cooldownTag);
        return tag;
    }

    public static AirshipRaidSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        AirshipRaidSavedData data = new AirshipRaidSavedData();
        data.automaticEnabled = tag.getBoolean("AutomaticEnabled");
        data.lastAutomaticCheck = tag.getLong("LastAutomaticCheck");
        data.nextRaidId = Math.max(1, tag.getInt("NextRaidId"));
        CompoundTag cooldownTag = tag.getCompound("PlayerCooldowns");
        for (String key : cooldownTag.getAllKeys()) {
            try {
                data.playerCooldowns.put(UUID.fromString(key), cooldownTag.getLong(key));
            } catch (IllegalArgumentException ignored) {
            }
        }
        return data;
    }

    public static AirshipRaidSavedData get(MinecraftServer server) {
        ServerLevel level = server.overworld();
        return level.getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(AirshipRaidSavedData::new, AirshipRaidSavedData::load, null),
                "moveearth_airship_raids");
    }
}
