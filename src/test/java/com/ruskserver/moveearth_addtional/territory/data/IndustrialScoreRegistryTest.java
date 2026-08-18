package com.ruskserver.moveearth_addtional.territory.data;

import com.ruskserver.moveearth_addtional.territory.domain.TerritoryOwnerId;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IndustrialScoreRegistryTest {
    private static final TerritoryOwnerId OWNER = TerritoryOwnerId.of(
            UUID.fromString("00000000-0000-0000-0000-000000000001"));

    @Test
    void missingOwnerStartsAtZeroAndUpdatesAreReported() {
        IndustrialScoreRegistry registry = new IndustrialScoreRegistry();

        assertEquals(0.0D, registry.get(OWNER));
        assertTrue(registry.set(OWNER, 120.0D));
        assertFalse(registry.set(OWNER, 120.0D));
        assertEquals(120.0D, registry.get(OWNER));
    }

    @Test
    void invalidScoresAreRejected() {
        IndustrialScoreRegistry registry = new IndustrialScoreRegistry();

        assertThrows(IllegalArgumentException.class, () -> registry.set(OWNER, -1.0D));
        assertThrows(IllegalArgumentException.class, () -> registry.set(OWNER, Double.NaN));
    }
}
