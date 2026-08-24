package com.ruskserver.moveearth_addtional.tpa;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Persistent, server-wide count of successful TPA teleports in the current opening-day cycle. */
public final class TpaUsageSavedData extends SavedData {
    public static final int DAILY_LIMIT = 5;
    private final Map<UUID, Usage> usages = new HashMap<>();

    public int used(UUID playerId) {
        return current(playerId).used;
    }

    public int remaining(UUID playerId) {
        return Math.max(0, DAILY_LIMIT - used(playerId));
    }

    public boolean tryConsume(UUID playerId) {
        Usage usage = current(playerId);
        if (usage.used >= DAILY_LIMIT) {
            return false;
        }
        usage.used++;
        setDirty();
        return true;
    }

    public void reset(UUID playerId) {
        usages.put(playerId, new Usage(OpenDayCycle.currentId(), 0));
        setDirty();
    }

    private Usage current(UUID playerId) {
        String cycle = OpenDayCycle.currentId();
        Usage usage = usages.get(playerId);
        if (usage == null || !usage.cycle.equals(cycle)) {
            usage = new Usage(cycle, 0);
            usages.put(playerId, usage);
            setDirty();
        }
        return usage;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        CompoundTag players = new CompoundTag();
        usages.forEach((playerId, usage) -> {
            CompoundTag usageTag = new CompoundTag();
            usageTag.putString("Cycle", usage.cycle);
            usageTag.putInt("Used", usage.used);
            players.put(playerId.toString(), usageTag);
        });
        tag.put("Players", players);
        return tag;
    }

    public static TpaUsageSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        TpaUsageSavedData data = new TpaUsageSavedData();
        CompoundTag players = tag.getCompound("Players");
        for (String key : players.getAllKeys()) {
            try {
                UUID playerId = UUID.fromString(key);
                CompoundTag usageTag = players.getCompound(key);
                data.usages.put(playerId, new Usage(
                        usageTag.getString("Cycle"),
                        Math.max(0, usageTag.getInt("Used"))));
            } catch (IllegalArgumentException ignored) {
            }
        }
        return data;
    }

    public static TpaUsageSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(TpaUsageSavedData::new, TpaUsageSavedData::load, null),
                "moveearth_tpa_usage");
    }

    private static final class Usage {
        private final String cycle;
        private int used;

        private Usage(String cycle, int used) {
            this.cycle = cycle;
            this.used = used;
        }
    }
}
