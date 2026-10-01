package com.ruskserver.moveearth_addtional.compat.vehicle;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static com.ruskserver.moveearth_addtional.compat.vehicle.AssemblyExclusionPolicy.Kind.*;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AssemblyExclusionPolicyTest {
    private final UUID defender = UUID.randomUUID();
    private final UUID attacker = UUID.randomUUID();

    @Test
    void fixturesNeverMove() {
        assertTrue(AssemblyExclusionPolicy.excluded(FIXTURE, null, null));
        assertTrue(AssemblyExclusionPolicy.excluded(FIXTURE, defender, defender));
    }

    @Test
    void anotherNationsProtectedBlocksStayBehind() {
        assertTrue(AssemblyExclusionPolicy.excluded(NATION_PROTECTED, defender, attacker));
        assertTrue(AssemblyExclusionPolicy.excluded(NATION_PROTECTED, defender, null));
    }

    @Test
    void theOwnerAssemblesItsOwnArmorAndUnownedArmorMoves() {
        assertFalse(AssemblyExclusionPolicy.excluded(NATION_PROTECTED, defender, defender));
        assertFalse(AssemblyExclusionPolicy.excluded(NATION_PROTECTED, null, attacker));
        assertFalse(AssemblyExclusionPolicy.excluded(NATION_PROTECTED, null, null));
    }

    @Test
    void ordinaryBlocksAlwaysMove() {
        assertFalse(AssemblyExclusionPolicy.excluded(ORDINARY, defender, attacker));
    }
}
