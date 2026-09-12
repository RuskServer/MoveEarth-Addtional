package com.ruskserver.moveearth_addtional.s2.territory;

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

/** Persists the last safe position needed to recover restricted vehicle occupants. */
final class BastionPlayerSavedData extends SavedData {
    private final Map<UUID, PlayerState> players = new LinkedHashMap<>();

    Optional<SafePosition> safePosition(UUID playerId) {
        PlayerState state = players.get(playerId);
        return Optional.ofNullable(state == null ? null : state.safePosition);
    }

    boolean returnRequired(UUID playerId) {
        PlayerState state = players.get(playerId);
        return state != null && state.returnRequired;
    }

    void recordSafe(UUID playerId, SafePosition position) {
        PlayerState state = players.computeIfAbsent(playerId, ignored -> new PlayerState());
        SafePosition previous = state.safePosition;
        if (previous != null && previous.dimension.equals(position.dimension)) {
            double dx = previous.x - position.x;
            double dy = previous.y - position.y;
            double dz = previous.z - position.z;
            if (dx * dx + dy * dy + dz * dz < 4.0D) return;
        }
        state.safePosition = position;
        setDirty();
    }

    void requireReturn(UUID playerId) {
        PlayerState state = players.computeIfAbsent(playerId, ignored -> new PlayerState());
        if (state.returnRequired) return;
        state.returnRequired = true;
        setDirty();
    }

    void clearReturn(UUID playerId) {
        PlayerState state = players.get(playerId);
        if (state == null || !state.returnRequired) return;
        state.returnRequired = false;
        setDirty();
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag entries = new ListTag();
        for (Map.Entry<UUID, PlayerState> entry : players.entrySet()) {
            PlayerState state = entry.getValue();
            if (state.safePosition == null && !state.returnRequired) continue;
            CompoundTag value = new CompoundTag();
            value.putUUID("Player", entry.getKey());
            value.putBoolean("ReturnRequired", state.returnRequired);
            if (state.safePosition != null) {
                SafePosition safe = state.safePosition;
                value.putString("Dimension", safe.dimension.toString());
                value.putDouble("X", safe.x);
                value.putDouble("Y", safe.y);
                value.putDouble("Z", safe.z);
                value.putFloat("Yaw", safe.yaw);
                value.putFloat("Pitch", safe.pitch);
            }
            entries.add(value);
        }
        tag.put("Players", entries);
        return tag;
    }

    static BastionPlayerSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        BastionPlayerSavedData data = new BastionPlayerSavedData();
        ListTag entries = tag.getList("Players", Tag.TAG_COMPOUND);
        for (int index = 0; index < entries.size(); index++) {
            CompoundTag value = entries.getCompound(index);
            if (!value.hasUUID("Player")) continue;
            PlayerState state = new PlayerState();
            state.returnRequired = value.getBoolean("ReturnRequired");
            if (value.contains("Dimension", Tag.TAG_STRING)) {
                try {
                    state.safePosition = new SafePosition(
                            ResourceLocation.parse(value.getString("Dimension")),
                            value.getDouble("X"), value.getDouble("Y"), value.getDouble("Z"),
                            value.getFloat("Yaw"), value.getFloat("Pitch"));
                } catch (IllegalArgumentException ignored) {
                }
            }
            data.players.put(value.getUUID("Player"), state);
        }
        return data;
    }

    static BastionPlayerSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(BastionPlayerSavedData::new, BastionPlayerSavedData::load, null),
                "moveearth_bastion_players");
    }

    record SafePosition(ResourceLocation dimension, double x, double y, double z, float yaw, float pitch) {
    }

    private static final class PlayerState {
        private SafePosition safePosition;
        private boolean returnRequired;
    }
}
