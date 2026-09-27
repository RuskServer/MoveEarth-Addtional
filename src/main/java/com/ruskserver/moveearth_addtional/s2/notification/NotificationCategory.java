package com.ruskserver.moveearth_addtional.s2.notification;

/** User-facing groups used by both the in-game notification center and Discord. */
public enum NotificationCategory {
    DEFENSE,
    TERRITORY,
    CITIZENS,
    RECOVERY,
    DISPATCH;

    public static NotificationCategory of(NationNotificationSavedData.EventType type) {
        if (type == null) return DEFENSE;
        return switch (type) {
            case TERRITORY_EXPOSED, TERRITORY_RESEALED, UPKEEP_WARNING -> TERRITORY;
            case JOIN_APPLICATION -> CITIZENS;
            case RECOVERY_STARTED, RECOVERY_OBJECTIVE, RECOVERY_COMPLETED, RECOVERY_EXPIRED,
                    RIVAL_UPDATED -> RECOVERY;
            case DISPATCH_CREATED, DISPATCH_ACTIVATED, DISPATCH_COMPLETED,
                    DISPATCH_CANCELLED -> DISPATCH;
            default -> DEFENSE;
        };
    }
}
