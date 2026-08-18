package com.ruskserver.moveearth_addtional.territory.overlay;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TerritoryOverlayCellTest {
    @Test
    void packedCellPreservesRelationTierAndInfluence() {
        int packed = TerritoryOverlayCell.pack(TerritoryOverlayCell.RELATION_CONTESTED, 4, 123.5D);

        assertEquals(TerritoryOverlayCell.RELATION_CONTESTED, TerritoryOverlayCell.relation(packed));
        assertEquals(4, TerritoryOverlayCell.protectionTier(packed));
        assertEquals(123.5D, TerritoryOverlayCell.influence(packed));
    }

    @Test
    void invalidMetadataIsRejectedAndStrengthIsClamped() {
        assertThrows(IllegalArgumentException.class, () -> TerritoryOverlayCell.pack(4, 0, 1.0D));
        assertEquals(4095.9375D, TerritoryOverlayCell.influence(
                TerritoryOverlayCell.pack(TerritoryOverlayCell.RELATION_HOSTILE, 0, 1_000_000.0D)));
    }
}
