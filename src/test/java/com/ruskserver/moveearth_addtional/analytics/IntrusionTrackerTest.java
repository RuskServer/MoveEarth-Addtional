package com.ruskserver.moveearth_addtional.analytics;

import com.ruskserver.moveearth_addtional.analytics.model.DetectorActivityBucket;
import com.ruskserver.moveearth_addtional.analytics.tracker.IntrusionTracker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class IntrusionTrackerTest {

    private IntrusionTracker tracker;
    private UUID groupOwner;
    private UUID member;
    private UUID intruder;

    @BeforeEach
    public void setUp() {
        tracker = new IntrusionTracker();
        groupOwner = UUID.randomUUID();
        member = UUID.randomUUID();
        intruder = UUID.randomUUID();
    }

    @Test
    public void testIntrusionSessionTracking() {
        String dim = "minecraft:overworld";
        String hash = "pos_hash_1";
        long time = 1000000L;

        // 1回目のスキャン: メンバー1人と侵入者1人を検知 -> 侵入セッション開始 (count = 1)
        tracker.recordScan(dim, hash, "北門", groupOwner, Set.of(member), Collections.emptySet(), Set.of(intruder), time);

        // 2回目のスキャン (5秒後): 同じ侵入者がまだ滞在 -> セッション数は増えない
        tracker.recordScan(dim, hash, "北門", groupOwner, Set.of(member), Collections.emptySet(), Set.of(intruder), time + 5000L);

        // 3回目のスキャン (10秒後): 侵入者が退出
        tracker.recordScan(dim, hash, "北門", groupOwner, Set.of(member), Collections.emptySet(), Collections.emptySet(), time + 10000L);

        // 4回目のスキャン (15秒後): 同じ侵入者が再入域 -> 新たなセッション開始 (count = 2)
        tracker.recordScan(dim, hash, "北門", groupOwner, Set.of(member), Collections.emptySet(), Set.of(intruder), time + 15000L);

        // バケットフラッシュ
        List<DetectorActivityBucket> buckets = tracker.flushBucket(time / 1000L);
        assertEquals(1, buckets.size());

        DetectorActivityBucket bucket = buckets.getFirst();
        assertEquals(dim, bucket.dimension());
        assertEquals(hash, bucket.detectorPosHash());
        assertEquals("北門", bucket.detectorName());
        assertEquals(groupOwner, bucket.groupOwnerUuid());
        assertEquals(2, bucket.intrusionSessions()); // 2セッション
        assertEquals(1, bucket.distinctMembers());
        assertEquals(1, bucket.distinctVisitors());
    }

    @Test
    public void testFlushResetsBucketAccumulators() {
        String dim = "minecraft:overworld";
        String hash = "pos_hash_1";
        long time = 1000000L;

        tracker.recordScan(dim, hash, "北門", groupOwner, Set.of(member), Collections.emptySet(), Set.of(intruder), time);

        List<DetectorActivityBucket> buckets1 = tracker.flushBucket(time / 1000L);
        assertEquals(1, buckets1.size());

        // 次のフラッシュ（新たなスキャンなし）は空
        List<DetectorActivityBucket> buckets2 = tracker.flushBucket((time + 300000L) / 1000L);
        assertEquals(0, buckets2.size());
    }

    @Test
    public void testLatestDetectorNameIsUsedWithoutChangingIdentity() {
        String dim = "minecraft:overworld";
        String hash = "stable_detector_id";
        long time = 1000000L;

        tracker.recordScan(dim, hash, "北門", groupOwner,
                Set.of(member), Collections.emptySet(), Set.of(intruder), time);
        tracker.recordScan(dim, hash, "正門", groupOwner,
                Set.of(member), Collections.emptySet(), Set.of(intruder), time + 5000L);

        List<DetectorActivityBucket> buckets = tracker.flushBucket(time / 1000L);
        assertEquals(1, buckets.size());
        assertEquals(hash, buckets.getFirst().detectorPosHash());
        assertEquals("正門", buckets.getFirst().detectorName());
        assertEquals(1, buckets.getFirst().intrusionSessions());
    }
}
