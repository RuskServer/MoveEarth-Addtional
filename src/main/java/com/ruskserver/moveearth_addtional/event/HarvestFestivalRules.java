package com.ruskserver.moveearth_addtional.event;

public final class HarvestFestivalRules {
    public static final int MAX_POINTS = 2200;

    private HarvestFestivalRules() { }

    public static int addHarvest(int currentPoints, boolean farmer) {
        return Math.min(MAX_POINTS, Math.max(0, currentPoints) + (farmer ? 11 : 10));
    }

    public static int currency(int rank, int paidToday) {
        int requested = 2 + (rank < 3 ? 2 : 0) + (rank == 0 ? 1 : 0);
        return Math.min(requested, Math.max(0, 10 - paidToday));
    }

    /**
     * Higher points first; on equal points whoever reached them earlier. The UUID only breaks exact
     * ties, which previously decided every capped score and handed one account the diamonds each time.
     */
    public static int compare(int pointsA, long reachedA, String idA, int pointsB, long reachedB, String idB) {
        if (pointsA != pointsB) return Integer.compare(pointsB, pointsA);
        if (reachedA != reachedB) return Long.compare(reachedA, reachedB);
        return idA.compareTo(idB);
    }

    public static int diamonds(int rank) {
        return rank == 0 ? 6 : rank < 3 ? 3 : 0;
    }
}
