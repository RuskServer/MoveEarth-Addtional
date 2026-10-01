package com.ruskserver.moveearth_addtional.s2.territory;

import java.util.UUID;

/**
 * How long a core still being configured may hold land. A configuring core pays
 * no upkeep and cannot be besieged, so without a limit a free nation could hold
 * up to 81 chunks at a chokepoint indefinitely by never asking for validation.
 *
 * <p>For outposts the hour belongs to the land, not to the core: an outpost that
 * starts configuring on land overlapping one of its nation's configuring outposts,
 * or one removed while configuring within {@link #REUSE_COOLDOWN_OPEN_TICKS}, takes
 * over the earlier start. Breaking and re-placing an outpost (a new core id), or
 * placing a second one beside a lapsed one, therefore does not restart the hour.
 * Capitals keep the plain per-core hour.
 */
public final class ConfiguringReservationPolicy {
    /** One hour of server-open time, in ticks. */
    public static final long LIMIT_OPEN_TICKS = 60L * 60L * 20L;
    /**
     * How long, in server-open ticks, the land of an outpost removed while configuring stays tied
     * to its start. After this the nation may reserve that land afresh.
     */
    public static final long REUSE_COOLDOWN_OPEN_TICKS = 24L * 60L * 60L * 20L;

    private ConfiguringReservationPolicy() { }

    /** Whether a reservation that began at {@code sinceOpenTicks} still holds at {@code nowOpenTicks}. */
    public static boolean live(long sinceOpenTicks, long nowOpenTicks) {
        return nowOpenTicks - sinceOpenTicks < LIMIT_OPEN_TICKS;
    }

    /** Whether the land of an outpost removed at {@code removedAtOpenTicks} is still tied to its start. */
    public static boolean retained(long removedAtOpenTicks, long nowOpenTicks) {
        return nowOpenTicks - removedAtOpenTicks < REUSE_COOLDOWN_OPEN_TICKS;
    }

    /** Land an outpost's configuring reservation covers, and when its hour started. */
    public record Claim(UUID nationId, String dimension, TerritoryPreviewArea area, long since) {
        public boolean sameLand(UUID otherNation, String otherDimension, TerritoryPreviewArea otherArea) {
            return nationId.equals(otherNation) && dimension.equals(otherDimension) && area.overlaps(otherArea);
        }
    }

    /**
     * The start an outpost's reservation counts from: the earliest start among {@code claims} of the
     * same nation on overlapping land in the same dimension, never later than {@code ownStart}.
     *
     * @param ownStart the outpost's own start, or the current open time when it is new
     */
    public static long start(UUID nationId, String dimension, TerritoryPreviewArea area, long ownStart,
                             Iterable<Claim> claims) {
        long start = ownStart;
        for (Claim claim : claims) {
            if (claim.sameLand(nationId, dimension, area)) start = Math.min(start, claim.since());
        }
        return start;
    }
}
