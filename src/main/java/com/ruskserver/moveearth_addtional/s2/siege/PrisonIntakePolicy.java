package com.ruskserver.moveearth_addtional.s2.siege;

import java.util.UUID;

/** Keeps intake diagnostics meaningful before and during an escort. */
final class PrisonIntakePolicy {
    private PrisonIntakePolicy() { }

    static boolean isActiveHoldingTerritory(UUID territoryOwner, UUID custodyHoldingNation,
                                            UUID viewerNation) {
        UUID expectedNation = custodyHoldingNation != null ? custodyHoldingNation : viewerNation;
        return expectedNation != null && expectedNation.equals(territoryOwner);
    }
}
