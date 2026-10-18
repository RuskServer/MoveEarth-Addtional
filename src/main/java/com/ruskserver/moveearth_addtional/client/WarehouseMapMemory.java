package com.ruskserver.moveearth_addtional.client;

import com.ruskserver.moveearth_addtional.warehouse.WarehouseZoneView;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The Warehouses a player has been near, kept so their maps can show them later. The server only ever
 * tells a player about Warehouses within a short range, so nothing here is more than the player has
 * already been shown; a remembered site that should be in range but is no longer reported has gone and
 * is forgotten. Pure, so it is unit tested.
 */
final class WarehouseMapMemory {
    record Site(String dimension, int minX, int minY, int minZ) { }

    private final Set<Site> sites = new LinkedHashSet<>();

    /**
     * Adds every site now reported and drops remembered ones in {@code dimension} within {@code forgetRadius}
     * of the player that are not reported. Returns whether anything changed.
     */
    boolean reconcile(String dimension, Collection<Site> reported, double playerX, double playerZ,
                      double forgetRadius) {
        Set<Site> current = new HashSet<>(reported);
        boolean changed = sites.removeIf(site -> site.dimension().equals(dimension) && !current.contains(site)
                && WarehouseZoneView.distanceToFootprint(site.minX(), site.minZ(), playerX, playerZ) <= forgetRadius);
        for (Site site : reported) changed |= sites.add(site);
        return changed;
    }

    List<Site> in(String dimension) {
        List<Site> result = new ArrayList<>();
        for (Site site : sites) if (site.dimension().equals(dimension)) result.add(site);
        return List.copyOf(result);
    }

    int size() {
        return sites.size();
    }

    /** One site per line: dimension, then the footprint's minimum x, y and z. */
    String serialize() {
        StringBuilder text = new StringBuilder();
        for (Site site : sites) {
            text.append(site.dimension()).append(' ').append(site.minX()).append(' ')
                    .append(site.minY()).append(' ').append(site.minZ()).append('\n');
        }
        return text.toString();
    }

    /** Reads {@link #serialize()} output, skipping lines that do not parse. */
    static WarehouseMapMemory parse(String text) {
        WarehouseMapMemory memory = new WarehouseMapMemory();
        for (String line : text.split("\n")) {
            String[] parts = line.trim().split(" ");
            if (parts.length != 4 || parts[0].isBlank()) continue;
            try {
                memory.sites.add(new Site(parts[0], Integer.parseInt(parts[1]), Integer.parseInt(parts[2]),
                        Integer.parseInt(parts[3])));
            } catch (NumberFormatException ignored) {
                // A hand-edited or truncated line costs that one site only.
            }
        }
        return memory;
    }
}
