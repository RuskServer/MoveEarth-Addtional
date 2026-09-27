package com.ruskserver.moveearth_addtional.s2.notification;

/** Minecraft-independent incident thresholds used to suppress repetitive combat noise. */
public final class NotificationIncidentPolicy {
    private NotificationIncidentPolicy() { }

    public static int healthBand(int health, int maximum) {
        if (maximum <= 0) return 100;
        int percent = Math.max(0, Math.min(100, health * 100 / maximum));
        if (percent <= 10) return 10;
        if (percent <= 25) return 25;
        if (percent <= 50) return 50;
        if (percent <= 75) return 75;
        return 100;
    }

    public static boolean crossed(Integer previousBand, int nextBand) {
        return nextBand < 100 && (previousBand == null || nextBand < previousBand);
    }
}
