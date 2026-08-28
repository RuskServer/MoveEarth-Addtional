package com.ruskserver.moveearth_addtional.analytics;

import com.ruskserver.moveearth_addtional.analytics.queue.AnalyticsEventQueue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class AnalyticsEventQueueTest {

    private AnalyticsEventQueue queue;

    @BeforeEach
    public void setUp() {
        // 容量3の小規模キューでテスト
        queue = new AnalyticsEventQueue(3);
    }

    @Test
    public void testEnqueueAndPoll() {
        assertEquals(0, queue.size());
        assertEquals(0, queue.getDroppedEventsCount());

        AnalyticsEventQueue.SessionStartEvent event1 = new AnalyticsEventQueue.SessionStartEvent(
                UUID.randomUUID(), UUID.randomUUID(), "Player1", 1000L);
        AnalyticsEventQueue.SessionStartEvent event2 = new AnalyticsEventQueue.SessionStartEvent(
                UUID.randomUUID(), UUID.randomUUID(), "Player2", 1001L);

        assertTrue(queue.enqueue(event1));
        assertTrue(queue.enqueue(event2));
        assertEquals(2, queue.size());

        assertEquals(event1, queue.poll());
        assertEquals(1, queue.size());
        assertEquals(event2, queue.poll());
        assertEquals(0, queue.size());
    }

    @Test
    public void testQueueOverflowDropsAndCounts() {
        AnalyticsEventQueue.SessionStartEvent event1 = new AnalyticsEventQueue.SessionStartEvent(
                UUID.randomUUID(), UUID.randomUUID(), "Player1", 1000L);
        AnalyticsEventQueue.SessionStartEvent event2 = new AnalyticsEventQueue.SessionStartEvent(
                UUID.randomUUID(), UUID.randomUUID(), "Player2", 1001L);
        AnalyticsEventQueue.SessionStartEvent event3 = new AnalyticsEventQueue.SessionStartEvent(
                UUID.randomUUID(), UUID.randomUUID(), "Player3", 1002L);
        AnalyticsEventQueue.SessionStartEvent event4 = new AnalyticsEventQueue.SessionStartEvent(
                UUID.randomUUID(), UUID.randomUUID(), "Player4", 1003L);

        assertTrue(queue.enqueue(event1));
        assertTrue(queue.enqueue(event2));
        assertTrue(queue.enqueue(event3));
        assertEquals(3, queue.size());
        assertEquals(0, queue.getDroppedEventsCount());

        // 4件目は上限超過で破棄される
        assertFalse(queue.enqueue(event4));
        assertEquals(3, queue.size());
        assertEquals(1, queue.getDroppedEventsCount());
    }

    @Test
    public void testDrainTo() {
        AnalyticsEventQueue.SessionStartEvent event1 = new AnalyticsEventQueue.SessionStartEvent(
                UUID.randomUUID(), UUID.randomUUID(), "Player1", 1000L);
        AnalyticsEventQueue.SessionStartEvent event2 = new AnalyticsEventQueue.SessionStartEvent(
                UUID.randomUUID(), UUID.randomUUID(), "Player2", 1001L);

        queue.enqueue(event1);
        queue.enqueue(event2);

        List<AnalyticsEventQueue.AnalyticsEvent> buffer = new ArrayList<>();
        int drained = queue.drainTo(buffer, 10);

        assertEquals(2, drained);
        assertEquals(2, buffer.size());
        assertEquals(0, queue.size());
    }
}
