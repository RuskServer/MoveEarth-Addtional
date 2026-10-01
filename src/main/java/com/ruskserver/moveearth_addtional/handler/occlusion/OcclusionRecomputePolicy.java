package com.ruskserver.moveearth_addtional.handler.occlusion;

/**
 * When a player's visible sections are recomputed. Movement and turning used to trigger
 * a new search up to every tick per player; now at most once per minimum interval.
 */
final class OcclusionRecomputePolicy {
    private OcclusionRecomputePolicy() { }

    /**
     * @param hasResult  a previous result exists for this player in this dimension
     * @param sinceLast  ticks since the previous search (negative after a clock reset)
     */
    static boolean shouldRecompute(boolean hasResult, long sinceLast, boolean sectionChanged, boolean turned,
                                   int minimumIntervalTicks, int maximumIntervalTicks) {
        if (!hasResult || sinceLast < 0L) return true;
        if (sinceLast < minimumIntervalTicks) return false;
        return sinceLast >= maximumIntervalTicks || sectionChanged || turned;
    }

    /** Fail open past a truncated search: never hide what the budget did not reach. */
    static boolean beyondTruncation(int truncatedDepth, int startX, int startY, int startZ,
                                    int sectionX, int sectionY, int sectionZ) {
        if (truncatedDepth == SectionVisibilitySearch.COMPLETE) return false;
        int distance = Math.abs(sectionX - startX) + Math.abs(sectionY - startY) + Math.abs(sectionZ - startZ);
        return distance >= truncatedDepth - 1;
    }
}
