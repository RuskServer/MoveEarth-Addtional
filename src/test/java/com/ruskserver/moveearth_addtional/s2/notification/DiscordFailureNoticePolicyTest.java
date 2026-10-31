package com.ruskserver.moveearth_addtional.s2.notification;

import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class DiscordFailureNoticePolicyTest {
    @Test void retriesAreThrottledButTerminalLossStillWarns() {
        var policy = new DiscordFailureNoticePolicy();
        UUID nation = UUID.randomUUID(), player = UUID.randomUUID();
        assertTrue(policy.allow(nation, player, false, 1_000));
        assertFalse(policy.allow(nation, player, false, 2_000));
        assertTrue(policy.allow(nation, player, true, 2_000));
        assertFalse(policy.allow(nation, player, true, 3_000));
        assertTrue(policy.allow(nation, player, false,
                1_000 + DiscordFailureNoticePolicy.COOLDOWN_MILLIS));
    }

    @Test void recipientsAndNationsAreIndependent() {
        var policy = new DiscordFailureNoticePolicy();
        UUID nation = UUID.randomUUID(), player = UUID.randomUUID();
        assertTrue(policy.allow(nation, player, false, 1_000));
        assertTrue(policy.allow(nation, UUID.randomUUID(), false, 1_001));
        assertTrue(policy.allow(UUID.randomUUID(), player, false, 1_001));
        policy.clear();
        assertTrue(policy.allow(nation, player, false, 1_002));
    }

    @Test void clockRollbackDoesNotSuppressWarningsIndefinitely() {
        var policy = new DiscordFailureNoticePolicy();
        UUID nation = UUID.randomUUID(), player = UUID.randomUUID();
        assertTrue(policy.allow(nation, player, false, 5_000));
        assertTrue(policy.allow(nation, player, false, 1_000));
    }
}
