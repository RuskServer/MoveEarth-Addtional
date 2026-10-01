package com.ruskserver.moveearth_addtional.client;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

/** Whether a territory map refresh carries anything new. Pure logic, no game classes. */
final class TerritoryMapChange {
    private TerritoryMapChange() { }

    /**
     * Whether received cores and nations differ from what is cached for their dimension. Value
     * equality throughout: the packet entries are records.
     */
    static <C, N> boolean changes(List<C> cachedCores, Map<UUID, N> cachedNations,
                                  List<C> cores, List<N> nations, Function<N, UUID> nationId) {
        if (cachedCores == null || cachedNations == null) return true;
        if (!cachedCores.equals(cores) || cachedNations.size() != nations.size()) return true;
        for (N nation : nations) {
            if (!nation.equals(cachedNations.get(nationId.apply(nation)))) return true;
        }
        return false;
    }
}
