package com.ruskserver.moveearth_addtional.s2.reinforcement;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ReinforcementDamageLimiterTest {
    @Test
    void limitsSamePlayerAndBlockButNotOtherTargets() {
        ReinforcementDamageLimiter limiter = new ReinforcementDamageLimiter(5);
        UUID player = UUID.randomUUID();
        assertTrue(limiter.tryDamage(player, "overworld", 10L, 100L));
        assertFalse(limiter.tryDamage(player, "overworld", 10L, 104L));
        assertTrue(limiter.tryDamage(player, "overworld", 11L, 104L));
        assertTrue(limiter.tryDamage(player, "overworld", 10L, 105L));
    }
}
