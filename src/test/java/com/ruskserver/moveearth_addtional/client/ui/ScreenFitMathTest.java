package com.ruskserver.moveearth_addtional.client.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScreenFitMathTest {
    // The market panel, 610 by 348, with the margins HubTabs.fit asks for.
    private static final int NEED_WIDTH = 618;
    private static final int NEED_HEIGHT = 360;

    @Test
    void aScreenThatFitsKeepsThePlayersScale() {
        assertEquals(3.0D, ScreenFitMath.scale(1920, 1080, 3, NEED_WIDTH, NEED_HEIGHT));
        assertEquals(2.0D, ScreenFitMath.scale(1920, 1080, 2, NEED_WIDTH, NEED_HEIGHT));
    }

    @Test
    void autoScaleOnA1080pScreenStepsDownOneWholeStep() {
        // Auto picks 4 at 1080p: 480 by 270, too small for the market; 3 fits exactly.
        assertEquals(3.0D, ScreenFitMath.scale(1920, 1080, 4, NEED_WIDTH, NEED_HEIGHT));
        assertEquals(4.0D, ScreenFitMath.scale(2560, 1440, 6, NEED_WIDTH, NEED_HEIGHT));
    }

    @Test
    void scaleTwoInASmallWindowShrinksALittleInsteadOfHalving() {
        // 1200 by 700 at scale 2 is 600 by 350: just short. Scale 1 would halve everything.
        double scale = ScreenFitMath.scale(1200, 700, 2, NEED_WIDTH, NEED_HEIGHT);
        assertTrue(scale > 1.9D && scale < 2.0D, "fractional, close to 2: " + scale);
        assertTrue(Math.ceil(1200 / scale) >= NEED_WIDTH && Math.ceil(700 / scale) >= NEED_HEIGHT, "and it fits");
    }

    @Test
    void aTinyWindowStopsAtScaleOne() {
        assertEquals(1.0D, ScreenFitMath.scale(500, 300, 2, NEED_WIDTH, NEED_HEIGHT));
    }
}
