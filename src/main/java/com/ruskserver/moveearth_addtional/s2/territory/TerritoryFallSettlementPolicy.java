package com.ruskserver.moveearth_addtional.s2.territory;

import java.util.function.IntPredicate;

/** Pure policy for turning a finalized fallen core into rebuild or occupation state. */
public final class TerritoryFallSettlementPolicy {
    private TerritoryFallSettlementPolicy() { }

    public static Decision decide(boolean capital, int originalRadius,
                                  IntPredicate conflictsAtRadius) {
        if (capital) {
            return new Decision(Outcome.CAPITAL_REBUILDING, 0);
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

    public enum Outcome { CAPITAL_REBUILDING, OUTPOST_OCCUPIED, OUTPOST_NEUTRALIZED }
    public record Decision(Outcome outcome, int radius) { }
}
