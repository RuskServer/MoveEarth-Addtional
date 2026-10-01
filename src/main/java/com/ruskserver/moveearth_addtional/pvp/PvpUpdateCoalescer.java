package com.ruskserver.moveearth_addtional.pvp;

/**
 * Collapses any number of change notifications into at most one broadcast per
 * {@code minIntervalTicks}. Callers mark it dirty when state changes and broadcast
 * when {@link #due(long)} says so, then report the send with {@link #sent(long)}.
 */
final class PvpUpdateCoalescer {
    private final int minIntervalTicks;
    private boolean dirty;
    private boolean everSent;
    private long lastSentTick;

    PvpUpdateCoalescer(int minIntervalTicks) {
        this.minIntervalTicks = Math.max(1, minIntervalTicks);
    }

    void markDirty() {
        dirty = true;
    }

    boolean isDirty() {
        return dirty;
    }

    boolean due(long now) {
        if (!dirty) return false;
        return !everSent || now < lastSentTick || now - lastSentTick >= minIntervalTicks;
    }

    void sent(long now) {
        dirty = false;
        everSent = true;
        lastSentTick = now;
    }

    void reset() {
        dirty = false;
        everSent = false;
        lastSentTick = 0L;
    }
}
