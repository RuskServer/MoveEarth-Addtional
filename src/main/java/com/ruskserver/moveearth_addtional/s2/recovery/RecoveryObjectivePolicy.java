package com.ruskserver.moveearth_addtional.s2.recovery;

/** Pure recovery objective calculation kept separate from world access. */
public final class RecoveryObjectivePolicy {
    private RecoveryObjectivePolicy() { }

    public static Progress evaluate(boolean resealed, int wallTarget, int healthyWalls, boolean upkeepPaid) {
        boolean walls = wallTarget <= 0 || healthyWalls >= wallTarget;
        int percent = 30 + (resealed ? 30 : 0) + (walls ? 20 : 0) + (upkeepPaid ? 20 : 0);
        return new Progress(resealed, walls, upkeepPaid, Math.min(100, percent));
    }

    public record Progress(boolean resealed, boolean wallsRestored, boolean upkeepPaid,
                           int supportPercent) { }
}
