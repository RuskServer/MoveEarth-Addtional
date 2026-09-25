package com.ruskserver.moveearth_addtional.event;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EventScheduleRulesTest {
    @Test
    void openTimeIntervalAndPause() {
        long next = EventScheduleRules.nextStart(100);
        assertEquals(216100, next);
        assertFalse(EventScheduleRules.due(next - 1, next, true, true));
        assertFalse(EventScheduleRules.due(next, next, false, true));
        assertFalse(EventScheduleRules.due(next, next, true, false));
        assertTrue(EventScheduleRules.due(next, next, true, true));
    }
}
