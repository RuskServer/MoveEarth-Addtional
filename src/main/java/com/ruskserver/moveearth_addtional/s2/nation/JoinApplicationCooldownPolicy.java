package com.ruskserver.moveearth_addtional.s2.nation;

/**
 * Paces a player's own join applications. Each application notifies the target nation's managers and
 * records analytics, so after a successful apply or cancel the next application waits
 * {@link #COOLDOWN_MILLIS}. Cancelling is never delayed: it needs an open application, so it is already
 * bounded by how often a player may apply.
 */
final class JoinApplicationCooldownPolicy {
    static final long COOLDOWN_MILLIS = 15_000L;

    private JoinApplicationCooldownPolicy() {
    }

    /** @param lastChangeAt wall-clock millis of the player's last successful apply or cancel, or null */
    static boolean applyAllowed(Long lastChangeAt, long now) {
        // A clock that went backwards must not lock the player out.
        return lastChangeAt == null || now < lastChangeAt || now - lastChangeAt >= COOLDOWN_MILLIS;
    }
}
