package com.ruskserver.moveearth_addtional.s2.reinforcement;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TerritoryIntrusionPolicyTest {
    @Test void firstBreakNotifiesThenAtMostEveryFiveMinutes() {
        assertTrue(TerritoryIntrusionPolicy.shouldNotify(0L, 1_000L));
        assertFalse(TerritoryIntrusionPolicy.shouldNotify(1_000L, 1_000L + 299_999L));
        assertTrue(TerritoryIntrusionPolicy.shouldNotify(1_000L, 1_000L + 300_000L));
    }
}
