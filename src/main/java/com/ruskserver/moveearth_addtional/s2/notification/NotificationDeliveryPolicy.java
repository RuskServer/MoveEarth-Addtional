package com.ruskserver.moveearth_addtional.s2.notification;

/** Minecraft-independent limits for the persistent external notification outbox. */
public final class NotificationDeliveryPolicy {
    public static final int MAX_OUTBOX = 4_096;
    public static final int MAX_ATTEMPTS = 8;

    private NotificationDeliveryPolicy() { }

    public static boolean canQueue(boolean linked, boolean discordEnabled, int currentSize) {
        return linked && discordEnabled && currentSize >= 0 && currentSize < MAX_OUTBOX;
    }

    public static boolean shouldDrop(int attempts) {
        return attempts >= MAX_ATTEMPTS;
    }

    public static long retryDelayMillis(int attempts) {
        int exponent = Math.max(0, Math.min(10, attempts - 1));
        return Math.min(15 * 60_000L, 5_000L << exponent);
    }

    public static boolean expired(long createdAtMillis, long nowMillis, long retentionMillis) {
        return retentionMillis > 0L && nowMillis >= createdAtMillis
                && nowMillis - createdAtMillis >= retentionMillis;
    }

    public static boolean duplicate(long previousMillis, long nowMillis, long windowMillis) {
        return windowMillis > 0L && previousMillis >= 0L && nowMillis >= previousMillis
                && nowMillis - previousMillis < windowMillis;
    }
}
