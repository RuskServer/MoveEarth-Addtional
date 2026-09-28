package com.ruskserver.moveearth_addtional.compat.mekanism;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MekanismRuntimeRestrictionPolicyTest {
    @Test
    void blocksRemovedGeneratorsFusionAndLaserBlocks() {
        assertBlock("mekanismgenerators", "heat_generator");
        assertBlock("mekanismgenerators", "gas_burning_generator");
        assertBlock("mekanismgenerators", "fusion_reactor_controller");
        assertBlock("mekanismgenerators", "laser_focus_matrix");
        assertBlock("mekanism", "laser");
        assertBlock("mekanism", "laser_amplifier");
        assertBlock("mekanism", "laser_tractor_beam");
    }

    @Test
    void blocksRemovedLogisticsMachinesStorageAndEndgameBlocks() {
        assertBlock("mekanism", "digital_miner");
        assertBlock("mekanism", "quantum_entangloporter");
        assertBlock("mekanism", "ultimate_energy_cube");
        assertBlock("mekanism", "creative_bin");
        assertBlock("mekanism", "elite_logistical_transporter");
        assertBlock("mekanism", "advanced_enriching_factory");
        assertBlock("mekanism", "sps_casing");
        assertBlock("mekanism", "antiprotonic_nucleosynthesizer");
    }

    @Test
    void blocksRemovedPortableCombatAndUpgradeItems() {
        assertItem("mekanism", "portable_teleporter");
        assertItem("mekanism", "atomic_disassembler");
        assertItem("mekanism", "module_gravitational_modulating_unit");
        assertItem("mekanism", "module_elytra_unit");
        assertItem("mekanism", "module_hydraulic_propulsion_unit");
        assertItem("mekanism", "module_locomotive_boosting_unit");
        assertItem("mekanism", "module_vision_enhancement_unit");
        assertItem("mekanism", "meka_tool");
        assertItem("mekanism", "jetpack_armored");
        assertItem("mekanism", "ultimate_tier_installer");
        assertItem("mekanismgenerators", "hohlraum");
    }

    @Test
    void blocksItemFluidAndHeatTransmitters() {
        assertBlock("mekanism", "diversion_transporter");
        assertBlock("mekanism", "restrictive_transporter");
        assertBlock("mekanism", "basic_mechanical_pipe");
        assertBlock("mekanism", "ultimate_mechanical_pipe");
        assertBlock("mekanism", "elite_thermodynamic_conductor");
        assertItem("mekanism", "advanced_mechanical_pipe");
        assertFalse(MekanismRuntimeRestrictionPolicy.isRestrictedBlock("mekanism", "ultimate_pressurized_tube"));
        assertFalse(MekanismRuntimeRestrictionPolicy.isRestrictedBlock("mekanism", "basic_universal_cable"));
    }

    @Test
    void restrictsRefinedObsidianGearAndEveryPaxel() {
        assertItem("mekanismtools", "refined_obsidian_chestplate");
        assertItem("mekanismtools", "refined_obsidian_paxel");
        assertItem("mekanismtools", "refined_obsidian_shield");
        for (String material : new String[]{"wood", "stone", "iron", "gold", "diamond", "netherite",
                "bronze", "lapis_lazuli", "osmium", "refined_glowstone", "steel"}) {
            assertItem("mekanismtools", material + "_paxel");
        }
        assertFalse(MekanismRuntimeRestrictionPolicy.isRestrictedItem("mekanismtools", "osmium_chestplate"));
        assertFalse(MekanismRuntimeRestrictionPolicy.isRestrictedItem("mekanismtools", "steel_helmet"));
        assertFalse(MekanismRuntimeRestrictionPolicy.isRestrictedItem("mekanismtools", "steel_pickaxe"));
    }

    @Test
    void allowsOnlyTheBasicSolarGenerator() {
        assertFalse(MekanismRuntimeRestrictionPolicy.isRestrictedBlock("mekanismgenerators", "solar_generator"));
        assertBlock("mekanismgenerators", "advanced_solar_generator");
        assertBlock("mekanismgenerators", "wind_generator");
        assertBlock("mekanismgenerators", "heat_generator");
    }

    @Test
    void keepsTheMekaSuitAndItsPermittedModulesUsable() {
        for (String piece : new String[]{"mekasuit_helmet", "mekasuit_bodyarmor", "mekasuit_pants", "mekasuit_boots"}) {
            assertFalse(MekanismRuntimeRestrictionPolicy.isRestrictedItem("mekanism", piece));
        }
        assertFalse(MekanismRuntimeRestrictionPolicy.isRestrictedBlock("mekanism", "modification_station"));
        assertFalse(MekanismRuntimeRestrictionPolicy.isRestrictedItem("mekanism", "module_radiation_shielding_unit"));
        assertFalse(MekanismRuntimeRestrictionPolicy.isRestrictedItem("mekanism", "module_inhalation_purification_unit"));
    }

    @Test
    void keepsEnergizedSmelterAndSmeltingFactoriesUsable() {
        assertFalse(MekanismRuntimeRestrictionPolicy.isRestrictedBlock("mekanism", "energized_smelter"));
        assertFalse(MekanismRuntimeRestrictionPolicy.isRestrictedBlock("mekanism", "ultimate_smelting_factory"));
        assertFalse(MekanismRuntimeRestrictionPolicy.isRestrictedItem("mekanism", "basic_smelting_factory"));
    }

    @Test
    void keepsApprovedChemistryNuclearAndCableContentUsable() {
        assertFalse(MekanismRuntimeRestrictionPolicy.isRestrictedBlock(
                "mekanismgenerators", "fission_reactor_casing"));
        assertFalse(MekanismRuntimeRestrictionPolicy.isRestrictedBlock(
                "mekanismgenerators", "turbine_casing"));
        assertFalse(MekanismRuntimeRestrictionPolicy.isRestrictedBlock(
                "mekanism", "metallurgic_infuser"));
        assertFalse(MekanismRuntimeRestrictionPolicy.isRestrictedBlock(
                "mekanism", "ultimate_universal_cable"));
        assertFalse(MekanismRuntimeRestrictionPolicy.isRestrictedItem(
                "other", "atomic_disassembler"));
    }

    private static void assertBlock(String namespace, String path) {
        assertTrue(MekanismRuntimeRestrictionPolicy.isRestrictedBlock(namespace, path));
        assertTrue(MekanismRuntimeRestrictionPolicy.isRestrictedItem(namespace, path));
    }

    private static void assertItem(String namespace, String path) {
        assertTrue(MekanismRuntimeRestrictionPolicy.isRestrictedItem(namespace, path));
    }
}
