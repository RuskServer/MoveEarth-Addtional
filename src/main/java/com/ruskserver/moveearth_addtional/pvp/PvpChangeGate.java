package com.ruskserver.moveearth_addtional.pvp;

import java.util.Objects;

/**
 * Decides whether a broadcast packet is worth sending: only when the client-visible
 * content (the key) changed, or, if {@code keepAliveTicks > 0}, when that many ticks
 * have passed since the last send. Not thread-safe; used from the server thread.
 */
final class PvpChangeGate<K> {
    private final int keepAliveTicks;
    private K lastKey;
    private long lastSentTick;
    private boolean sent;

    PvpChangeGate(int keepAliveTicks) {
        this.keepAliveTicks = keepAliveTicks;
    }

    /** Returns true, and records the send, when {@code key} should go out now. */
    boolean shouldSend(K key, long now) {
        boolean due = !sent
                || !Objects.equals(lastKey, key)
                || now < lastSentTick
                || (keepAliveTicks > 0 && now - lastSentTick >= keepAliveTicks);
        if (due) {
            lastKey = key;
            lastSentTick = now;
            sent = true;
        }
        return due;
    }

    void reset() {
        lastKey = null;
        lastSentTick = 0L;
        sent = false;
    }
}
