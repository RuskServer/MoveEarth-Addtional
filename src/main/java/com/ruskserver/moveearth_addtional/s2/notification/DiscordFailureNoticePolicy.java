package com.ruskserver.moveearth_addtional.s2.notification;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Server-thread-only, per-recipient throttling; final loss is separate from retry warnings. */
public final class DiscordFailureNoticePolicy {
    public static final long COOLDOWN_MILLIS = 5 * 60_000L;
    private final Map<Key, Long> notices = new HashMap<>();

    public boolean allow(UUID nation, UUID recipient, boolean lost, long now) {
        notices.entrySet().removeIf(entry -> now < entry.getValue()
                || now - entry.getValue() >= COOLDOWN_MILLIS);
        return notices.putIfAbsent(new Key(nation, recipient, lost), now) == null;
    }

    public void clear() { notices.clear(); }

    private record Key(UUID nation, UUID recipient, boolean lost) { }
}
