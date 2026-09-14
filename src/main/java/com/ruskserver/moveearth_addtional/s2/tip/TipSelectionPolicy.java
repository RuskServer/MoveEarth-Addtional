package com.ruskserver.moveearth_addtional.s2.tip;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Pure unread-first selection used by the runtime and tests. */
public final class TipSelectionPolicy {
    private TipSelectionPolicy() { }

    public static String select(List<String> available, Set<String> seen, String previous, long entropy) {
        if (available == null || available.isEmpty()) return null;
        List<String> candidates = new ArrayList<>();
        for (String id : available) {
            if (id != null && !seen.contains(id)) candidates.add(id);
        }
        if (candidates.isEmpty()) {
            for (String id : available) {
                if (id != null && (available.size() == 1 || !id.equals(previous))) candidates.add(id);
            }
        }
        if (candidates.isEmpty()) return available.getFirst();
        return candidates.get(Math.floorMod(entropy, candidates.size()));
    }
}
