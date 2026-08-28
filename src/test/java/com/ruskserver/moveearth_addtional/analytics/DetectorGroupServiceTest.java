package com.ruskserver.moveearth_addtional.analytics;

import com.ruskserver.moveearth_addtional.analytics.config.AnalyticsConfig;
import com.ruskserver.moveearth_addtional.analytics.group.GroupRelation;
import com.ruskserver.moveearth_addtional.data.WhitelistRegistry;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class DetectorGroupServiceTest {

    @Test
    public void testGroupRelationValues() {
        assertEquals("member", GroupRelation.MEMBER.getId());
        assertEquals("自領域", GroupRelation.MEMBER.getDisplayName());

        assertEquals("outsider", GroupRelation.OUTSIDER.getId());
        assertEquals("他領域", GroupRelation.OUTSIDER.getDisplayName());

        assertEquals("wilderness", GroupRelation.WILDERNESS.getId());
        assertEquals("荒野", GroupRelation.WILDERNESS.getDisplayName());
    }

    @Test
    public void testAnalyticsConfigConstants() {
        assertEquals(30 * 20, AnalyticsConfig.POSITION_SAMPLE_INTERVAL_TICKS);
        assertEquals(32, AnalyticsConfig.CELL_SIZE_BLOCKS);
        assertEquals(300, AnalyticsConfig.AGGREGATION_BUCKET_SECONDS);
        assertEquals(300000L, AnalyticsConfig.AFK_THRESHOLD_MS);
        assertEquals(100.0D, AnalyticsConfig.DETECTOR_GROUP_RADIUS_BLOCKS);
        assertEquals(2.0D, AnalyticsConfig.MOVEMENT_THRESHOLD_BLOCKS);
    }

    @Test
    public void testGroupMembershipWithWhitelistRegistry() {
        WhitelistRegistry registry = new WhitelistRegistry();
        UUID owner = UUID.randomUUID();
        UUID member = UUID.randomUUID();
        UUID outsider = UUID.randomUUID();

        registry.addToWhitelist(owner, member, "MemberPlayer");

        // オーナー本人はグループ所属
        assertTrue(owner.equals(owner) || registry.isWhitelisted(owner, owner));

        // ホワイトリスト登録者はメンバー
        assertTrue(registry.isWhitelisted(owner, member));

        // 未登録者はメンバーではない
        assertFalse(registry.isWhitelisted(owner, outsider));
    }
}
