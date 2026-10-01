package com.ruskserver.moveearth_addtional.pvp;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Per-player change detection for the PvP entry-state packet.
 *
 * <p>The packet only updates an open PvP screen, which receives a fresh state when it
 * is opened, so a player needs a packet only when what they would see changed. A
 * change to their own flags (registered/active) or to hosting/running is sent at the
 * next flush. A change of the entry count alone, for a player who is not taking part,
 * is spaced to at most one packet per {@code countOnlyIntervalTicks}; the caller keeps
 * flushing while {@link Decision#DEFER} is returned, so the latest count still arrives.
 */
final class PvpEntryStateSync {
    enum Decision { SEND, DEFER, SKIP }

    record State(boolean joined, boolean active, boolean hosting, boolean running, int entries) {
        boolean involved() {
            return joined || active;
        }

        boolean differsOnlyInEntries(State other) {
            return joined == other.joined && active == other.active && hosting == other.hosting
                    && running == other.running && entries != other.entries;
        }
    }

    private record Sent(State state, long tick) {}

    private final int countOnlyIntervalTicks;
    private final Map<UUID, Sent> lastSent = new HashMap<>();

    PvpEntryStateSync(int countOnlyIntervalTicks) {
        this.countOnlyIntervalTicks = Math.max(0, countOnlyIntervalTicks);
    }

    /** Decides for one player; a {@link Decision#SEND} is recorded as sent at {@code now}. */
    Decision decide(UUID playerId, State current, long now) {
        Sent last = lastSent.get(playerId);
        if (last != null && last.state().equals(current)) return Decision.SKIP;
        if (last != null && !current.involved() && current.differsOnlyInEntries(last.state())
                && now >= last.tick() && now - last.tick() < countOnlyIntervalTicks) {
            return Decision.DEFER;
        }
        lastSent.put(playerId, new Sent(current, now));
        return Decision.SEND;
    }

    void forget(UUID playerId) {
        lastSent.remove(playerId);
    }

    void retainOnly(Collection<UUID> online) {
        lastSent.keySet().retainAll(online);
    }

    void clear() {
        lastSent.clear();
    }
}
