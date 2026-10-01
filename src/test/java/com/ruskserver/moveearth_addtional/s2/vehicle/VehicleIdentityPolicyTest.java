package com.ruskserver.moveearth_addtional.s2.vehicle;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VehicleIdentityPolicyTest {
    private final UUID replacement = UUID.randomUUID();
    private final UUID dismantled = UUID.randomUUID();
    private final UUID other = UUID.randomUUID();

    @Test
    void aRecoredTurretStillResolvesToTheNewCore() {
        // Hull carries the new core's id; the turret joined by a bearing still carries the dismantled one.
        Set<UUID> live = Set.of(replacement);
        assertEquals(Optional.of(replacement),
                VehicleIdentityPolicy.resolve(List.of(replacement, dismantled), live::contains));
        assertEquals(Set.of(dismantled), VehicleIdentityPolicy.stale(List.of(replacement, dismantled), live::contains));
    }

    @Test
    void aSingleLiveIdResolves() {
        assertEquals(Optional.of(replacement),
                VehicleIdentityPolicy.resolve(List.of(replacement, replacement), id -> true));
    }

    @Test
    void onlyStaleIdsResolveToNothing() {
        assertTrue(VehicleIdentityPolicy.resolve(List.of(dismantled), id -> false).isEmpty());
        assertTrue(VehicleIdentityPolicy.resolve(List.of(), id -> true).isEmpty());
    }

    @Test
    void twoLiveIdsStayAmbiguous() {
        assertTrue(VehicleIdentityPolicy.resolve(List.of(replacement, other), id -> true).isEmpty());
        assertTrue(VehicleIdentityPolicy.stale(List.of(replacement, other), id -> true).isEmpty());
    }
}
