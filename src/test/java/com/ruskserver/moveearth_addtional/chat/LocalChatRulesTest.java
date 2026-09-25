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
        assertEquals("自分", LocalChatRules.distanceLabel(0, true));
        assertEquals("1ブロック未満", LocalChatRules.distanceLabel(0.75, false));
        assertEquals("8ブロック先", LocalChatRules.distanceLabel(8.2, false));
        assertEquals("12ブロック先", LocalChatRules.distanceLabel(12.4, false));
        assertEquals("27ブロック先", LocalChatRules.distanceLabel(27.3, false));
        assertEquals("100ブロック先", LocalChatRules.distanceLabel(99.6, false));
    }
}
