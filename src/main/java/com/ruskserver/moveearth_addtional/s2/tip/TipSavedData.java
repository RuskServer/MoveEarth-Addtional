package com.ruskserver.moveearth_addtional.s2.tip;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Persistent per-player pacing, unread state, opt-out and recent tip history. */
public final class TipSavedData extends SavedData {
    private final Map<UUID, TipProgress> players = new LinkedHashMap<>();

    public TipProgress state(UUID playerId, int initialDelaySeconds) {
        TipProgress existing = players.get(playerId);
        if (existing != null) return existing;
        TipProgress created = new TipProgress(true, initialDelaySeconds);
        players.put(playerId, created);
        setDirty();
        return created;
    }

    public void advanceSecond(UUID playerId, int initialDelaySeconds) {
        TipProgress state = state(playerId, initialDelaySeconds);
        if (!state.enabled() || state.remainingSeconds() <= 0) return;
        state.advanceSecond();
        setDirty();
    }

    public void setEnabled(UUID playerId, boolean enabled, int initialDelaySeconds) {
        TipProgress state = state(playerId, initialDelaySeconds);
        if (state.enabled() == enabled) return;
        state.setEnabled(enabled, initialDelaySeconds);
        setDirty();
    }

    public void markShown(UUID playerId, String tipId, int intervalSeconds, int historySize) {
        TipProgress state = state(playerId, 0);
        state.markShown(tipId, intervalSeconds, historySize);
        setDirty();
    }

    public void resetSeen(UUID playerId) {
        TipProgress state = players.get(playerId);
        if (state == null || state.seen().isEmpty()) return;
        state.resetSeen();
        setDirty();
    }

    public List<String> history(UUID playerId) {
        TipProgress state = players.get(playerId);
        return state == null ? List.of() : state.history();
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag entries = new ListTag();
        for (Map.Entry<UUID, TipProgress> entry : players.entrySet()) {
            CompoundTag value = new CompoundTag();
            value.putUUID("Player", entry.getKey());
            TipProgress state = entry.getValue();
            value.putBoolean("Enabled", state.enabled());
            value.putInt("RemainingSeconds", state.remainingSeconds());
            ListTag seen = new ListTag();
            state.seen().forEach(id -> seen.add(StringTag.valueOf(id)));
            value.put("Seen", seen);
            ListTag history = new ListTag();
            state.history().forEach(id -> history.add(StringTag.valueOf(id)));
            value.put("History", history);
            entries.add(value);
        }
        tag.put("Players", entries);
        return tag;
    }

    public static TipSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        TipSavedData data = new TipSavedData();
        ListTag entries = tag.getList("Players", Tag.TAG_COMPOUND);
        for (int index = 0; index < entries.size(); index++) {
            CompoundTag value = entries.getCompound(index);
            if (!value.hasUUID("Player")) continue;
            TipProgress state = new TipProgress(
                    !value.contains("Enabled") || value.getBoolean("Enabled"),
                    Math.max(0, value.getInt("RemainingSeconds")));
            readStrings(value.getList("Seen", Tag.TAG_STRING), state::restoreSeen);
            readStrings(value.getList("History", Tag.TAG_STRING), state::restoreHistory);
            data.players.put(value.getUUID("Player"), state);
        }
        return data;
    }

    private static void readStrings(ListTag source, java.util.function.Consumer<String> target) {
        for (int index = 0; index < source.size(); index++) {
            String id = source.getString(index);
            if (TipCatalog.byId(id) != null) target.accept(id);
        }
    }

    public static TipSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(TipSavedData::new, TipSavedData::load, null),
                "moveearth_player_tips");
    }

}
