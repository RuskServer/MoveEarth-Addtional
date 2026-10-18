package com.ruskserver.moveearth_addtional.pvp;

/**
 * Collapses any number of change notifications into at most one broadcast per
 * {@code minIntervalTicks}. Callers mark it dirty when state changes and broadcast
 * when {@link #due(long)} says so, then report the send with {@link #sent(long)}.
 */
public final class PvpUpdateCoalescer {
    private final int minIntervalTicks;
    private boolean dirty;
    private boolean everSent;
    private long lastSentTick;

    public PvpUpdateCoalescer(int minIntervalTicks) {
        this.minIntervalTicks = Math.max(1, minIntervalTicks);
    }

    public void markDirty() {
        dirty = true;
    }

    public boolean isDirty() {
        return dirty;
    }

    public boolean due(long now) {
        if (!dirty) return false;
        return !everSent || now < lastSentTick || now - lastSentTick >= minIntervalTicks;
    }

    public void sent(long now) {
        dirty = false;
        everSent = true;
        lastSentTick = now;
    }

    public void reset() {
        dirty = false;
        everSent = false;
        lastSentTick = 0L;
    }
}
