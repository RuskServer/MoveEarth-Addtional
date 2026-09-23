package com.ruskserver.moveearth_addtional.s2.nation;

/**
 * Whether a capital core may be founded at a block, and if not, why.
 *
 * <p>One rule, read by both sides. The client picker used to check a single
 * condition of the nine the server enforces, so the selection box went green on
 * ground the server would refuse and the refusal came back as one sentence
 * listing three possible causes. Nothing crashed; the player simply could not
 * find out what was wrong. Measuring the two paths against each other is the
 * only thing that keeps them honest, so the decision lives here and neither
 * side is allowed its own version of it.
 *
 * <p>Deliberately free of Minecraft types: each side reads its own world and
 * hands the answers over, which is what makes the ordering below testable.
 */
public final class NationFoundationSite {

    /** How far from the player a capital may be founded, in blocks. */
    public static final double REACH = 10.0D;

    /** The same limit squared, because both sides compare squared distances. */
    public static final double REACH_SQR = REACH * REACH;

    private NationFoundationSite() { }

    /**
     * What each side observed about the candidate block.
     *
     * @param loaded             the chunk holding the block is loaded
     * @param withinWorld        inside build height and the world border
     * @param withinReach        no further from the player than {@link #REACH}
     * @param sightClear         the player can actually see the supporting block.
     *                           The server traces this itself rather than trusting
     *                           the client, which is the point of checking it
     * @param onVehicle          the block belongs to a Sable sub-level, i.e. a
     *                           vehicle rather than the world
     * @param replaceable        the block itself gives way to the core. Grass,
     *                           flowers and a single snow layer all do
     * @param flooded            the block holds a fluid
     * @param supported          the block below offers a sturdy upward face
     * @param occupiedByEntity   something non-spectator stands in the block
     */
    public record Reading(boolean loaded, boolean withinWorld, boolean withinReach,
                          boolean sightClear, boolean onVehicle, boolean replaceable,
                          boolean flooded, boolean supported, boolean occupiedByEntity) {

        /**
         * A reading of a block nobody can see into.
         *
         * <p>Named rather than assembled by the caller so that no side has to
         * decide what the other eight fields mean when the chunk is absent;
         * {@link #judge} answers {@link Verdict#UNLOADED} before it reads them.
         */
        public static Reading unloaded() {
            return new Reading(false, false, false, false, false, false, false, false, false);
        }
    }

    /**
     * The single reason a site was refused, or {@link #OK}.
     *
     * <p>Each carries its own translation key so that the overlay a player reads
     * while aiming and the message they get after pressing the button are the
     * same sentence — they were two different sentences before, and the pair
     * could drift without anything noticing.
     */
    public enum Verdict {
        OK(null),
        UNLOADED("unloaded"),
        OUT_OF_WORLD("out_of_world"),
        TOO_FAR("too_far"),
        NO_SIGHT("no_sight"),
        ON_VEHICLE("on_vehicle"),
        OCCUPIED("occupied"),
        FLOODED("flooded"),
        NO_SUPPORT("no_support"),
        ENTITY_IN_THE_WAY("entity");

        private static final String PREFIX = "message.moveearth_addtional.nation.foundation.";

        private final String suffix;

        Verdict(String suffix) {
            this.suffix = suffix;
        }

        public boolean allowed() { return this == OK; }

        /** The sentence to show. Never called for {@link #OK}, which has nothing to say. */
        public String messageKey() {
            if (suffix == null) {
                throw new IllegalStateException("OK carries no message");
            }
            return PREFIX + suffix;
        }
    }

    /**
     * Judges a site, reporting the first thing wrong with it.
     *
     * <p>The order is what a player can act on, not what is cheapest to test:
     * being out of range or unable to see the spot is worth saying before
     * anything about the block itself, because the block they are being told
     * about may not even be the one they think they are aiming at.
     */
    public static Verdict judge(Reading reading) {
        if (!reading.loaded()) return Verdict.UNLOADED;
        if (!reading.withinWorld()) return Verdict.OUT_OF_WORLD;
        if (!reading.withinReach()) return Verdict.TOO_FAR;
        if (!reading.sightClear()) return Verdict.NO_SIGHT;
        if (reading.onVehicle()) return Verdict.ON_VEHICLE;
        if (!reading.replaceable()) return Verdict.OCCUPIED;
        if (reading.flooded()) return Verdict.FLOODED;
        if (!reading.supported()) return Verdict.NO_SUPPORT;
        if (reading.occupiedByEntity()) return Verdict.ENTITY_IN_THE_WAY;
        return Verdict.OK;
    }
}
