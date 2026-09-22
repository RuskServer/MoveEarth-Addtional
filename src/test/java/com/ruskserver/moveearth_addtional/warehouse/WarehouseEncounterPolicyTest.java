package com.ruskserver.moveearth_addtional.warehouse;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class WarehouseEncounterPolicyTest {
    @Test
    void halfHealthNoticeIncludesLargeOneHitDamageOnlyOnce() {
        assertTrue(WarehouseEncounterPolicy.firstHalfHealthHit(true, 0, 320));
        assertTrue(WarehouseEncounterPolicy.firstHalfHealthHit(true, 160, 320));
        assertFalse(WarehouseEncounterPolicy.firstHalfHealthHit(false, 100, 320));
        assertFalse(WarehouseEncounterPolicy.firstHalfHealthHit(true, 180, 320));
        assertFalse(WarehouseEncounterPolicy.firstHalfHealthHit(true, 0, 0));
    }

    @Test
    void transitionsOnlyAcceptTheActiveBoss() {
        var first = java.util.UUID.randomUUID();
        var other = java.util.UUID.randomUUID();
        assertTrue(WarehouseEncounterPolicy.matchesBoss(true, first, first));
        assertFalse(WarehouseEncounterPolicy.matchesBoss(true, first, other));
        assertFalse(WarehouseEncounterPolicy.matchesBoss(false, first, first));
        assertFalse(WarehouseEncounterPolicy.matchesBoss(true, null, first));
    }

    @Test
    void cooldownUsesOnlyTheOpenTimeClock() {
        assertFalse(WarehouseEncounterPolicy.deadlineReached(100, 200));
        assertTrue(WarehouseEncounterPolicy.deadlineReached(200, 200));
        assertFalse(WarehouseEncounterPolicy.deadlineReached(999, 0));
    }
}
