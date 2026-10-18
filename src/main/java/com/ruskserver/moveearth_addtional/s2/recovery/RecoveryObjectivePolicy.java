package com.ruskserver.moveearth_addtional.s2.recovery;

/** Pure recovery objective calculation kept separate from world access. */
public final class RecoveryObjectivePolicy {
    /** Support a nation has from the start, and what each objective adds; they total 100. */
    public static final int BASE_PERCENT = 30;
    public static final int RESEAL_PERCENT = 30;
    public static final int WALLS_PERCENT = 20;
    public static final int UPKEEP_PERCENT = 20;

    private RecoveryObjectivePolicy() { }

    /** A fixed number of strongest walls: more cheap blocks cannot replace the same number of diamond walls. */
    public static int wallHealth(java.util.stream.IntStream health, int blockLimit) {
        return health.filter(value -> value > 0).map(value -> -value).sorted()
                .limit(Math.max(0, blockLimit)).map(value -> -value).sum();
    }

    public static Progress evaluate(boolean resealed, int wallTarget, int healthyWalls, boolean upkeepPaid) {
        boolean walls = wallTarget <= 0 || healthyWalls >= wallTarget;
        int percent = BASE_PERCENT + (resealed ? RESEAL_PERCENT : 0) + (walls ? WALLS_PERCENT : 0)
                + (upkeepPaid ? UPKEEP_PERCENT : 0);
        return new Progress(resealed, walls, upkeepPaid, Math.min(100, percent));
    }

    public record Progress(boolean resealed, boolean wallsRestored, boolean upkeepPaid,
                           int supportPercent) { }
}
