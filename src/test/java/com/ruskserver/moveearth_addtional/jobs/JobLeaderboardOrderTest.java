package com.ruskserver.moveearth_addtional.jobs;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;

class JobLeaderboardOrderTest {
    private static final UUID A = new UUID(0, 1);
    private static final UUID B = new UUID(0, 2);

    @Test
    void ranksLevelThenCurrentXpThenTotalXpDescending() {
        assertTrue(compare(3, 0, 100, "A", A, 2, 999, 999, "B", B) < 0);
        assertTrue(compare(2, 50, 100, "A", A, 2, 40, 999, "B", B) < 0);
        assertTrue(compare(2, 50, 200, "A", A, 2, 50, 100, "B", B) < 0);
    }

    @Test
    void usesNameAndUuidAsStableTieBreakers() {
        assertTrue(compare(2, 50, 100, "Alice", B, 2, 50, 100, "Bob", A) < 0);
        assertTrue(compare(2, 50, 100, "Same", A, 2, 50, 100, "same", B) < 0);
    }

    private static int compare(int leftLevel, double leftXp, double leftTotal, String leftName, UUID leftId,
                               int rightLevel, double rightXp, double rightTotal, String rightName, UUID rightId) {
        return JobLeaderboardOrder.compare(leftLevel, leftXp, leftTotal, leftName, leftId,
                rightLevel, rightXp, rightTotal, rightName, rightId);
    }
}
