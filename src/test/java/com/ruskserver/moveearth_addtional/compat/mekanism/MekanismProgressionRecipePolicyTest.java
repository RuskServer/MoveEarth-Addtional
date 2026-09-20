package com.ruskserver.moveearth_addtional.compat.mekanism;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MekanismProgressionRecipePolicyTest {
    @Test
    void mapsEntryMachineAndEveryUniversalCableTier() {
        assertTrue(MekanismProgressionRecipePolicy.isLegacyRecipe(
                "mekanism", "metallurgic_infuser"));
        assertTrue(MekanismProgressionRecipePolicy.isLegacyRecipe(
                "mekanism", "transmitter/universal_cable/basic"));
        assertTrue(MekanismProgressionRecipePolicy.isLegacyRecipe(
                "mekanism", "transmitter/universal_cable/ultimate"));
        assertEquals(36, MekanismProgressionRecipePolicy.replacementPaths().size());
    }

    @Test
    void recognizesOnlyMoveEarthReplacementIds() {
        assertTrue(MekanismProgressionRecipePolicy.isReplacementFor(
                "metallurgic_infuser", "moveearth_addtional", "mekanism/metallurgic_infuser"));
        assertTrue(MekanismProgressionRecipePolicy.isReplacementFor(
                "transmitter/universal_cable/elite",
                "moveearth_addtional", "mekanism/universal_cable/elite"));
        assertFalse(MekanismProgressionRecipePolicy.isReplacementFor(
                "metallurgic_infuser", "mekanism", "metallurgic_infuser"));
    }

    @Test
    void preservesOriginalWhenReplacementFailedToLoad() {
        assertFalse(MekanismProgressionRecipePolicy.hasLoadedReplacement(
                "metallurgic_infuser", Set.of()));
        assertTrue(MekanismProgressionRecipePolicy.hasLoadedReplacement(
                "metallurgic_infuser", Set.of("mekanism/metallurgic_infuser")));
    }

    @Test
    void mapsSteelCasingAndNuclearChemistryMachines() {
        assertTrue(MekanismProgressionRecipePolicy.isLegacyRecipe("mekanism", "steel_casing"));
        assertTrue(MekanismProgressionRecipePolicy.isReplacementFor(
                "steel_casing", "moveearth_addtional", "mekanism/steel_casing"));
        assertTrue(MekanismProgressionRecipePolicy.isReplacementFor(
                "chemical_dissolution_chamber",
                "moveearth_addtional", "mekanism/machine/chemical_dissolution_chamber"));
        assertTrue(MekanismProgressionRecipePolicy.isReplacementFor(
                "isotopic_centrifuge",
                "moveearth_addtional", "mekanism/machine/isotopic_centrifuge"));
    }

    @Test
    void leavesUnrelatedMekanismRecipesAlone() {
        assertFalse(MekanismProgressionRecipePolicy.isLegacyRecipe(
                "mekanism", "solar_neutron_activator"));
        assertFalse(MekanismProgressionRecipePolicy.isLegacyRecipe(
                "other", "metallurgic_infuser"));
    }

    @Test
    void mapsNuclearMultiblocksAndInductionWithoutEnergyCubes() {
        assertTrue(MekanismProgressionRecipePolicy.isReplacementFor(
                "boiler_casing", "moveearth_addtional", "mekanism/nuclear/boiler_casing"));
        assertTrue(MekanismProgressionRecipePolicy.isReplacementFor(
                "induction/cell/basic",
                "moveearth_addtional", "mekanism/nuclear/induction/cell/basic"));
        assertTrue(MekanismProgressionRecipePolicy.isReplacementFor(
                "fission_reactor/fuel_assembly",
                "moveearth_addtional", "mekanism/nuclear/fission/fuel_assembly"));
        assertTrue(MekanismProgressionRecipePolicy.isReplacementFor(
                "electromagnetic_coil",
                "moveearth_addtional", "mekanism/nuclear/turbine/electromagnetic_coil"));
        assertTrue(MekanismProgressionRecipePolicy.isLegacyRecipe(
                "mekanismgenerators", "fission_reactor/fuel_assembly"));
        assertFalse(MekanismProgressionRecipePolicy.isLegacyRecipe(
                "mekanism", "fission_reactor/fuel_assembly"));
    }
}
