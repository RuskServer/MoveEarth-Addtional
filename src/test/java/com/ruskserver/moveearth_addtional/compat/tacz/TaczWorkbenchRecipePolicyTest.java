package com.ruskserver.moveearth_addtional.compat.tacz;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TaczWorkbenchRecipePolicyTest {
    @Test
    void identifiesOnlyTheThreeOriginalWorkbenchRecipes() {
        assertTrue(TaczWorkbenchRecipePolicy.isLegacyRecipe("tacz", "gun_smith_table"));
        assertTrue(TaczWorkbenchRecipePolicy.isLegacyRecipe("tacz", "ammo_workbench"));
        assertTrue(TaczWorkbenchRecipePolicy.isLegacyRecipe("tacz", "attachment_workbench"));
        assertFalse(TaczWorkbenchRecipePolicy.isLegacyRecipe("tacz", "iron_ammo_box"));
        assertFalse(TaczWorkbenchRecipePolicy.isLegacyRecipe("other", "gun_smith_table"));
    }

    @Test
    void pairsEachLegacyRecipeWithItsMoveEarthReplacement() {
        assertTrue(TaczWorkbenchRecipePolicy.isReplacementRecipe(
                "ammo_workbench", "moveearth_addtional", "ammo_workbench"));
        assertFalse(TaczWorkbenchRecipePolicy.isReplacementRecipe(
                "ammo_workbench", "moveearth_addtional", "attachment_workbench"));
        assertFalse(TaczWorkbenchRecipePolicy.isReplacementRecipe(
                "ammo_workbench", "tacz", "ammo_workbench"));
    }
}
