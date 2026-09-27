package com.ruskserver.moveearth_addtional.compat.create;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CampfireAlloyPolicyTest {
    @Test
    void litCampfireHeatsCampfireAlloys() {
        assertTrue(CampfireAlloyPolicy.heatMet(true, false, true, true));
    }

    @Test
    void otherHeatedRecipesStillNeedARealHeatSource() {
        assertFalse(CampfireAlloyPolicy.heatMet(true, false, true, false));
    }

    @Test
    void unlitOrMissingCampfireDoesNothing() {
        assertFalse(CampfireAlloyPolicy.heatMet(true, false, false, true));
    }

    @Test
    void superheatedRecipesNeverQualify() {
        assertFalse(CampfireAlloyPolicy.heatMet(false, false, true, true));
    }

    @Test
    void createsOwnVerdictIsKept() {
        assertTrue(CampfireAlloyPolicy.heatMet(true, true, false, false));
    }
}
