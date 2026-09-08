package com.ruskserver.moveearth_addtional.analytics.query.dto;

/**
 * 集計対象の窓期間（時間枠）
 */
public enum TimeWindow {
    DAYS_7("7days", 7 * 86400L),
    DAYS_30("30days", 30 * 86400L),
    ALL_TIME("all_time", Long.MAX_VALUE);

    private final String id;
    private final long seconds;

    TimeWindow(String id, long seconds) {
        this.id = id;
        this.seconds = seconds;
    }

    public String getId() {
        return id;
    }

    public long getSeconds() {
        return seconds;
    }

    /**
     * 現在時刻（エポック秒）から起算した窓期間の開始エポック秒を計算
     */
    public long getStartEpochSec(long currentEpochSec) {
        if (this == ALL_TIME) {
            return 0L;
        }
        return Math.max(0L, currentEpochSec - seconds);
    }
}
