package com.ruskserver.moveearth_addtional.region;

import java.util.List;

/**
 * What one player is allowed to know about the regions around them.
 *
 * <p>Built per player, not per world: the same region is shown differently to
 * someone who has been there and someone who has only heard it exists. The
 * server decides that rather than the screen, so a client cannot read what it
 * was not told.
 *
 * @param currentRegion the region the player is standing in, 0 when nowhere
 * @param regions       the current region first, then its neighbours
 */
public record RegionSnapshot(int currentRegion, List<Entry> regions) {

    public RegionSnapshot {
        regions = regions == null ? List.of() : List.copyOf(regions);
    }

    /**
     * One region as this player sees it.
     *
     * @param known      whether the player has stood here. When false the rest
     *                   is blank rather than false: an unvisited neighbour is a
     *                   place to go and look, and filling it in would remove
     *                   the reason to
     * @param exclusives the strategic resources it was given
     * @param specialty  the common material it has more of than its neighbours
     * @param shortage   the one it has less of
     */
    public record Entry(int id, boolean current, boolean known, List<String> exclusives,
                        String specialty, String shortage, double baseDensity) {

        public Entry {
            exclusives = exclusives == null ? List.of() : List.copyOf(exclusives);
            specialty = specialty == null ? "" : specialty;
            shortage = shortage == null ? "" : shortage;
        }

        /** An entry for a region the player has not been to. */
        public static Entry unknown(int id, boolean current) {
            return new Entry(id, current, false, List.of(), "", "", 0.0);
        }
    }
}
