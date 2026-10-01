package com.ruskserver.moveearth_addtional.s2.vehicle;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static com.ruskserver.moveearth_addtional.s2.vehicle.VehicleCoreDismantlePolicy.Decision.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VehicleCoreDismantlePolicyTest {
    private final UUID attackerNation = UUID.randomUUID();
    private final UUID attackerPlayer = UUID.randomUUID();
    private final UUID bystander = UUID.randomUUID();

    @Test
    void aHealthyParkedVehicleMayBeDismantled() {
        assertEquals(ALLOWED, VehicleCoreDismantlePolicy.owner(true, 600, 600, 1_000, 0));
        assertEquals(ALLOWED, VehicleCoreDismantlePolicy.owner(true, 600, 600, 1_000, 1_000));
    }

    @Test
    void combatBlocksDismantlingEvenAtFullHealth() {
        assertEquals(IN_COMBAT, VehicleCoreDismantlePolicy.owner(true, 600, 600, 1_000, 1_001));
        assertEquals(IN_COMBAT, VehicleCoreDismantlePolicy.owner(true, 100, 600, 1_000, 5_000));
    }

    @Test
    void aDamagedOrDestroyedCoreMustBeRepairedFirst() {
        assertEquals(DAMAGED, VehicleCoreDismantlePolicy.owner(true, 599, 600, 1_000, 0));
        assertEquals(DAMAGED, VehicleCoreDismantlePolicy.owner(true, 0, 600, 1_000, 0));
    }

    @Test
    void ownMembersWithoutPermissionMayNotDismantle() {
        assertEquals(NO_PERMISSION, VehicleCoreDismantlePolicy.owner(false, 600, 600, 1_000, 0));
    }

    @Test
    void outsidersNeedTheCoreDestroyedAndMustNotEraseSomeoneElsesSalvage() {
        assertEquals(OPERATIONAL, VehicleCoreDismantlePolicy.outsider(1, false));
        assertEquals(ALLOWED, VehicleCoreDismantlePolicy.outsider(0, false));
        assertEquals(LOOT_PROTECTED, VehicleCoreDismantlePolicy.outsider(0, true));
    }

    @Test
    void onlyTheGrantedSideHoldsLiveSalvage() {
        // Nation grant: members of that nation may break, anyone else is held off until it expires.
        assertFalse(VehicleCoreDismantlePolicy.salvageHeldByOthers(true, 100, 50, false, attackerNation,
                bystander, attackerNation));
        assertTrue(VehicleCoreDismantlePolicy.salvageHeldByOthers(true, 100, 50, false, attackerNation,
                bystander, UUID.randomUUID()));
        assertTrue(VehicleCoreDismantlePolicy.salvageHeldByOthers(true, 100, 50, false, attackerNation,
                bystander, null));
        assertFalse(VehicleCoreDismantlePolicy.salvageHeldByOthers(true, 100, 100, false, attackerNation,
                bystander, null));
        // Individual grant: only that player.
        assertFalse(VehicleCoreDismantlePolicy.salvageHeldByOthers(true, 100, 50, true, attackerPlayer,
                attackerPlayer, null));
        assertTrue(VehicleCoreDismantlePolicy.salvageHeldByOthers(true, 100, 50, true, attackerPlayer,
                bystander, attackerPlayer));
        assertFalse(VehicleCoreDismantlePolicy.salvageHeldByOthers(false, 100, 50, true, attackerPlayer,
                bystander, null));
    }

    @Test
    void remainingSecondsRoundUp() {
        assertEquals(0, VehicleCoreDismantlePolicy.secondsUntil(100, 100));
        assertEquals(1, VehicleCoreDismantlePolicy.secondsUntil(100, 101));
        assertEquals(1, VehicleCoreDismantlePolicy.secondsUntil(100, 120));
        assertEquals(2, VehicleCoreDismantlePolicy.secondsUntil(100, 121));
    }
}
