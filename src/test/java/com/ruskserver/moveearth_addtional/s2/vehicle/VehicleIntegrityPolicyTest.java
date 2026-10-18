package com.ruskserver.moveearth_addtional.s2.vehicle;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VehicleIntegrityPolicyTest {
    private final UUID body = UUID.randomUUID();
    private final UUID goneBody = UUID.randomUUID();
    private final UUID vehicle = UUID.randomUUID();
    private final UUID nation = UUID.randomUUID();
    private final UUID rival = UUID.randomUUID();

    @Test
    void aRecordOnAReusedPlotIsAGhostUnlessItsCoreStandsThere() {
        assertEquals(VehicleIntegrityPolicy.PlotRecord.KEEP, VehicleIntegrityPolicy.plotRecord(body, body, true));
        assertEquals(VehicleIntegrityPolicy.PlotRecord.RELINK, VehicleIntegrityPolicy.plotRecord(goneBody, body, true));
        assertEquals(VehicleIntegrityPolicy.PlotRecord.GHOST, VehicleIntegrityPolicy.plotRecord(goneBody, body, false));
        assertEquals(VehicleIntegrityPolicy.PlotRecord.GHOST, VehicleIntegrityPolicy.plotRecord(null, body, false));
    }

    @Test
    void aCoreGetsItsRecordBackOnlyWhereTheBodyShowsItWasThisVehicles() {
        assertTrue(VehicleIntegrityPolicy.restoresCore(vehicle, nation, vehicle, null, true));
        assertTrue(VehicleIntegrityPolicy.restoresCore(vehicle, nation, null, nation, true));
        // A copy set down on someone else's body, or for a nation that no longer exists, proves nothing.
        assertFalse(VehicleIntegrityPolicy.restoresCore(vehicle, nation, UUID.randomUUID(), rival, true));
        assertFalse(VehicleIntegrityPolicy.restoresCore(vehicle, nation, vehicle, nation, false));
        assertFalse(VehicleIntegrityPolicy.restoresCore(null, nation, null, nation, true));
    }

    @Test
    void anUnownedBodyGoesToTheOneNationWhoseStorageIsOnIt() {
        Set<UUID> existing = Set.of(nation, rival);
        Map<UUID, Integer> one = Map.of(nation, 4);
        assertEquals(nation, VehicleIntegrityPolicy.inferredNation(one, existing::contains));
        Map<UUID, Integer> two = new LinkedHashMap<>();
        two.put(nation, 4);
        two.put(rival, 1);
        assertNull(VehicleIntegrityPolicy.inferredNation(two, existing::contains));
        // A disbanded nation's leftovers do not make the answer ambiguous.
        Map<UUID, Integer> withGone = new LinkedHashMap<>();
        withGone.put(UUID.randomUUID(), 2);
        withGone.put(nation, 1);
        assertEquals(nation, VehicleIntegrityPolicy.inferredNation(withGone, existing::contains));
        assertNull(VehicleIntegrityPolicy.inferredNation(Map.of(), existing::contains));
    }

    @Test
    void aMissingCoreIsDroppedOnlyAfterSeveralSweeps() {
        assertFalse(VehicleIntegrityPolicy.dropsMissingCore(1));
        assertFalse(VehicleIntegrityPolicy.dropsMissingCore(VehicleIntegrityPolicy.MISSING_CORE_STRIKES - 1));
        assertTrue(VehicleIntegrityPolicy.dropsMissingCore(VehicleIntegrityPolicy.MISSING_CORE_STRIKES));
    }
}
