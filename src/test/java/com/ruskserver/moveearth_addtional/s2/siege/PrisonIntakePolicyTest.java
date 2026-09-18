package com.ruskserver.moveearth_addtional.s2.siege;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PrisonIntakePolicyTest {
    private static final UUID HOME = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID EMPLOYER = UUID.fromString("00000000-0000-0000-0000-000000000002");

    @Test
    void usesViewerNationWhenNoEscortExists() {
        assertTrue(PrisonIntakePolicy.isActiveHoldingTerritory(HOME, null, HOME));
        assertFalse(PrisonIntakePolicy.isActiveHoldingTerritory(EMPLOYER, null, HOME));
        assertFalse(PrisonIntakePolicy.isActiveHoldingTerritory(HOME, null, null));
    }

    @Test
    void escortHoldingNationOverridesViewerNation() {
        assertTrue(PrisonIntakePolicy.isActiveHoldingTerritory(EMPLOYER, EMPLOYER, HOME));
        assertFalse(PrisonIntakePolicy.isActiveHoldingTerritory(HOME, EMPLOYER, HOME));
    }
}
