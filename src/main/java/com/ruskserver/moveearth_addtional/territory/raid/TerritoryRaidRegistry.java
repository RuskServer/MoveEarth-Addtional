package com.ruskserver.moveearth_addtional.territory.raid;

import com.ruskserver.moveearth_addtional.territory.domain.RaidEmitter;
import net.minecraft.server.MinecraftServer;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

public final class TerritoryRaidRegistry {
    private static final Map<MinecraftServer, Map<UUID, Entry>> ENTRIES = new WeakHashMap<>();

    private TerritoryRaidRegistry() {
    }

    public static void update(MinecraftServer server, RaidEmitter emitter, UUID shipId,
                              double validStress, long gameTime) {
        ENTRIES.computeIfAbsent(server, ignored -> new HashMap<>())
                .put(emitter.id(), new Entry(emitter, shipId, validStress, gameTime));
    }

    public static void remove(MinecraftServer server, UUID emitterId) {
        Map<UUID, Entry> entries = ENTRIES.get(server);
        if (entries == null) {
            return;
        }
        entries.remove(emitterId);
        if (entries.isEmpty()) {
            ENTRIES.remove(server);
        }
    }

    public static Collection<RaidEmitter> activeEmitters(MinecraftServer server, long gameTime) {
        Map<UUID, Entry> entries = ENTRIES.get(server);
        if (entries == null) {
            return List.of();
        }
        long staleAfter = Math.max(
                TerritoryRaidConfig.STALE_AFTER.get(),
                TerritoryRaidConfig.REFRESH_INTERVAL.get() * 2L
        );
        entries.values().removeIf(entry -> gameTime - entry.lastSeenGameTime() > staleAfter);
        if (entries.isEmpty()) {
            ENTRIES.remove(server);
            return List.of();
        }
        return entries.values().stream().map(Entry::emitter).toList();
    }

    public static void clear(MinecraftServer server) {
        ENTRIES.remove(server);
    }

    public record Entry(RaidEmitter emitter, UUID shipId, double validStress, long lastSeenGameTime) {
    }
}
