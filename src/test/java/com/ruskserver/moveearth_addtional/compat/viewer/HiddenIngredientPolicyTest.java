package com.ruskserver.moveearth_addtional.compat.viewer;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HiddenIngredientPolicyTest {
    @Test
    void hidesMekanismContentThatIsRestrictedAtRuntime() {
        assertTrue(HiddenIngredientPolicy.isHidden("mekanism", "digital_miner"));
        assertTrue(HiddenIngredientPolicy.isHidden("mekanism", "basic_mechanical_pipe"));
        assertTrue(HiddenIngredientPolicy.isHidden("mekanism", "elite_crushing_factory"));
        assertTrue(HiddenIngredientPolicy.isHidden("mekanism", "module_elytra_unit"));
        assertTrue(HiddenIngredientPolicy.isHidden("mekanismgenerators", "wind_generator"));
        assertTrue(HiddenIngredientPolicy.isHidden("mekanismtools", "refined_obsidian_chestplate"));
    }

    @Test
    void hidesTheEnderChest() {
        assertTrue(HiddenIngredientPolicy.isHidden("minecraft", "ender_chest"));
        assertFalse(HiddenIngredientPolicy.isHidden("minecraft", "chest"));
    }

    @Test
    void hidesOreMultiplicationIntermediates() {
        assertTrue(HiddenIngredientPolicy.isHidden("mekanism", "clump_iron"));
        assertTrue(HiddenIngredientPolicy.isHidden("mekanism", "shard_copper"));
        assertTrue(HiddenIngredientPolicy.isHidden("mekanism", "crystal_osmium"));
        assertTrue(HiddenIngredientPolicy.isHidden("mekanism", "dirty_dust_gold"));
    }

    @Test
    void keepsWhatPlayersCanStillMakeAndUse() {
        assertFalse(HiddenIngredientPolicy.isHidden("mekanism", "dust_iron"));
        assertFalse(HiddenIngredientPolicy.isHidden("mekanism", "basic_smelting_factory"));
        assertFalse(HiddenIngredientPolicy.isHidden("mekanism", "mekasuit_helmet"));
        assertFalse(HiddenIngredientPolicy.isHidden("mekanism", "module_energy_unit"));
        assertFalse(HiddenIngredientPolicy.isHidden("mekanismgenerators", "solar_generator"));
        assertFalse(HiddenIngredientPolicy.isHidden("mekanismtools", "steel_chestplate"));
        assertFalse(HiddenIngredientPolicy.isHidden("create", "crushed_raw_iron"));
    }
}
