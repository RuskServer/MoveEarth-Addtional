package com.ruskserver.moveearth_addtional.analytics;

import com.ruskserver.moveearth_addtional.analytics.activity.ActivityCategory;
import com.ruskserver.moveearth_addtional.analytics.activity.PlayerActivityTracker;
import com.ruskserver.moveearth_addtional.analytics.config.AnalyticsConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class PlayerActivityTrackerTest {

    private PlayerActivityTracker tracker;
    private UUID playerUuid;

    @BeforeEach
    public void setUp() {
        tracker = new PlayerActivityTracker();
        playerUuid = UUID.randomUUID();
    }

    @Test
    public void testLoginAndInitialState() {
        long startTime = 100000L;

        tracker.onPlayerLogin(playerUuid, 100.0, 64.0, 100.0, "minecraft:overworld", startTime);

        assertEquals(startTime, tracker.getLastActiveTimeMs(playerUuid));
        assertFalse(tracker.isAfk(playerUuid, startTime));
        assertFalse(tracker.isAfk(playerUuid, startTime + AnalyticsConfig.AFK_THRESHOLD_MS - 1000));
        assertTrue(tracker.isAfk(playerUuid, startTime + AnalyticsConfig.AFK_THRESHOLD_MS));
    }

    @Test
    public void testRecordActivityExtendsActiveTime() {
        long startTime = 100000L;
        tracker.onPlayerLogin(playerUuid, 0.0, 0.0, 0.0, "minecraft:overworld", startTime);

        long activityTime = startTime + 4 * 60 * 1000L; // 4分後
        tracker.recordActivity(playerUuid, ActivityCategory.MINING, activityTime);

        assertEquals(activityTime, tracker.getLastActiveTimeMs(playerUuid));
        assertFalse(tracker.isAfk(playerUuid, startTime + 6 * 60 * 1000L));
        assertTrue(tracker.isAfk(playerUuid, activityTime + AnalyticsConfig.AFK_THRESHOLD_MS));
    }

    @Test
    public void testMovementDistanceAndTeleportExclusion() {
        long startTime = 100000L;
        tracker.onPlayerLogin(playerUuid, 0.0, 0.0, 0.0, "minecraft:overworld", startTime);

        // 1. 小さな移動（1.0m < 2.0m 閾値）-> 距離 0.0
        double dist1 = tracker.updatePositionAndGetDistance(playerUuid, 1.0, 0.0, 0.0, "minecraft:overworld", startTime + 1000L);
        assertEquals(0.0, dist1);

        // 2. 有効な通常移動（5.0m）-> 距離 5.0m
        double dist2 = tracker.updatePositionAndGetDistance(playerUuid, 5.0, 0.0, 0.0, "minecraft:overworld", startTime + 2000L);
        assertEquals(5.0, dist2, 0.001);

        // 3. テレポート移動（1000mジャンプ）-> 距離 0.0 (除外)
        double dist3 = tracker.updatePositionAndGetDistance(playerUuid, 1005.0, 0.0, 0.0, "minecraft:overworld", startTime + 3000L);
        assertEquals(0.0, dist3);

        // 4. 異ディメンション移動（Overworld -> Nether）-> 距離 0.0 (除外)
        double dist4 = tracker.updatePositionAndGetDistance(playerUuid, 10.0, 64.0, 10.0, "minecraft:the_nether", startTime + 4000L);
        assertEquals(0.0, dist4);

        // 5. Nether内での通常移動（10m）-> 距離 10.0m
        double dist5 = tracker.updatePositionAndGetDistance(playerUuid, 20.0, 64.0, 10.0, "minecraft:the_nether", startTime + 5000L);
        assertEquals(10.0, dist5, 0.001);
    }

    @Test
    public void testLogoutRemovesState() {
        tracker.onPlayerLogin(playerUuid, 0.0, 0.0, 0.0, "minecraft:overworld", 100000L);
        tracker.onPlayerLogout(playerUuid);

        assertEquals(0L, tracker.getLastActiveTimeMs(playerUuid));
        assertTrue(tracker.isAfk(playerUuid, 200000L));
    }
}
