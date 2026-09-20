package com.ruskserver.moveearth_addtional.compat.mekanism;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MekanismRecipePolicyTest {
    @Test
    void removesIndependentGeneratorsButKeepsNuclearGeneration() {
        assertCategory("mekanismgenerators", "generator/heat",
                MekanismRecipePolicy.RemovalCategory.INDEPENDENT_GENERATOR);
        assertCategory("mekanismgenerators", "generator/gas_burning",
                MekanismRecipePolicy.RemovalCategory.INDEPENDENT_GENERATOR);
        assertFalse(MekanismRecipePolicy.shouldRemove(
                "mekanismgenerators", "fission_reactor/casing"));
        assertFalse(MekanismRecipePolicy.shouldRemove(
                "mekanismgenerators", "turbine/casing"));
        assertCategory("mekanismgenerators", "module_geothermal_generator_unit",
                MekanismRecipePolicy.RemovalCategory.COMBAT_EQUIPMENT);
    }

    @Test
    void removesFusionUntilItIsExplicitlyReleased() {
        for (String path : new String[]{
                "activating/tritium",
                "chemical_infusing/fusion_fuel",
                "hohlraum",
                "laser_focus_matrix",
                "reactor/controller",
                "reactor/frame",
                "reactor/glass",
                "reactor/logic_adapter",
                "reactor/port",
                "rotary/deuterium",
                "rotary/fusion_fuel",
                "rotary/tritium",
                "separator/heavy_water"
        }) {
            assertCategory("mekanismgenerators", path,
                    MekanismRecipePolicy.RemovalCategory.FUSION);
        }
    }

    @Test
    void removesSpsAndDirectedEnergyWeapons() {
        assertCategory("mekanism", "sps_casing",
                MekanismRecipePolicy.RemovalCategory.ENDGAME_MATTER);
        assertCategory("mekanism", "sps_port",
                MekanismRecipePolicy.RemovalCategory.ENDGAME_MATTER);
        assertCategory("mekanism", "supercharged_coil",
                MekanismRecipePolicy.RemovalCategory.ENDGAME_MATTER);
        assertCategory("mekanism", "laser",
                MekanismRecipePolicy.RemovalCategory.DIRECTED_ENERGY_WEAPON);
        assertCategory("mekanism", "laser_amplifier",
                MekanismRecipePolicy.RemovalCategory.DIRECTED_ENERGY_WEAPON);
        assertCategory("mekanism", "laser_tractor_beam",
                MekanismRecipePolicy.RemovalCategory.DIRECTED_ENERGY_WEAPON);
        assertTrue(MekanismRecipePolicy.isBlockedLaserBlock("mekanism", "laser"));
        assertTrue(MekanismRecipePolicy.isBlockedLaserBlock("mekanism", "laser_amplifier"));
        assertTrue(MekanismRecipePolicy.isBlockedLaserBlock("mekanism", "laser_tractor_beam"));
        assertFalse(MekanismRecipePolicy.isBlockedLaserBlock("other", "laser"));
    }

    @Test
    void removesRemoteLogisticsAndStorageShortcuts() {
        assertCategory("mekanism", "digital_miner",
                MekanismRecipePolicy.RemovalCategory.LOGISTICS_BYPASS);
        assertCategory("mekanism", "portable_qio_dashboard",
                MekanismRecipePolicy.RemovalCategory.LOGISTICS_BYPASS);
        assertCategory("mekanism", "transmitter/logistical_transporter/basic",
                MekanismRecipePolicy.RemovalCategory.LOGISTICS_BYPASS);
        assertCategory("mekanism", "cardboard_box",
                MekanismRecipePolicy.RemovalCategory.LOGISTICS_BYPASS);
    }

    @Test
    void keepsUniversalCableForPlantDistribution() {
        assertFalse(MekanismRecipePolicy.shouldRemove(
                "mekanism", "transmitter/universal_cable/basic"));
        assertFalse(MekanismRecipePolicy.shouldRemove(
                "mekanism", "transmitter/universal_cable/ultimate"));
    }

    @Test
    void removesEnergyCubesButKeepsNuclearInductionMatrix() {
        assertCategory("mekanism", "energy_cube/basic",
                MekanismRecipePolicy.RemovalCategory.ENERGY_BYPASS);
        assertCategory("mekanism", "energy_cube/ultimate",
                MekanismRecipePolicy.RemovalCategory.ENERGY_BYPASS);
        assertFalse(MekanismRecipePolicy.shouldRemove(
                "mekanism", "induction/cell/basic"));
        assertFalse(MekanismRecipePolicy.shouldRemove(
                "mekanism", "induction/provider/ultimate"));
    }

    @Test
    void removesCompactFactoriesAndCreateReplacementMachines() {
        assertCategory("mekanism", "factory/basic/enriching",
                MekanismRecipePolicy.RemovalCategory.CREATE_REPLACEMENT);
        assertCategory("mekanism", "energized_smelter",
                MekanismRecipePolicy.RemovalCategory.CREATE_REPLACEMENT);
        assertCategory("mekanism", "formulaic_assemblicator",
                MekanismRecipePolicy.RemovalCategory.CREATE_REPLACEMENT);
        assertCategory("mekanism", "purification_chamber",
                MekanismRecipePolicy.RemovalCategory.CREATE_REPLACEMENT);
    }

    @Test
    void removesOreMultiplicationButPreservesNuclearChemistry() {
        assertCategory("mekanism", "processing/iron/dust/from_raw_ore",
                MekanismRecipePolicy.RemovalCategory.ORE_MULTIPLICATION);
        assertCategory("mekanism", "processing/osmium/clump/from_ore",
                MekanismRecipePolicy.RemovalCategory.ORE_MULTIPLICATION);
        assertCategory("mekanism", "processing/uranium/slurry/dirty/from_ore",
                MekanismRecipePolicy.RemovalCategory.ORE_MULTIPLICATION);
        assertCategory("mekanism", "processing/diamond/from_ore",
                MekanismRecipePolicy.RemovalCategory.ORE_MULTIPLICATION);

        assertFalse(MekanismRecipePolicy.shouldRemove(
                "mekanism", "processing/uranium/yellow_cake_uranium"));
        assertFalse(MekanismRecipePolicy.shouldRemove(
                "mekanism", "processing/uranium/fissile_fuel"));
        assertFalse(MekanismRecipePolicy.shouldRemove(
                "mekanism", "processing/lategame/polonium"));
        assertFalse(MekanismRecipePolicy.shouldRemove(
                "mekanism", "enriching/enriched/carbon"));
    }

    @Test
    void removesMatterReplicationWhileLeavingOtherModsAlone() {
        assertCategory("mekanism", "antiprotonic_nucleosynthesizer",
                MekanismRecipePolicy.RemovalCategory.ENDGAME_MATTER);
        assertCategory("mekanism", "nucleosynthesizing/diamond",
                MekanismRecipePolicy.RemovalCategory.ENDGAME_MATTER);
        assertFalse(MekanismRecipePolicy.shouldRemove("create", "crushing/iron_ore"));
        assertFalse(MekanismRecipePolicy.shouldRemove(null, null));
    }

    private static void assertCategory(String namespace, String path,
                                       MekanismRecipePolicy.RemovalCategory expected) {
        assertTrue(MekanismRecipePolicy.shouldRemove(namespace, path));
        assertEquals(expected, MekanismRecipePolicy.classify(namespace, path));
    }
}
