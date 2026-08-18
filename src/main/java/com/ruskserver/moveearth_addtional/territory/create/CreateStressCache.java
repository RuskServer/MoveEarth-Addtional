package com.ruskserver.moveearth_addtional.territory.create;

import com.ruskserver.moveearth_addtional.territory.domain.TerritoryOwnerId;
import net.minecraft.server.MinecraftServer;

import java.util.Map;
import java.util.WeakHashMap;

public final class CreateStressCache {
    private static final Map<MinecraftServer, Map<TerritoryOwnerId, CreateStressSnapshot>> SNAPSHOTS =
            new WeakHashMap<>();

    private CreateStressCache() {
    }

    public static synchronized CreateStressSnapshot get(MinecraftServer server, TerritoryOwnerId ownerId) {
        return SNAPSHOTS.getOrDefault(server, Map.of())
                .getOrDefault(ownerId, CreateStressSnapshot.empty(server.overworld().getGameTime()));
    }

    static synchronized void replace(MinecraftServer server,
                                     Map<TerritoryOwnerId, CreateStressSnapshot> snapshots) {
        SNAPSHOTS.put(server, Map.copyOf(snapshots));
    }

    public static synchronized void clear(MinecraftServer server) {
        SNAPSHOTS.remove(server);
    }
}
