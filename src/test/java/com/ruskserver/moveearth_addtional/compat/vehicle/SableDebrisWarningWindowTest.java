package com.ruskserver.moveearth_addtional.compat.vehicle;

import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class SableDebrisWarningWindowTest {
    @Test
    void lateArrivalGetsAFullGracePeriodInsteadOfAnAlreadyExpiredDeadline() {
        Object body = new Object();
        var window = new SableDebrisWarningWindow<Object>(body, 0, 1200);
        UUID player = UUID.randomUUID();
        Object session = new Object();
        assertTrue(window.warn(player, session, 1199));
        assertEquals(SableDebrisCleanupPolicy.Action.WAIT,
                SableDebrisCleanupPolicy.review(false, 65, 64, 1200, window.since(), 1200).action());
        assertEquals(SableDebrisCleanupPolicy.Action.REMOVE,
                SableDebrisCleanupPolicy.review(false, 65, 64, 2399, window.since(), 1200).action());
    }

    @Test
    void sameSessionDoesNotKeepExtendingTheDeadlineButReconnectDoes() {
        Object body = new Object();
        var window = new SableDebrisWarningWindow<Object>(body, 0, 1200);
        UUID player = UUID.randomUUID();
        Object first = new Object();
        assertTrue(window.warn(player, first, 100));
        assertFalse(window.warn(player, first, 200));
        assertEquals(100, window.since());
        assertTrue(window.warn(player, new Object(), 300));
        assertEquals(300, window.since());
    }

    @Test
    void aReloadedBodyCannotInheritThePreviousInstancesWarningDeadline() {
        Object oldBody = new Object();
        var window = new SableDebrisWarningWindow<Object>(oldBody, 0, 1200);
        assertTrue(window.matches(oldBody));
        assertFalse(window.matches(new Object()));
    }

    @Test
    void configReloadCannotShortenTheGracePeriodAlreadyAnnounced() {
        Object body = new Object();
        var window = new SableDebrisWarningWindow<Object>(body, 0, 1200);
        window.extendGrace(200);
        assertEquals(SableDebrisCleanupPolicy.Action.WAIT,
                SableDebrisCleanupPolicy.review(false, 65, 64, 300, window.since(), window.graceTicks()).action());
        window.extendGrace(2400);
        assertEquals(2400, window.graceTicks());
    }
}
