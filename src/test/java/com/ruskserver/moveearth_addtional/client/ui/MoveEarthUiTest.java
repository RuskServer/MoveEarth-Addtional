package com.ruskserver.moveearth_addtional.client.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MoveEarthUiTest {
    @Test
    void rectangleUsesHalfOpenHitBounds() {
        MoveEarthUi.Rect rect = new MoveEarthUi.Rect(10, 20, 30, 40);

        assertTrue(rect.contains(10, 20));
        assertTrue(rect.contains(39.99, 59.99));
        assertFalse(rect.contains(40, 20));
        assertFalse(rect.contains(10, 60));
        assertFalse(rect.contains(9.99, 20));
    }

    @Test
    void rectangleRejectsNegativeDimensions() {
        assertThrows(IllegalArgumentException.class, () -> new MoveEarthUi.Rect(0, 0, -1, 10));
        assertThrows(IllegalArgumentException.class, () -> new MoveEarthUi.Rect(0, 0, 10, -1));
    }

    @Test
    void scrollClampsToContentBounds() {
        assertEquals(0, MoveEarthUiMath.scroll(0, 1, 24, 300, 100));
        assertEquals(24, MoveEarthUiMath.scroll(0, -1, 24, 300, 100));
        assertEquals(200, MoveEarthUiMath.scroll(190, -2, 24, 300, 100));
        assertEquals(0, MoveEarthUiMath.scroll(20, 1, 24, 80, 100));
    }
}
