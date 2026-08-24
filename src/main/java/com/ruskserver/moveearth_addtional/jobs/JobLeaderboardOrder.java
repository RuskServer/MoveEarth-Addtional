package com.ruskserver.moveearth_addtional.jobs;

import java.util.UUID;

/** Stable ranking order shared by saved-data queries and unit tests. */
public final class JobLeaderboardOrder {
    private JobLeaderboardOrder() {
    }

    public static int compare(int leftLevel, double leftXp, double leftTotal, String leftName, UUID leftId,
                              int rightLevel, double rightXp, double rightTotal, String rightName, UUID rightId) {
        int result = Integer.compare(rightLevel, leftLevel);
        if (result != 0) return result;
        result = Double.compare(rightXp, leftXp);
        if (result != 0) return result;
        result = Double.compare(rightTotal, leftTotal);
        if (result != 0) return result;
        result = String.CASE_INSENSITIVE_ORDER.compare(leftName, rightName);
        return result != 0 ? result : leftId.compareTo(rightId);
    }
}
