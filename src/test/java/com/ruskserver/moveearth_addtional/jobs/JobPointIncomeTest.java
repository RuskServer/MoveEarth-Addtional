package com.ruskserver.moveearth_addtional.jobs;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class JobPointIncomeTest {
    @Test
    void convertsEffectiveXpIntoRecurringPoints() {
        JobPointIncome.Result first = JobPointIncome.apply(-1, 0, 0, 499, 100);
        assertEquals(0, first.pointsEarned());
        assertEquals(499, first.xpTowardsNextPoint());

        JobPointIncome.Result second = JobPointIncome.apply(first.startedAt(),
                first.xpTowardsNextPoint(), first.pointsInWindow(), 1, 120);
        assertEquals(1, second.pointsEarned());
        assertEquals(0, second.xpTowardsNextPoint());
        assertEquals(1, second.pointsInWindow());
    }

    @Test
    void capsTheSharedHourlyIncomeAndDropsOverflow() {
        JobPointIncome.Result result = JobPointIncome.apply(-1, 0, 0, 2_499, 500);
        assertEquals(4, result.pointsEarned());
        assertEquals(4, result.pointsInWindow());
        assertEquals(0, result.xpTowardsNextPoint());

        JobPointIncome.Result capped = JobPointIncome.apply(result.startedAt(), 0,
                result.pointsInWindow(), 10_000, 600);
        assertEquals(0, capped.pointsEarned());
        assertEquals(0, capped.xpTowardsNextPoint());
    }

    @Test
    void startsAFreshWindowAfterOneHour() {
        JobPointIncome.Result result = JobPointIncome.apply(100, 250, 3, 500,
                100 + JobPointIncome.WINDOW_TICKS);
        assertEquals(1, result.pointsEarned());
        assertEquals(1, result.pointsInWindow());
        assertEquals(0, result.xpTowardsNextPoint());
        assertEquals(100 + JobPointIncome.WINDOW_TICKS, result.startedAt());
        assertEquals(JobPointIncome.WINDOW_TICKS,
                JobPointIncome.ticksRemaining(result.startedAt(), result.startedAt()));
    }
}
