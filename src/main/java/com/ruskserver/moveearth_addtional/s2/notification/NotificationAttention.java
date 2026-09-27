package com.ruskserver.moveearth_addtional.s2.notification;

/** Discord notification state worth surfacing on the hub home page to members who can fix it. */
public enum NotificationAttention {
    NONE, UNLINKED, PROBLEM;

    public static NotificationAttention of(boolean canManage, boolean botEnabled, boolean linked,
                                           int retrying, long lastSuccessAtMillis, long lastFailureAtMillis) {
        if (!canManage || !botEnabled) return NONE;
        if (!linked) return UNLINKED;
        return retrying > 0 || lastFailureAtMillis > lastSuccessAtMillis ? PROBLEM : NONE;
    }

    public static NotificationAttention at(int index) {
        return index >= 0 && index < values().length ? values()[index] : NONE;
    }
}
