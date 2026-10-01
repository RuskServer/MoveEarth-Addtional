package com.ruskserver.moveearth_addtional.oxygen;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Filter consumed by a mask that left its wearer's head before the consumption was
 * written, and could not be found to write it then. The debt is charged the next time
 * any player puts that mask on, so swapping masks quickly never refunds filter.
 */
final class PendingFilterDebts {
    private final int capacity;
    private final long lifetimeMillis;
    private final Map<UUID, Entry> debts = new LinkedHashMap<>();

    PendingFilterDebts(int capacity, long lifetimeMillis) {
        this.capacity = Math.max(1, capacity);
        this.lifetimeMillis = Math.max(1L, lifetimeMillis);
    }

    void add(UUID maskId, int ticks, long nowMillis) {
        if (maskId == null || ticks <= 0) return;
        expire(nowMillis);
        Entry previous = debts.remove(maskId);
        int total = previous == null ? ticks : (int) Math.min(Integer.MAX_VALUE, (long) previous.ticks + ticks);
        debts.put(maskId, new Entry(total, nowMillis));
        while (debts.size() > capacity) {
            Iterator<UUID> oldest = debts.keySet().iterator();
            oldest.next();
            oldest.remove();
        }
    }

    /** The debt for {@code maskId}, removed; zero when there is none or it expired. */
    int take(UUID maskId, long nowMillis) {
        if (maskId == null) return 0;
        expire(nowMillis);
        Entry entry = debts.remove(maskId);
        return entry == null ? 0 : entry.ticks;
    }

    int size() {
        return debts.size();
    }

    void clear() {
        debts.clear();
    }

    private void expire(long nowMillis) {
        // Insertion order is age order, because add() re-inserts.
        Iterator<Entry> iterator = debts.values().iterator();
        while (iterator.hasNext()) {
            if (nowMillis - iterator.next().createdMillis < lifetimeMillis) break;
            iterator.remove();
        }
    }

    private record Entry(int ticks, long createdMillis) { }
}
