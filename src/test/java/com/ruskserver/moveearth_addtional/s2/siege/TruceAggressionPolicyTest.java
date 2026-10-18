package com.ruskserver.moveearth_addtional.s2.siege;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TruceAggressionPolicyTest {
    private final UUID shielded = UUID.randomUUID();
    private final UUID other = UUID.randomUUID();
    private static final long NOW = 1_800_000_000_000L;

    @Test
    void aShieldedNationStrikingFirstLosesItsTruce() {
        assertTrue(TruceAggressionPolicy.endsTruce(true, shielded, other, false, 0L, NOW));
    }

    @Test
    void strikingBackWithinTheWindowIsDefence() {
        assertFalse(TruceAggressionPolicy.endsTruce(true, shielded, other, false, NOW - 10_000L, NOW));
        assertTrue(TruceAggressionPolicy.endsTruce(true, shielded, other, false,
                NOW - TruceAggressionPolicy.SELF_DEFENSE_MILLIS - 1L, NOW));
    }

    @Test
    void onlyAShieldedNationAttackingAnotherNationCounts() {
        assertFalse(TruceAggressionPolicy.endsTruce(false, shielded, other, false, 0L, NOW));
        assertFalse(TruceAggressionPolicy.endsTruce(true, shielded, shielded, false, 0L, NOW));
        assertFalse(TruceAggressionPolicy.endsTruce(true, shielded, other, true, 0L, NOW));
        assertFalse(TruceAggressionPolicy.endsTruce(true, shielded, null, false, 0L, NOW));
        assertFalse(TruceAggressionPolicy.endsTruce(true, null, other, false, 0L, NOW));
    }
}
