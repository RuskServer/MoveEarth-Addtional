package com.ruskserver.moveearth_addtional.jobs;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class JobIncomePolicyTest {
    @Test void smallActionsAccumulateUntilPayment() {
        var first = JobIncomePolicy.award(12.0D, 0.0D, 0, 0);
        assertEquals(0, first.currency());
        var second = JobIncomePolicy.award(13.0D, first.carriedXp(), 0, 0);
        assertEquals(1, second.currency());
        assertEquals(0.0D, second.carriedXp());
    }

    @Test void sharedHourlyAndDailyCapsDiscardOverflow() {
        var hourly = JobIncomePolicy.award(100.0D, 0.0D, 19, 20);
        assertEquals(1, hourly.currency());
        assertEquals(0.0D, hourly.carriedXp());
        var daily = JobIncomePolicy.award(500.0D, 0.0D, 0, 80);
        assertEquals(0, daily.currency());
        assertEquals(0.0D, daily.carriedXp());
    }

    @Test void invalidXpCannotMint() {
        assertEquals(0, JobIncomePolicy.award(Double.POSITIVE_INFINITY, 0, 0, 0).currency());
        assertEquals(0, JobIncomePolicy.award(-25, 0, 0, 0).currency());
    }
}
