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
    void assemblyTakesOnlyOwnLandAndCompactUnprotectedFreight() {
        assertEquals(MinecartContraptionPolicy.AssemblyFailure.NONE,
                MinecartContraptionPolicy.assemblyFailure(true, 256, 16, false, false));
        assertEquals(MinecartContraptionPolicy.AssemblyFailure.FOREIGN_BLOCK,
                MinecartContraptionPolicy.assemblyFailure(false, 1, 0, false, false));
        assertEquals(MinecartContraptionPolicy.AssemblyFailure.TOO_LARGE,
                MinecartContraptionPolicy.assemblyFailure(true, 257, 0, false, false));
        assertEquals(MinecartContraptionPolicy.AssemblyFailure.TOO_MANY_CONTAINERS,
                MinecartContraptionPolicy.assemblyFailure(true, 16, 17, false, false));
        assertEquals(MinecartContraptionPolicy.AssemblyFailure.REINFORCED_BLOCK,
                MinecartContraptionPolicy.assemblyFailure(true, 16, 1, true, false));
        assertEquals(MinecartContraptionPolicy.AssemblyFailure.PROTECTED_BLOCK,
                MinecartContraptionPolicy.assemblyFailure(true, 16, 1, false, true));
    }

    @Test
    void ownLandIsTheOwnersOrNobodys() {
        assertTrue(MinecartContraptionPolicy.ownLand(HOME, HOME));
        assertTrue(MinecartContraptionPolicy.ownLand(HOME, null));
        assertTrue(MinecartContraptionPolicy.ownLand(null, null));
        assertFalse(MinecartContraptionPolicy.ownLand(HOME, ENEMY));
        assertFalse(MinecartContraptionPolicy.ownLand(null, HOME));
    }

    @Test
    void anyCitizenPlacesAnAssemblerAtHomeAndAnyoneOnUnheldLand() {
        assertTrue(MinecartContraptionPolicy.canPlaceAssembler(HOME, HOME));
        assertTrue(MinecartContraptionPolicy.canPlaceAssembler(HOME, null));
        assertTrue(MinecartContraptionPolicy.canPlaceAssembler(null, null));
        assertFalse(MinecartContraptionPolicy.canPlaceAssembler(HOME, ENEMY));
        assertFalse(MinecartContraptionPolicy.canPlaceAssembler(null, HOME));
    }

    @Test
    void unloadingAllowsOwnAndUnheldLandButNotAnotherNations() {
        assertTrue(MinecartContraptionPolicy.canUnload(true, HOME, HOME, true));
        assertTrue(MinecartContraptionPolicy.canUnload(true, HOME, null, false));
        assertFalse(MinecartContraptionPolicy.canUnload(true, HOME, ENEMY, true));
        assertFalse(MinecartContraptionPolicy.canUnload(true, HOME, ENEMY, false));
        assertFalse(MinecartContraptionPolicy.canUnload(true, null, HOME, true));
    }

    @Test
    void anOlderCartWithNoOwnerUnloadsAtAnyAssemblerOnce() {
        assertTrue(MinecartContraptionPolicy.canUnload(false, null, HOME, true));
        assertTrue(MinecartContraptionPolicy.canUnload(false, null, null, false));
        assertFalse(MinecartContraptionPolicy.canUnload(false, null, HOME, false));
    }

    @Test
    void workDevicesStopOnLandThatIsNotTheCartsOwn() {
        assertTrue(MinecartContraptionPolicy.suspendWorkActors(HOME, ENEMY, true));
        assertTrue(MinecartContraptionPolicy.suspendWorkActors(null, HOME, true));
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
