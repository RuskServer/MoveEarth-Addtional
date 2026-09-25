package com.ruskserver.moveearth_addtional.economy;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MarketOrderRulesTest {
    @Test void priceAndQuantityAreBounded() {
        assertEquals(120L, MarketOrderRules.totalPrice(12, 10));
        assertEquals(-1L, MarketOrderRules.totalPrice(4097, 10));
        assertEquals(-1L, MarketOrderRules.totalPrice(1, 1_000_001L));
        assertEquals(-1L, MarketOrderRules.totalPrice(0, 10));
        assertEquals(-1L, MarketOrderRules.totalPrice(1, -1));
        assertEquals(4_096_000_000L, MarketOrderRules.totalPrice(4096, 1_000_000));
    }

    @Test void partialFillRequiresStockAndReceivingCapacity() {
        assertTrue(MarketOrderRules.canFill(4, 10, 4, 4));
        assertFalse(MarketOrderRules.canFill(5, 10, 4, 5));
        assertFalse(MarketOrderRules.canFill(5, 10, 5, 4));
        assertFalse(MarketOrderRules.canFill(11, 10, 11, 11));
        assertFalse(MarketOrderRules.canFill(0, 10, 10, 10));
    }

    @Test void physicalHandOffHasAReachLimit() {
        assertTrue(MarketOrderRules.inReach(64));
        assertFalse(MarketOrderRules.inReach(64.01));
        assertFalse(MarketOrderRules.inReach(Double.NaN));
    }
}
