package com.ruskserver.moveearth_addtional;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServerScheduleTest {
    @Test
    void opensAtEighteen() {
        assertFalse(ServerSchedule.isOpenHour(17));
        assertTrue(ServerSchedule.isOpenHour(18));
    }

    @Test
    void closesAtMidnight() {
        assertTrue(ServerSchedule.isOpenHour(23));
        assertFalse(ServerSchedule.isOpenHour(0));
    }
}
