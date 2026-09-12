package com.ruskserver.moveearth_addtional.s2.notification;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class NotificationDeliveryPolicyTest {
    @Test
    void requiresLinkOptInAndQueueCapacity() {
        assertFalse(NotificationDeliveryPolicy.canQueue(false, true, 0));
        assertFalse(NotificationDeliveryPolicy.canQueue(true, false, 0));
        assertFalse(NotificationDeliveryPolicy.canQueue(true, true, NotificationDeliveryPolicy.MAX_OUTBOX));
        assertTrue(NotificationDeliveryPolicy.canQueue(true, true,
                NotificationDeliveryPolicy.MAX_OUTBOX - 1));
    }

    @Test
    void retryBackoffIsBoundedAndPoisonDeliveriesExpire() {
        assertEquals(5_000L, NotificationDeliveryPolicy.retryDelayMillis(1));
        assertEquals(10_000L, NotificationDeliveryPolicy.retryDelayMillis(2));
        assertEquals(15 * 60_000L, NotificationDeliveryPolicy.retryDelayMillis(99));
        assertFalse(NotificationDeliveryPolicy.shouldDrop(NotificationDeliveryPolicy.MAX_ATTEMPTS - 1));
        assertTrue(NotificationDeliveryPolicy.shouldDrop(NotificationDeliveryPolicy.MAX_ATTEMPTS));
    }

    @Test
    void retentionAndDeduplicationUseExplicitWindows() {
        assertFalse(NotificationDeliveryPolicy.expired(1_000L, 10_999L, 10_000L));
        assertTrue(NotificationDeliveryPolicy.expired(1_000L, 11_000L, 10_000L));
        assertFalse(NotificationDeliveryPolicy.expired(1_000L, 50_000L, 0L));
        assertTrue(NotificationDeliveryPolicy.duplicate(1_000L, 10_999L, 10_000L));
        assertFalse(NotificationDeliveryPolicy.duplicate(1_000L, 11_000L, 10_000L));
        assertFalse(NotificationDeliveryPolicy.duplicate(1_000L, 2_000L, 0L));
    }
}
