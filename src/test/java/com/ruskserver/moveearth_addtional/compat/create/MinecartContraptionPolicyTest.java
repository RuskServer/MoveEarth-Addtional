package com.ruskserver.moveearth_addtional.compat.create;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MinecartContraptionPolicyTest {
    private static final UUID HOME = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID ENEMY = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");

    @Test
    void assemblyIsLimitedToCompactUnprotectedHomeFreight() {
        assertEquals(MinecartContraptionPolicy.AssemblyFailure.NONE,
                MinecartContraptionPolicy.assemblyFailure(true, true, 256, 16, false, false));
        assertEquals(MinecartContraptionPolicy.AssemblyFailure.NO_HOME_TERRITORY,
                MinecartContraptionPolicy.assemblyFailure(false, true, 1, 0, false, false));
        assertEquals(MinecartContraptionPolicy.AssemblyFailure.CROSSES_TERRITORY,
                MinecartContraptionPolicy.assemblyFailure(true, false, 1, 0, false, false));
        assertEquals(MinecartContraptionPolicy.AssemblyFailure.TOO_LARGE,
                MinecartContraptionPolicy.assemblyFailure(true, true, 257, 0, false, false));
        assertEquals(MinecartContraptionPolicy.AssemblyFailure.TOO_MANY_CONTAINERS,
                MinecartContraptionPolicy.assemblyFailure(true, true, 16, 17, false, false));
        assertEquals(MinecartContraptionPolicy.AssemblyFailure.REINFORCED_BLOCK,
                MinecartContraptionPolicy.assemblyFailure(true, true, 16, 1, true, false));
        assertEquals(MinecartContraptionPolicy.AssemblyFailure.PROTECTED_BLOCK,
                MinecartContraptionPolicy.assemblyFailure(true, true, 16, 1, false, true));
    }

    @Test
    void assemblerPlacementRequiresHomeNationTerritoryManagement() {
        assertTrue(MinecartContraptionPolicy.canPlaceAssembler(HOME, HOME, true));
        assertFalse(MinecartContraptionPolicy.canPlaceAssembler(HOME, HOME, false));
        assertFalse(MinecartContraptionPolicy.canPlaceAssembler(HOME, ENEMY, true));
        assertFalse(MinecartContraptionPolicy.canPlaceAssembler(null, HOME, true));
    }

    @Test
    void unloadingAllowsHomeAndWildernessButNotForeignTerritory() {
        assertTrue(MinecartContraptionPolicy.canDisassemble(HOME, HOME));
        assertTrue(MinecartContraptionPolicy.canDisassemble(HOME, null));
        assertFalse(MinecartContraptionPolicy.canDisassemble(HOME, ENEMY));
        assertFalse(MinecartContraptionPolicy.canDisassemble(null, HOME));
    }

    @Test
    void workDevicesStopOnlyInsideForeignControlledLand() {
        assertTrue(MinecartContraptionPolicy.suspendWorkActors(HOME, ENEMY, true));
        assertFalse(MinecartContraptionPolicy.suspendWorkActors(HOME, HOME, true));
        assertFalse(MinecartContraptionPolicy.suspendWorkActors(HOME, null, true));
        assertFalse(MinecartContraptionPolicy.suspendWorkActors(HOME, ENEMY, false));
    }

    @Test
    void weaponModBlocksCannotBecomeCheapFreightContraptions() {
        assertTrue(MinecartContraptionPolicy.isWeaponNamespace("createbigcannons"));
        assertTrue(MinecartContraptionPolicy.isWeaponNamespace("create_warnautics"));
        assertTrue(MinecartContraptionPolicy.isWeaponNamespace("tacz"));
        assertFalse(MinecartContraptionPolicy.isWeaponNamespace("create"));
    }
}
