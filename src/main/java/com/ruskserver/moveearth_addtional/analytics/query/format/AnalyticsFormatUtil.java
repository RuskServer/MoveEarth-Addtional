package com.ruskserver.moveearth_addtional.analytics.query.format;

import java.text.DecimalFormat;

/**
 * プレイヤー分析用テキスト・数値・時間の純粋Javaフォーマットユーティリティ
 */
public final class AnalyticsFormatUtil {

    private static final DecimalFormat NUM_FMT = new DecimalFormat("#,###");
    private static final DecimalFormat DEC_FMT = new DecimalFormat("#,##0.0");

    private AnalyticsFormatUtil() {
    }

    public static String formatDuration(long totalSeconds) {
        if (totalSeconds < 60) {
            return totalSeconds + "秒";
        }
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        if (hours > 0) {
            return hours + "h " + minutes + "m";
        }
        return minutes + "m";
    }

    public static String formatDistance(double blocks) {
        if (blocks >= 1000.0) {
            return DEC_FMT.format(blocks / 1000.0) + "km";
        }
        return NUM_FMT.format(Math.round(blocks)) + "m";
    }

    public static String formatPercent(long part, long total) {
        if (total <= 0) return "0%";
        double pct = (double) part / total * 100.0;
        return DEC_FMT.format(pct) + "%";
    }

    public static String formatNumber(long num) {
        return NUM_FMT.format(num);
    }

    public static String formatDecimal(double num) {
        return DEC_FMT.format(num);
    }
}
