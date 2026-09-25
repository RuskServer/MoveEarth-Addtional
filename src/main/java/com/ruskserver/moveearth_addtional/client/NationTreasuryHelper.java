package com.ruskserver.moveearth_addtional.client;

import java.text.NumberFormat;
import java.util.Locale;

/**
 * Pure logic and formatting helper for NationTreasuryScreen.
 */
public final class NationTreasuryHelper {
    private static final NumberFormat FORMATTER = NumberFormat.getIntegerInstance(Locale.ROOT);

    private NationTreasuryHelper() {}

    public static String formatCurrency(long amount) {
        return FORMATTER.format(amount) + " TC";
    }

    public static String formatNumber(long number) {
        return FORMATTER.format(number);
    }

    /** Fully payable upkeep cycles converted to hours, saturating instead of overflowing. */
    public static long calculateCoverageHours(long nationBalance, long upkeepPerCycle, int cycleHours) {
        if (upkeepPerCycle <= 0L || nationBalance <= 0L || cycleHours <= 0) return 0L;
        long cycles = nationBalance / upkeepPerCycle;
        return cycles > Long.MAX_VALUE / cycleHours ? Long.MAX_VALUE : cycles * cycleHours;
    }

    /**
     * Parses numeric text into a valid positive long amount.
     * Returns 0 if empty or invalid. If text overflows Long.MAX_VALUE, returns Long.MAX_VALUE.
     */
    public static long parseAmount(String text) {
        if (text == null || text.isBlank()) return 0L;
        String digits = text.replaceAll("[^0-9]", "");
        if (digits.isBlank()) return 0L;
        try {
            return Math.max(0L, Long.parseLong(digits));
        } catch (NumberFormatException ignored) {
            return Long.MAX_VALUE;
        }
    }

    /**
     * Adds an increment to the current amount, clamped between 0 and maxCap.
     */
    public static long applyIncrement(long current, long delta, long maxCap) {
        long result = current + delta;
        if (delta > 0 && result < current) {
            result = maxCap;
        }
        return Math.clamp(result, 0L, Math.max(0L, maxCap));
    }

    public record ParsedTransaction(boolean incoming, long amount, String reason) {
        public static ParsedTransaction fromRaw(String raw) {
            if (raw == null || raw.isBlank()) {
                return new ParsedTransaction(true, 0L, "");
            }
            boolean incoming = !raw.startsWith("-");
            String cleaned = raw.startsWith("+") || raw.startsWith("-") ? raw.substring(1).trim() : raw.trim();
            String[] parts = cleaned.split("\\s+", 2);
            long amount = 0L;
            try {
                amount = Long.parseLong(parts[0]);
            } catch (NumberFormatException ignored) {}
            String reason = parts.length > 1 ? parts[1].trim() : "";
            return new ParsedTransaction(incoming, amount, reason);
        }
    }
}
