package com.ruskserver.moveearth_addtional.s2.nation;

/**
 * When a chest left outside every nation's land opens to anyone. Pure, so it is tested without a server.
 *
 * <p>A nation's chests lie outside its land only after the land was lost: a fall, a shrunk capital, a disbanded
 * nation. They used to stay locked for good, with their contents, and for everyone once the nation was gone. The
 * owner keeps them to itself for a while after the land goes, and is told, then anyone may take what is inside.
 */
public final class AbandonedStoragePolicy {
    /** Open hours the owner keeps an abandoned chest to itself. */
    public static final int GRACE_OPEN_HOURS = 4;
    public static final long GRACE_OPEN_TICKS = GRACE_OPEN_HOURS * 60L * 60L * 20L;

    private AbandonedStoragePolicy() {
    }

    /** Whether a chest first seen outside every nation's land at {@code lostAt} is open to anyone at {@code now}. */
    public static boolean open(long lostAt, long now) {
        return lostAt > 0L && now - lostAt >= GRACE_OPEN_TICKS;
    }

    /** Open-time ticks left before it opens; 0 once open, or when it was never seen abandoned. */
    public static long remaining(long lostAt, long now) {
        return lostAt <= 0L ? 0L : Math.max(0L, lostAt + GRACE_OPEN_TICKS - now);
    }
}
