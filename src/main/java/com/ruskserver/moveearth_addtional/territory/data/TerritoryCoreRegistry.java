package com.ruskserver.moveearth_addtional.territory.data;

import com.ruskserver.moveearth_addtional.territory.domain.TerritoryCore;
import com.ruskserver.moveearth_addtional.territory.domain.TerritoryPosition;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public final class TerritoryCoreRegistry {
    private final Map<UUID, TerritoryCore> cores = new LinkedHashMap<>();

    public boolean register(TerritoryCore core) {
        Objects.requireNonNull(core, "core");
        List<UUID> staleAtPosition = cores.values().stream()
                .filter(existing -> !existing.id().equals(core.id()))
                .filter(existing -> sameBlock(existing.position(), core.position()))
                .map(TerritoryCore::id)
                .toList();
        staleAtPosition.forEach(cores::remove);

        TerritoryCore previous = cores.put(core.id(), core);
        return !core.equals(previous) || !staleAtPosition.isEmpty();
    }

    public boolean unregister(UUID coreId) {
        return coreId != null && cores.remove(coreId) != null;
    }

    public boolean unregisterAt(String dimensionId, int x, int y, int z) {
        List<UUID> matching = cores.values().stream()
                .filter(core -> core.position().dimensionId().equals(dimensionId))
                .filter(core -> blockX(core.position()) == x)
                .filter(core -> blockY(core.position()) == y)
                .filter(core -> blockZ(core.position()) == z)
                .map(TerritoryCore::id)
                .toList();
        matching.forEach(cores::remove);
        return !matching.isEmpty();
    }

    public Collection<TerritoryCore> allCores() {
        return List.copyOf(cores.values());
    }

    public Collection<TerritoryCore> coresIn(String dimensionId) {
        return cores.values().stream()
                .filter(core -> core.position().dimensionId().equals(dimensionId))
                .toList();
    }

    public int size() {
        return cores.size();
    }

    private static boolean sameBlock(TerritoryPosition first, TerritoryPosition second) {
        return first.dimensionId().equals(second.dimensionId())
                && blockX(first) == blockX(second)
                && blockY(first) == blockY(second)
                && blockZ(first) == blockZ(second);
    }

    private static int blockX(TerritoryPosition position) {
        return (int) Math.floor(position.x());
    }

    private static int blockY(TerritoryPosition position) {
        return (int) Math.floor(position.y());
    }

    private static int blockZ(TerritoryPosition position) {
        return (int) Math.floor(position.z());
    }
}
