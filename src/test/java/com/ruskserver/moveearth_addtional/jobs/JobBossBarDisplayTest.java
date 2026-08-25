package com.ruskserver.moveearth_addtional.jobs;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class JobBossBarDisplayTest {
    @Test
    void formatsLevelProgressAndFractionalGain() {
        JobBossBarDisplay.Display display = JobBossBarDisplay.create(
                "採掘師", 12, 340.5D, 1_135.0D, 8.8D);

        assertEquals("Lvl 12 採掘師: 340.5/1135 XP (+8.8)", display.title());
        assertEquals(340.5F / 1_135.0F, display.progress(), 0.0001F);
    }

    @Test
    void displaysMaxLevelAsAFullBar() {
        JobBossBarDisplay.Display display = JobBossBarDisplay.create(
                "採掘師", 50, 0.0D, 0.0D, 4.0D);

        assertEquals("Lvl 50 採掘師: MAX XP (+4)", display.title());
        assertEquals(1.0F, display.progress());
    }
}
