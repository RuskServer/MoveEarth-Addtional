package com.ruskserver.moveearth_addtional.compat.vehicle;

import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Every newly observing player session gets a complete grace period, including after a body reload. */
final class SableDebrisWarningWindow<P> {
    private final WeakReference<Object> body;
    private final Map<UUID, WeakReference<P>> recipients = new HashMap<>();
    private long since;
    private long graceTicks;

    SableDebrisWarningWindow(Object body, long now, long graceTicks) {
        this.body = new WeakReference<>(body);
        since = now;
        this.graceTicks = graceTicks;
    }

    boolean matches(Object body) { return this.body.get() == body; }
    long since() { return since; }
    long graceTicks() { return graceTicks; }
    /** Reloading a shorter config must not break a grace period already promised in a warning. */
    void extendGrace(long configuredTicks) { graceTicks = Math.max(graceTicks, configuredTicks); }

    boolean warn(UUID playerId, P session, long now) {
        var previous = recipients.get(playerId);
        if (previous != null && previous.get() == session && now >= since) return false;
        recipients.put(playerId, new WeakReference<>(session));
        since = now;
        return true;
    }
}
