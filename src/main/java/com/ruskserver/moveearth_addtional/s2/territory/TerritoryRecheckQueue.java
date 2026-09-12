package com.ruskserver.moveearth_addtional.s2.territory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Small deterministic debounce queue used to keep closure scans off block-change event paths. */
final class TerritoryRecheckQueue<K> {
    private final Map<K, Long> dueTimes = new LinkedHashMap<>();

    void schedule(K key, long dueTime) {
        if (key == null) return;
        dueTimes.merge(key, Math.max(0L, dueTime), Math::min);
    }

    List<K> pollDue(long now, int limit) {
        int safeLimit = Math.max(0, limit);
        List<K> result = new ArrayList<>(safeLimit);
        var iterator = dueTimes.entrySet().iterator();
        while (iterator.hasNext() && result.size() < safeLimit) {
            Map.Entry<K, Long> entry = iterator.next();
            if (entry.getValue() > now) continue;
            result.add(entry.getKey());
            iterator.remove();
        }
        return List.copyOf(result);
    }

    int size() {
        return dueTimes.size();
    }

    void clear() {
        dueTimes.clear();
    }
}
