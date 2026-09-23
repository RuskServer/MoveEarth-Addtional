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

    /**
     * The fault this replaced: every screen drew its own card around an unbordered
     * EditBox, and an unbordered EditBox draws its text at plain {@code getY()} —
     * vanilla's vertical centring lives in the border, not in the field. In a
     * 24-pixel box the text therefore sat at the top, five pixels above where it
     * looked like it should be.
     */
    @Test
    void textSitsInTheMiddleOfItsBox() {
        assertEquals(8, MoveEarthUiMath.textTop(24, 8));
        assertEquals(6, MoveEarthUiMath.textTop(20, 8));
        assertEquals(5, MoveEarthUiMath.textTop(18, 8));
    }

    @Test
    void anOddGapLeavesTheExtraPixelBelowTheText() {
        // Vanilla rounds the same way, so a field here lines up with a
        // vanilla-bordered one of the same height sitting next to it.
        assertEquals(5, MoveEarthUiMath.textTop(19, 8));
        assertEquals(5, MoveEarthUiMath.textTop(18, 8));
    }

    @Test
    void aBoxTooShortForItsTextStillStartsInside() {
        assertEquals(0, MoveEarthUiMath.textTop(8, 8));
        assertEquals(0, MoveEarthUiMath.textTop(4, 8));
        assertEquals(0, MoveEarthUiMath.textTop(0, 8));
    }

    @Test
    void paddingIsTakenOffBothSidesAndNeverGoesNegative() {
        assertEquals(14, MoveEarthUiMath.textWidth(24, 5));
        assertEquals(0, MoveEarthUiMath.textWidth(10, 5));
        assertEquals(0, MoveEarthUiMath.textWidth(4, 5));
    }
}
