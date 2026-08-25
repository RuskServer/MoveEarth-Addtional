package com.ruskserver.moveearth_addtional.jobs;

import java.util.List;
import java.util.Set;

/** Pure selection helpers shared by persistent data, the Jobs GUI and unit tests. */
public final class JobSelectionRules {
    private JobSelectionRules() {
    }

    public static <T> boolean removeMissing(Set<T> active, Set<T> valid) {
        if (valid.isEmpty()) {
            return false;
        }
        return active.removeIf(value -> !valid.contains(value));
    }

    public static int indexOfId(List<String> visibleIds, String selectedId) {
        if (selectedId == null || selectedId.isEmpty()) {
            return 0;
        }
        int index = visibleIds.indexOf(selectedId);
        return index < 0 ? 0 : index;
    }
}
