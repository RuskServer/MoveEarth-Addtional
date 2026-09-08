package com.ruskserver.moveearth_addtional.tpa;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Persistent TPA usage, beginner allowance, and role-based cooldown state. */
public final class TpaUsageSavedData extends SavedData {
    public static final int DAILY_LIMIT = 2;
    private final Map<UUID, Usage> usages = new HashMap<>();

    public int used(UUID playerId) {
        return current(playerId).used;
    }

    public int remaining(UUID playerId) {
        return Math.max(0, DAILY_LIMIT - used(playerId));
    }

    public int beginnerUsed(UUID playerId) {
        return current(playerId).beginnerUsed;
    }

    public int beginnerRemaining(UUID playerId, int allowance) {
        return Math.max(0, allowance - beginnerUsed(playerId));
    }

    public long travelerCooldownRemainingMillis(UUID playerId, long nowEpochMs) {
        return TpaPolicy.remainingMillis(current(playerId).travelerCooldownUntilEpochMs, nowEpochMs);
    }

    public long hostCooldownRemainingMillis(UUID playerId, long nowEpochMs) {
        return TpaPolicy.remainingMillis(current(playerId).hostCooldownUntilEpochMs, nowEpochMs);
    }

    public void recordSuccessfulTeleport(
            UUID travelerId,
            UUID hostId,
            TpaPolicy.Mode mode,
            long nowEpochMs,
            long travelerCooldownMillis,
            long hostCooldownMillis
    ) {
        Usage traveler = current(travelerId);
        Usage host = current(hostId);
        if (mode == TpaPolicy.Mode.BEGINNER) {
            traveler.beginnerUsed++;
        } else {
            traveler.used = Math.min(DAILY_LIMIT, traveler.used + 1);
            traveler.travelerCooldownUntilEpochMs = Math.max(
                    traveler.travelerCooldownUntilEpochMs,
                    nowEpochMs + Math.max(0L, travelerCooldownMillis)
            );
        }
        host.hostCooldownUntilEpochMs = Math.max(
                host.hostCooldownUntilEpochMs,
                nowEpochMs + Math.max(0L, hostCooldownMillis)
        );
        setDirty();
    }

    public void reset(UUID playerId) {
        usages.put(playerId, new Usage(OpenDayCycle.currentId(), 0, 0, 0L, 0L));
        setDirty();
    }

    private Usage current(UUID playerId) {
        String cycle = OpenDayCycle.currentId();
        Usage usage = usages.get(playerId);
        if (usage == null) {
            usage = new Usage(cycle, 0, 0, 0L, 0L);
            usages.put(playerId, usage);
            setDirty();
        } else if (!usage.cycle.equals(cycle)) {
            usage.cycle = cycle;
            usage.used = 0;
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
            usageTag.putInt("BeginnerUsed", usage.beginnerUsed);
            usageTag.putLong("TravelerCooldownUntilEpochMs", usage.travelerCooldownUntilEpochMs);
            usageTag.putLong("HostCooldownUntilEpochMs", usage.hostCooldownUntilEpochMs);
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
                        Math.max(0, usageTag.getInt("Used")),
                        Math.max(0, usageTag.getInt("BeginnerUsed")),
                        Math.max(0L, usageTag.getLong("TravelerCooldownUntilEpochMs")),
                        Math.max(0L, usageTag.getLong("HostCooldownUntilEpochMs"))));
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
        private String cycle;
        private int used;
        private int beginnerUsed;
        private long travelerCooldownUntilEpochMs;
        private long hostCooldownUntilEpochMs;

        private Usage(
                String cycle,
                int used,
                int beginnerUsed,
                long travelerCooldownUntilEpochMs,
                long hostCooldownUntilEpochMs
        ) {
            this.cycle = cycle;
            this.used = used;
            this.beginnerUsed = beginnerUsed;
            this.travelerCooldownUntilEpochMs = travelerCooldownUntilEpochMs;
            this.hostCooldownUntilEpochMs = hostCooldownUntilEpochMs;
        }
    }
}
