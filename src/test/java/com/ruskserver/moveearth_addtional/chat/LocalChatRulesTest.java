package com.ruskserver.moveearth_addtional.chat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalChatRulesTest {
    @Test
    void onlySameDimensionPlayersWithinInclusiveRadiusReceiveChat() {
        assertTrue(LocalChatRules.canReceive(true, 0, 100));
        assertTrue(LocalChatRules.canReceive(true, 10000, 100));
        assertFalse(LocalChatRules.canReceive(true, 10000.1, 100));
        assertFalse(LocalChatRules.canReceive(false, 0, 100));
        assertFalse(LocalChatRules.canReceive(true, 0, 0));
    }

    @Test
    void distancesAreDisplayedToTheNearestBlock() {
        assertEquals(new LocalChatRules.Distance(LocalChatRules.Distance.Kind.SELF, 0L),
                LocalChatRules.distance(0, true));
        assertEquals(new LocalChatRules.Distance(LocalChatRules.Distance.Kind.UNDER_ONE_BLOCK, 0L),
                LocalChatRules.distance(0.75, false));
        assertEquals(8L, LocalChatRules.distance(8.2, false).blocks());
        assertEquals(12L, LocalChatRules.distance(12.4, false).blocks());
        assertEquals(27L, LocalChatRules.distance(27.3, false).blocks());
        assertEquals(100L, LocalChatRules.distance(99.6, false).blocks());
        assertEquals(LocalChatRules.Distance.Kind.BLOCKS, LocalChatRules.distance(99.6, false).kind());
    }
}
