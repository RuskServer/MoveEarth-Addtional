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
        assertBlock("mekanism", "advanced_smelting_factory");
        assertBlock("mekanism", "sps_casing");
        assertBlock("mekanism", "antiprotonic_nucleosynthesizer");
    }

    @Test
    void blocksRemovedPortableCombatAndUpgradeItems() {
        assertItem("mekanism", "portable_teleporter");
        assertItem("mekanism", "atomic_disassembler");
        assertItem("mekanism", "mekasuit_bodyarmor");
        assertItem("mekanism", "jetpack_armored");
        assertItem("mekanism", "ultimate_tier_installer");
        assertItem("mekanismgenerators", "hohlraum");
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
