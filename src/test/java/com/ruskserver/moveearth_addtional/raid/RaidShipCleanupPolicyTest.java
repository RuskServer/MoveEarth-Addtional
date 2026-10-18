package com.ruskserver.moveearth_addtional.raid;

import org.junit.jupiter.api.Test;

import java.util.OptionalInt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RaidShipCleanupPolicyTest {
    @Test
    void raidAirshipsAreRecognisedByTheirName() {
        assertEquals(OptionalInt.of(12), RaidShipCleanupPolicy.raidIdFromName("MoveEarth Raid #12"));
        assertEquals(OptionalInt.empty(), RaidShipCleanupPolicy.raidIdFromName("Raid #12"));
        assertEquals(OptionalInt.empty(), RaidShipCleanupPolicy.raidIdFromName("MoveEarth Raid #twelve"));
        assertEquals(OptionalInt.empty(), RaidShipCleanupPolicy.raidIdFromName(null));
    }

    @Test
    void anAirshipGoesOnlyOnceItsRaidIsOverAndNobodyClaimedIt() {
        assertTrue(RaidShipCleanupPolicy.remove(false, false));
        assertFalse(RaidShipCleanupPolicy.remove(true, false), "its raid is still running");
        assertFalse(RaidShipCleanupPolicy.remove(false, true), "a player made it their vehicle");
    }
}
