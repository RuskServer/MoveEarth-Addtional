package com.ruskserver.moveearth_addtional.s2.siege;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SiegeFallAttributionPolicyTest {
    private static SiegeFallAttributionPolicy.Candidate siege(boolean individual, boolean rolling, long remaining) {
        return new SiegeFallAttributionPolicy.Candidate(UUID.randomUUID(), individual, rolling, remaining);
    }

    @Test
    void anAltCannotTakeANationsFall() {
        var alt = siege(true, true, 36_000L);
        var nation = siege(false, true, 30_000L);
        assertEquals(nation.siegeId(), SiegeFallAttributionPolicy.credited(alt, List.of(alt, nation)));
    }

    @Test
    void theFinisherKeepsItsFallWithoutANationSiegeToOutrankIt() {
        var alt = siege(true, true, 36_000L);
        var otherIndividual = siege(true, true, 36_000L);
        assertEquals(alt.siegeId(), SiegeFallAttributionPolicy.credited(alt, List.of(alt, otherIndividual)));
        var nation = siege(false, true, 1_000L);
        var otherNation = siege(false, true, 36_000L);
        assertEquals(nation.siegeId(), SiegeFallAttributionPolicy.credited(nation, List.of(nation, otherNation)),
                "between nations the last point decides");
    }

    @Test
    void theRollingNationSiegeThatHitMostRecentlyIsCredited() {
        var alt = siege(true, true, 36_000L);
        var initialOnly = siege(false, false, 6_000L);
        var older = siege(false, true, 10_000L);
        var recent = siege(false, true, 35_000L);
        assertEquals(recent.siegeId(),
                SiegeFallAttributionPolicy.credited(alt, List.of(alt, initialOnly, older, recent)));
        assertEquals(initialOnly.siegeId(), SiegeFallAttributionPolicy.credited(alt, List.of(alt, initialOnly)));
    }

    @Test
    void theDefenderCannotSurrenderToAnAltDuringANationSiege() {
        assertFalse(SiegeFallAttributionPolicy.surrenderAllowed(true, true));
        assertTrue(SiegeFallAttributionPolicy.surrenderAllowed(true, false));
        assertTrue(SiegeFallAttributionPolicy.surrenderAllowed(false, true));
    }

    @Test
    void withdrawingAfterRealDamageIsAFailedSiege() {
        assertTrue(SiegeFallAttributionPolicy.withdrawalLocksCore(true, false));
        assertTrue(SiegeFallAttributionPolicy.withdrawalLocksCore(false, true));
        assertFalse(SiegeFallAttributionPolicy.withdrawalLocksCore(false, false), "initial lock only");
    }
}
