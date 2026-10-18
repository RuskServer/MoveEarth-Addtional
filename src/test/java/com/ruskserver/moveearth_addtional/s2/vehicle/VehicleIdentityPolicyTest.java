package com.ruskserver.moveearth_addtional.s2.vehicle;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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

    private final UUID hull = UUID.randomUUID();
    private final UUID turret = UUID.randomUUID();
    private final UUID fragment = UUID.randomUUID();
    private final UUID otherShip = UUID.randomUUID();
    private final UUID nation = UUID.randomUUID();
    private final UUID enemy = UUID.randomUUID();

    @Test
    void aChainHoldingTheCoreIsGovernedByIt() {
        assertEquals(Optional.of(replacement), VehicleIdentityPolicy.governing(turret,
                Map.of(hull, replacement, turret, replacement), Set.of(hull, turret),
                id -> id.equals(replacement) ? hull : null));
    }

    @Test
    void aSplitOffFragmentKeepsItsIdButIsNotGoverned() {
        // The core went with another piece; the fragment still carries the id but holds no core.
        assertEquals(Optional.empty(), VehicleIdentityPolicy.governing(fragment, Map.of(fragment, replacement),
                Set.of(fragment), id -> hull));
    }

    @Test
    void twoShipsTiedByARopeEachKeepTheirOwn() {
        Map<UUID, UUID> bound = Map.of(hull, replacement, otherShip, other);
        Set<UUID> chain = Set.of(hull, otherShip);
        java.util.function.Function<UUID, UUID> coreBody = id -> id.equals(replacement) ? hull : otherShip;
        assertEquals(Optional.of(replacement), VehicleIdentityPolicy.governing(hull, bound, chain, coreBody));
        assertEquals(Optional.of(other), VehicleIdentityPolicy.governing(otherShip, bound, chain, coreBody));
        // A body carrying nothing between them belongs to neither.
        assertEquals(Optional.empty(), VehicleIdentityPolicy.governing(turret, bound,
                Set.of(hull, otherShip, turret), coreBody));
    }

    @Test
    void deadAndDetachedSameNationIdsAreHandedToTheGoverningCore() {
        Set<UUID> chain = Set.of(hull, turret);
        assertTrue(VehicleIdentityPolicy.rebindable(dismantled, replacement, false, null, chain, null, nation));
        // The turret still carries the old craft's live id, whose core now sits on a body outside this chain.
        assertTrue(VehicleIdentityPolicy.rebindable(other, replacement, true, fragment, chain, nation, nation));
        assertFalse(VehicleIdentityPolicy.rebindable(other, replacement, true, fragment, chain, enemy, nation));
        assertFalse(VehicleIdentityPolicy.rebindable(other, replacement, true, turret, chain, nation, nation));
        assertFalse(VehicleIdentityPolicy.rebindable(replacement, replacement, true, hull, chain, nation, nation));
    }
}
