package com.ruskserver.moveearth_addtional.s2.nation;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** What one viewer must be told so its nameplates match the server; pure, for {@link NationNameplateSync}. */
final class NameplateDelta {
    record Change<E>(List<E> upserts, List<UUID> removed) {
        boolean isEmpty() {
            return upserts.isEmpty() && removed.isEmpty();
        }
    }

    /** One packet's worth of an update; only the first chunk of a full list replaces. */
    record Chunk<E>(boolean replace, List<E> entries, List<UUID> removed) { }

    private NameplateDelta() {
    }

    /**
     * Compares what the viewer was last sent with what it should now see, and records the result as sent.
     *
     * @param sent    the viewer's last-sent entries by target; updated in place
     * @param desired current entries for the targets that were recomputed (others are left as they are)
     * @param online  players still online; any other target in {@code sent} is removed
     */
    static <E> Change<E> apply(Map<UUID, E> sent, Map<UUID, E> desired, Set<UUID> online) {
        List<UUID> removed = new ArrayList<>();
        var iterator = sent.keySet().iterator();
        while (iterator.hasNext()) {
            UUID target = iterator.next();
            if (online.contains(target)) continue;
            removed.add(target);
            iterator.remove();
        }
        List<E> upserts = new ArrayList<>();
        desired.forEach((target, entry) -> {
            if (!online.contains(target)) return;
            if (Objects.equals(sent.put(target, entry), entry)) return;
            upserts.add(entry);
        });
        return new Change<>(List.copyOf(upserts), List.copyOf(removed));
    }

    /** Splits an update into chunks of at most {@code max} entries and removals each. */
    static <E> List<Chunk<E>> chunks(boolean replace, List<E> entries, List<UUID> removed, int max) {
        List<Chunk<E>> chunks = new ArrayList<>();
        int index = 0;
        int removedIndex = 0;
        do {
            int entryEnd = Math.min(entries.size(), index + max);
            int removedEnd = Math.min(removed.size(), removedIndex + max);
            chunks.add(new Chunk<>(replace && chunks.isEmpty(), List.copyOf(entries.subList(index, entryEnd)),
                    List.copyOf(removed.subList(removedIndex, removedEnd))));
            index = entryEnd;
            removedIndex = removedEnd;
        } while (index < entries.size() || removedIndex < removed.size());
        return chunks;
    }
}
