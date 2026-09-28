package com.ruskserver.moveearth_addtional.analytics;

import com.ruskserver.moveearth_addtional.analytics.config.AnalyticsConfig;
import com.ruskserver.moveearth_addtional.analytics.group.GroupRelation;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class GroupRelationTest {

    @Test
    public void keepsStoredIds() {
        assertEquals("member", GroupRelation.MEMBER.getId());
        assertEquals("allied", GroupRelation.ALLIED.getId());
        assertEquals("outsider", GroupRelation.OUTSIDER.getId());
        assertEquals("wilderness", GroupRelation.WILDERNESS.getId());
    }

    @Test
    public void relatesPlayerToTerritoryNation() {
        UUID own = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        assertEquals(GroupRelation.WILDERNESS, GroupRelation.of(null, own, false));
        assertEquals(GroupRelation.MEMBER, GroupRelation.of(own, own, false));
        assertEquals(GroupRelation.ALLIED, GroupRelation.of(other, own, true));
        assertEquals(GroupRelation.OUTSIDER, GroupRelation.of(other, own, false));
        // A nationless player has no allies, whatever the flag says.
        assertEquals(GroupRelation.OUTSIDER, GroupRelation.of(other, null, true));
    }

    @Test
    public void analyticsConfigConstants() {
        assertEquals(30 * 20, AnalyticsConfig.POSITION_SAMPLE_INTERVAL_TICKS);
        assertEquals(32, AnalyticsConfig.CELL_SIZE_BLOCKS);
        assertEquals(300, AnalyticsConfig.AGGREGATION_BUCKET_SECONDS);
        assertEquals(300000L, AnalyticsConfig.AFK_THRESHOLD_MS);
        assertEquals(2.0D, AnalyticsConfig.MOVEMENT_THRESHOLD_BLOCKS);
    }
}
