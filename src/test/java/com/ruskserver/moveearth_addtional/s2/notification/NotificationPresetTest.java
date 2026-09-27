package com.ruskserver.moveearth_addtional.s2.notification;

import org.junit.jupiter.api.Test;

import java.util.EnumMap;

import static org.junit.jupiter.api.Assertions.*;

class NotificationPresetTest {
    @Test
    void standardKeepsUrgentImmediateAndRoutineDigest() {
        var settings = NotificationPreset.STANDARD.preferences(true);
        assertEquals(NotificationPreference.DiscordMode.IMMEDIATE,
                settings.get(NotificationCategory.DEFENSE).discord());
        assertEquals(NotificationPreference.MentionPolicy.URGENT_ONLY,
                settings.get(NotificationCategory.DEFENSE).mention());
        assertEquals(NotificationPreference.DiscordMode.DIGEST,
                settings.get(NotificationCategory.CITIZENS).discord());
        assertEquals(NotificationPreset.STANDARD, NotificationPreset.detect(settings, true));
    }

    @Test
    void playerWithoutNationLinkNeverGetsDiscordDelivery() {
        var settings = NotificationPreset.ALL_IMMEDIATE.preferences(false);
        assertTrue(settings.values().stream().allMatch(value ->
                value.discord() == NotificationPreference.DiscordMode.OFF));
    }

    @Test
    void oneEditedCategoryIsCustom() {
        var settings = new EnumMap<>(NotificationPreset.STANDARD.preferences(true));
        settings.put(NotificationCategory.RECOVERY, new NotificationPreference(
                NotificationPreference.InGameMode.OFF,
                NotificationPreference.DiscordMode.OFF,
                NotificationPreference.MentionPolicy.NONE));
        assertEquals(NotificationPreset.CUSTOM, NotificationPreset.detect(settings, true));
    }
}
