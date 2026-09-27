package com.ruskserver.moveearth_addtional.s2.notification;

import java.util.EnumMap;
import java.util.Map;

public enum NotificationPreset {
    IMPORTANT,
    STANDARD,
    ALL_IMMEDIATE,
    CUSTOM;

    public Map<NotificationCategory, NotificationPreference> preferences(boolean discordAvailable) {
        EnumMap<NotificationCategory, NotificationPreference> result = new EnumMap<>(NotificationCategory.class);
        for (NotificationCategory category : NotificationCategory.values()) {
            NotificationPreference.DiscordMode discord = switch (this) {
                case IMPORTANT -> category == NotificationCategory.DEFENSE
                        || category == NotificationCategory.TERRITORY
                        ? NotificationPreference.DiscordMode.IMMEDIATE : NotificationPreference.DiscordMode.OFF;
                case STANDARD -> category == NotificationCategory.DEFENSE
                        || category == NotificationCategory.TERRITORY
                        ? NotificationPreference.DiscordMode.IMMEDIATE : NotificationPreference.DiscordMode.DIGEST;
                case ALL_IMMEDIATE -> NotificationPreference.DiscordMode.IMMEDIATE;
                case CUSTOM -> NotificationPreference.DiscordMode.OFF;
            };
            if (!discordAvailable) discord = NotificationPreference.DiscordMode.OFF;
            NotificationPreference.MentionPolicy mention = category == NotificationCategory.DEFENSE
                    && this != IMPORTANT && this != CUSTOM
                    ? NotificationPreference.MentionPolicy.URGENT_ONLY
                    : NotificationPreference.MentionPolicy.NONE;
            result.put(category, new NotificationPreference(
                    NotificationPreference.InGameMode.IMMEDIATE, discord, mention));
        }
        return result;
    }

    public static NotificationPreset detect(Map<NotificationCategory, NotificationPreference> preferences,
                                            boolean discordAvailable) {
        for (NotificationPreset preset : new NotificationPreset[]{IMPORTANT, STANDARD, ALL_IMMEDIATE}) {
            if (preset.preferences(discordAvailable).equals(preferences)) return preset;
        }
        return CUSTOM;
    }
}
