package com.ruskserver.moveearth_addtional.jobs;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JobShopRulesTest {
    @Test
    void validatesPriceAndUnlimitedOrBoundedLimits() {
        assertTrue(JobShopRules.validConfiguration(1, 0));
        assertTrue(JobShopRules.validConfiguration(1_000_000, 1_000_000));
        assertFalse(JobShopRules.validConfiguration(0, 0));
        assertFalse(JobShopRules.validConfiguration(1, -1));
        assertFalse(JobShopRules.validConfiguration(1_000_001, 0));
    }

    @Test
    void purchaseChecksServerOwnedStateInSafeOrder() {
        assertEquals(JobShopRules.PurchaseCheck.DISABLED,
                JobShopRules.checkPurchase(100, 10, 0, 0, false));
        assertEquals(JobShopRules.PurchaseCheck.LIMIT_REACHED,
                JobShopRules.checkPurchase(100, 10, 2, 2, true));
        assertEquals(JobShopRules.PurchaseCheck.NOT_ENOUGH_POINTS,
                JobShopRules.checkPurchase(9, 10, 0, 0, true));
        assertEquals(JobShopRules.PurchaseCheck.ALLOWED,
                JobShopRules.checkPurchase(10, 10, 0, 0, true));
    }

    @Test
    void reportsUnlimitedAndBoundedRemainingPurchases() {
        assertEquals(-1, JobShopRules.remainingPurchases(50, 0));
        assertEquals(3, JobShopRules.remainingPurchases(2, 5));
        assertEquals(0, JobShopRules.remainingPurchases(7, 5));
    }
}
