package com.ruskserver.moveearth_addtional.analytics;

import com.ruskserver.moveearth_addtional.analytics.activity.ActivityCategory;
import com.ruskserver.moveearth_addtional.analytics.activity.PlayerActivityTracker;
import com.ruskserver.moveearth_addtional.analytics.model.SessionRecord;
import com.ruskserver.moveearth_addtional.analytics.tracker.SessionTracker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class SessionTrackerTest {

    private SessionTracker sessionTracker;
    private PlayerActivityTracker activityTracker;
    private UUID playerUuid;

    @BeforeEach
    public void setUp() {
        sessionTracker = new SessionTracker();
        activityTracker = new PlayerActivityTracker();
        playerUuid = UUID.randomUUID();
    }

    @Test
    public void testSessionLoginAndLogoutAccounting() {
        long loginTime = 1000000L;
        activityTracker.onPlayerLogin(playerUuid, 0.0, 0.0, 0.0, loginTime);
        sessionTracker.onLogin(playerUuid, "TestPlayer", loginTime);

        // 2分後: アクション実行（アクティブ維持）
        long time2m = loginTime + 120_000L;
        activityTracker.recordActivity(playerUuid, ActivityCategory.MINING, time2m);
        sessionTracker.updateAllAccounting(activityTracker, time2m);

        // 6分後 (time2mから4分後): まだAFKではない
        long time6m = loginTime + 360_000L;
        sessionTracker.updateAllAccounting(activityTracker, time6m);

        // 10分後 (time2mから8分後): 5分以上無活動のためAFK
        long time10m = loginTime + 600_000L;
        sessionTracker.updateAllAccounting(activityTracker, time10m);

        // ログアウト
        SessionRecord record = sessionTracker.onLogout(playerUuid, activityTracker, time10m);
        assertNotNull(record);
        assertEquals(playerUuid, record.playerUuid());
        assertEquals("TestPlayer", record.lastKnownName());
        assertEquals(loginTime / 1000L, record.joinedAtEpochSec());
        assertEquals(time10m / 1000L, record.leftAtEpochSec());
        assertEquals(600, record.onlineSeconds()); // 10分 = 600秒

        // アクティブ時間とAFK時間の合計が総オンライン時間とほぼ一致することを確認
        assertEquals(600, record.activeSeconds() + record.afkSeconds());
        assertTrue(record.activeSeconds() > 0);
        assertTrue(record.afkSeconds() > 0);
    }
}
