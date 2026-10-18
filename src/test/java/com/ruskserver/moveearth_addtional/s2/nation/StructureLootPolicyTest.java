package com.ruskserver.moveearth_addtional.s2.nation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StructureLootPolicyTest {
    @Test
    void anUnopenedStructureChestInTheWildernessIsOpenToAll() {
        assertTrue(StructureLootPolicy.openToAll(false, false, false, true, false, false));
    }

    @Test
    void itStaysOpenAfterTheFirstVisitorRollsTheLoot() {
        assertTrue(StructureLootPolicy.openToAll(false, false, false, false, true, false));
    }

    @Test
    void storageATemplateFilledInsideAStructureOpensToo() {
        assertTrue(StructureLootPolicy.openToAll(false, false, false, false, false, true));
        assertFalse(StructureLootPolicy.openToAll(false, false, true, false, false, true), "unless in a nation's land");
    }

    @Test
    void ordinaryStorageInTheWildernessStaysClosed() {
        assertFalse(StructureLootPolicy.openToAll(false, false, false, false, false, false));
    }

    @Test
    void territoryOwnershipAndVehiclesKeepTheirOwnRules() {
        assertFalse(StructureLootPolicy.openToAll(false, false, true, true, true, false), "inside a nation's land");
        assertFalse(StructureLootPolicy.openToAll(true, false, false, true, true, false), "recorded as a nation's");
        assertFalse(StructureLootPolicy.openToAll(false, true, false, true, true, false), "on a vehicle");
    }
}
