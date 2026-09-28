package com.ruskserver.moveearth_addtional.s2.territory;

import java.util.function.IntPredicate;

/** Pure policy for turning a finalized fallen core into rebuild or occupation state. */
public final class TerritoryFallSettlementPolicy {
    /** A fallen capital loses this much radius per fall, so one defeat does not end a nation. */
    public static final int CAPITAL_RADIUS_LOSS = 2;

    private TerritoryFallSettlementPolicy() { }

    public static Decision decide(boolean capital, int originalRadius,
                                  IntPredicate conflictsAtRadius) {
        if (capital) {
            return capitalDecision(originalRadius);
        }
        int safeOriginal = Math.max(TerritoryPreviewArea.MIN_RADIUS,
                Math.min(TerritoryPreviewArea.MAX_RADIUS, originalRadius));
        for (int radius = safeOriginal; radius >= TerritoryPreviewArea.MIN_RADIUS; radius--) {
            if (!conflictsAtRadius.test(radius)) {
                return new Decision(Outcome.OUTPOST_OCCUPIED, radius);
            }
        }
        return new Decision(Outcome.OUTPOST_NEUTRALIZED, 0);
    }

    /** A player without a nation can defeat an outpost, but cannot own its territory. */
    public static Decision decideIndividual(boolean capital, int originalRadius) {
        return capital ? capitalDecision(originalRadius)
                : new Decision(Outcome.OUTPOST_NEUTRALIZED, 0);
    }

    /** Radius 4 falls to 2, then 0; the center chunk is never transferred. */
    private static Decision capitalDecision(int originalRadius) {
        int safeOriginal = Math.max(TerritoryPreviewArea.MIN_RADIUS,
                Math.min(TerritoryPreviewArea.MAX_RADIUS, originalRadius));
        return new Decision(Outcome.CAPITAL_REBUILDING,
                Math.max(TerritoryPreviewArea.MIN_RADIUS, safeOriginal - CAPITAL_RADIUS_LOSS));
    }

    /**
     * A fallen capital may shrink but not regrow while its nation is under the settlement truce or
     * its recovery episode is still active; otherwise the lost radius could be restored at once.
     * Completing recovery lifts the lock, which gives the defeated side a concrete goal.
     */
    public static boolean enlargementLocked(boolean capital, int currentRadius, int requestedRadius,
                                            boolean settlementTruce, boolean recovering) {
        return capital && requestedRadius > currentRadius && (settlementTruce || recovering);
    }

    public enum Outcome { CAPITAL_REBUILDING, OUTPOST_OCCUPIED, OUTPOST_NEUTRALIZED }
    public record Decision(Outcome outcome, int radius) { }
}
