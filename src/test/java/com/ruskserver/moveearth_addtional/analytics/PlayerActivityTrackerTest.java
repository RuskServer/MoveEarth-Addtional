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

        tracker.onPlayerLogin(playerUuid, 100.0, 64.0, 100.0, startTime);

        assertEquals(startTime, tracker.getLastActiveTimeMs(playerUuid));
        assertFalse(tracker.isAfk(playerUuid, startTime));
        assertFalse(tracker.isAfk(playerUuid, startTime + AnalyticsConfig.AFK_THRESHOLD_MS - 1000));
        assertTrue(tracker.isAfk(playerUuid, startTime + AnalyticsConfig.AFK_THRESHOLD_MS));
    }

    @Test
    public void testRecordActivityExtendsActiveTime() {
        long startTime = 100000L;
        tracker.onPlayerLogin(playerUuid, 0.0, 0.0, 0.0, startTime);

        long activityTime = startTime + 4 * 60 * 1000L; // 4分後
        tracker.recordActivity(playerUuid, ActivityCategory.MINING, activityTime);

        assertEquals(activityTime, tracker.getLastActiveTimeMs(playerUuid));
        // 最初の開始時から6分後（activityTimeからは2分後）なのでまだAFKではない
        assertFalse(tracker.isAfk(playerUuid, startTime + 6 * 60 * 1000L));
        // activityTimeから5分後はAFK
        assertTrue(tracker.isAfk(playerUuid, activityTime + AnalyticsConfig.AFK_THRESHOLD_MS));
    }

    @Test
    public void testMovementThreshold() {
        long startTime = 100000L;
        tracker.onPlayerLogin(playerUuid, 0.0, 0.0, 0.0, startTime);

        long checkTime1 = startTime + 1000L;
        // 1ブロックの移動（閾値2ブロック未満）-> 活動時刻は更新されない
        boolean movedSmall = tracker.updatePosition(playerUuid, 1.0, 0.0, 0.0, checkTime1);
        assertFalse(movedSmall);
        assertEquals(startTime, tracker.getLastActiveTimeMs(playerUuid));

        long checkTime2 = startTime + 2000L;
        // さらに2.5ブロックの移動（閾値2ブロック以上）-> 活動時刻が更新される
        boolean movedLarge = tracker.updatePosition(playerUuid, 3.5, 0.0, 0.0, checkTime2);
        assertTrue(movedLarge);
        assertEquals(checkTime2, tracker.getLastActiveTimeMs(playerUuid));
    }

    @Test
    public void testLogoutRemovesState() {
        tracker.onPlayerLogin(playerUuid, 0.0, 0.0, 0.0, 100000L);
        tracker.onPlayerLogout(playerUuid);

        assertEquals(0L, tracker.getLastActiveTimeMs(playerUuid));
        assertTrue(tracker.isAfk(playerUuid, 200000L));
    }
}
