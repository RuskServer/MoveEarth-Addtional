package com.ruskserver.moveearth_addtional.analytics;

import com.ruskserver.moveearth_addtional.analytics.queue.AnalyticsEventQueue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
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
    public void testLowPriorityDroppedAtHighCapacity() {
        // 容量3のキューで2件入ると 66% >= 90% (3 * 0.9 = 2.7 -> size >= 2.7 つまり3でLOWは破棄)
        AnalyticsEventQueue q10 = new AnalyticsEventQueue(10);
        // 9件NORMALを投入 (90%)
        for (int i = 0; i < 9; i++) {
            assertTrue(q10.enqueue(new AnalyticsEventQueue.PlayerActivityFlushEvent(Collections.emptyList())));
        }
        assertEquals(9, q10.size());
        assertEquals(0, q10.getDroppedEventsCount());

        // 10件目にLOW（空間サンプル）を投入すると、90%以上のため破棄される
        assertFalse(q10.enqueue(new AnalyticsEventQueue.SpatialActivityFlushEvent(Collections.emptyList())));
        assertEquals(9, q10.size());
        assertEquals(1, q10.getDroppedEventsCount());

        // NORMALは投入できる
        assertTrue(q10.enqueue(new AnalyticsEventQueue.PlayerActivityFlushEvent(Collections.emptyList())));
        assertEquals(10, q10.size());
    }

    @Test
    public void testHighPriorityEvictsWhenFull() {
        // NORMALイベントで満杯にする
        AnalyticsEventQueue.PlayerActivityFlushEvent normal1 = new AnalyticsEventQueue.PlayerActivityFlushEvent(Collections.emptyList());
        AnalyticsEventQueue.PlayerActivityFlushEvent normal2 = new AnalyticsEventQueue.PlayerActivityFlushEvent(Collections.emptyList());
        AnalyticsEventQueue.PlayerActivityFlushEvent normal3 = new AnalyticsEventQueue.PlayerActivityFlushEvent(Collections.emptyList());

        assertTrue(queue.enqueue(normal1));
        assertTrue(queue.enqueue(normal2));
        assertTrue(queue.enqueue(normal3));
        assertEquals(3, queue.size());
        assertEquals(0, queue.getDroppedEventsCount());

        // 満杯時にNORMALを投入すると破棄される
        AnalyticsEventQueue.PlayerActivityFlushEvent normal4 = new AnalyticsEventQueue.PlayerActivityFlushEvent(Collections.emptyList());
        assertFalse(queue.enqueue(normal4));
        assertEquals(3, queue.size());
        assertEquals(1, queue.getDroppedEventsCount());

        // 満杯時にHIGH（セッション開始）を投入すると、先頭のnormal1が追い出されて格納される
        AnalyticsEventQueue.SessionStartEvent highEvent = new AnalyticsEventQueue.SessionStartEvent(
                UUID.randomUUID(), UUID.randomUUID(), "ImportantPlayer", 2000L);
        assertTrue(queue.enqueue(highEvent));
        assertEquals(3, queue.size());
        assertEquals(2, queue.getDroppedEventsCount()); // normal4と追い出されたnormal1で合計2

        // キューの中身は normal2, normal3, highEvent
        assertEquals(normal2, queue.poll());
        assertEquals(normal3, queue.poll());
        assertEquals(highEvent, queue.poll());
    }

    @Test
    public void testHighPriorityProtectsOtherHighEvents() {
        // 容量3のキューで HIGH1, LOW1, HIGH2 の順に格納
        AnalyticsEventQueue.SessionStartEvent high1 = new AnalyticsEventQueue.SessionStartEvent(
                UUID.randomUUID(), UUID.randomUUID(), "High1", 1000L);
        AnalyticsEventQueue.SpatialActivityFlushEvent low1 = new AnalyticsEventQueue.SpatialActivityFlushEvent(Collections.emptyList());
        AnalyticsEventQueue.SessionStartEvent high2 = new AnalyticsEventQueue.SessionStartEvent(
                UUID.randomUUID(), UUID.randomUUID(), "High2", 2000L);

        AnalyticsEventQueue q3 = new AnalyticsEventQueue(3);
        assertTrue(q3.enqueue(high1));
        assertTrue(q3.enqueue(low1));
        assertTrue(q3.enqueue(high2));
        assertEquals(3, q3.size());

        // 満杯時に新しい HIGH3 を投入すると、先頭の high1 ではなく、間にある low1 が追い出される
        AnalyticsEventQueue.SessionStartEvent high3 = new AnalyticsEventQueue.SessionStartEvent(
                UUID.randomUUID(), UUID.randomUUID(), "High3", 3000L);
        assertTrue(q3.enqueue(high3));
        assertEquals(3, q3.size());

        // キューの中身は high1, high2, high3（high1が保護され、low1が破棄された）
        assertEquals(high1, q3.poll());
        assertEquals(high2, q3.poll());
        assertEquals(high3, q3.poll());
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
