package com.ruskserver.moveearth_addtional.s2.notification;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class NotificationIncidentPolicyTest {
    @Test
    void usesDocumentedCoreHealthThresholds() {
        assertEquals(100, NotificationIncidentPolicy.healthBand(760, 1000));
        assertEquals(75, NotificationIncidentPolicy.healthBand(750, 1000));
        assertEquals(50, NotificationIncidentPolicy.healthBand(500, 1000));
        assertEquals(25, NotificationIncidentPolicy.healthBand(250, 1000));
        assertEquals(10, NotificationIncidentPolicy.healthBand(100, 1000));
    }

    @Test
    void onlyNewLowerBandsNotify() {
        assertTrue(NotificationIncidentPolicy.crossed(null, 75));
        assertFalse(NotificationIncidentPolicy.crossed(75, 75));
        assertFalse(NotificationIncidentPolicy.crossed(75, 100));
        assertTrue(NotificationIncidentPolicy.crossed(75, 50));
        assertTrue(NotificationIncidentPolicy.crossed(25, 10));
    }
}
