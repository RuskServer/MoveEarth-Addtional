package com.ruskserver.moveearth_addtional.oxygen;

/**
 * The remaining filter of the mask a player wears, kept in memory and written to the
 * item only now and then. Writing the item's data every tick made the helmet count as
 * "changed" every tick, which resent the equipment to the wearer and every tracker.
 *
 * <p>{@link #written()} is what the item holds; {@link #current()} is the truth. Their
 * difference is consumption the item does not show yet, and it must reach the item (or
 * a pending debt) before the ledger is dropped, or the filter would be refunded.</p>
 */
final class MaskFilterLedger {
    private final int flushIntervalTicks;
    private boolean tracking;
    private int current;
    private int written;
    private int ticksSinceFlush;

    MaskFilterLedger(int flushIntervalTicks) {
        this.flushIntervalTicks = Math.max(1, flushIntervalTicks);
    }

    /** Starts from the value the item holds. */
    void track(int stored) {
        tracking = true;
        current = Math.max(0, stored);
        written = current;
        ticksSinceFlush = 0;
    }

    void untrack() {
        tracking = false;
        current = 0;
        written = 0;
        ticksSinceFlush = 0;
    }

    boolean tracking() {
        return tracking;
    }

    int current() {
        return current;
    }

    int written() {
        return written;
    }

    /** Consumption the item does not show yet. */
    int unflushed() {
        return tracking ? Math.max(0, written - current) : 0;
    }

    void consume(int amount) {
        if (!tracking || amount <= 0) return;
        current = Math.max(0, current - amount);
    }

    void tick() {
        if (tracking && ticksSinceFlush < Integer.MAX_VALUE) ticksSinceFlush++;
    }

    /**
     * Due once the interval has passed with something to write, and at once when the
     * filter runs out so the item never shows filter that is gone.
     */
    boolean flushDue() {
        if (!tracking || current == written) return false;
        return ticksSinceFlush >= flushIntervalTicks || current == 0;
    }

    /** Records that the item now holds {@link #current()}; returns that value. */
    int markFlushed() {
        written = current;
        ticksSinceFlush = 0;
        return current;
    }
}
