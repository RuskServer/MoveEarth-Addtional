package com.ruskserver.moveearth_addtional.region;

import java.util.List;

/**
 * What the region under the player's feet holds.
 *
 * <p>Only that region. Listing the neighbours was the first design and it
 * answered a question nobody was asking: the tab exists because a player who
 * digs and finds nothing cannot tell a region without gold from broken ore
 * generation, and the region they are standing in is the whole of that answer.
 * Where the gold actually is stays something to travel for or trade for.
 *
 * <p>{@code elsewhere} is the useful half. Knowing this region has oil does not
 * tell anyone what it lacks, and what it lacks is what sends them looking for
 * someone to deal with.
 *
 * @param id         the region, 0 when the player is outside any
 * @param exclusives the strategic resources it was given
 * @param elsewhere  the strategic resources that belong to other regions
 * @param specialty  the common material it has more of than its neighbours
 * @param shortage   the one it has less of
 * @param traceShare the share of an absent resource that still turns up here
 */
public record RegionSnapshot(int id, List<String> exclusives, List<String> elsewhere,
                             String specialty, String shortage, double traceShare) {

    public RegionSnapshot {
        exclusives = exclusives == null ? List.of() : List.copyOf(exclusives);
        elsewhere = elsewhere == null ? List.of() : List.copyOf(elsewhere);
        specialty = specialty == null ? "" : specialty;
        shortage = shortage == null ? "" : shortage;
    }

    /** Nowhere: outside any region, or before an answer has arrived. */
    public static RegionSnapshot none() {
        return new RegionSnapshot(0, List.of(), List.of(), "", "", 0.0);
    }

    public boolean known() {
        return id > 0;
    }
}
