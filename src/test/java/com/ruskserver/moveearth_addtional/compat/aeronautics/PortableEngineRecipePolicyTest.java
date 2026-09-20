package com.ruskserver.moveearth_addtional.compat.aeronautics;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PortableEngineRecipePolicyTest {
    @Test
    void removesOnlySimulatedsOriginalRedEngineRecipe() {
        assertTrue(PortableEngineRecipePolicy.isLegacyRecipe("simulated", "red_portable_engine"));
        assertFalse(PortableEngineRecipePolicy.isLegacyRecipe("simulated", "blue_portable_engine"));
        assertFalse(PortableEngineRecipePolicy.isLegacyRecipe("moveearth_addtional", "red_portable_engine"));
    }

    @Test
    void recognizesMoveEarthMechanicalReplacement() {
        assertTrue(PortableEngineRecipePolicy.isReplacementRecipe(
                "moveearth_addtional", "red_portable_engine"));
        assertFalse(PortableEngineRecipePolicy.isReplacementRecipe(
                "simulated", "red_portable_engine"));
    }
}
