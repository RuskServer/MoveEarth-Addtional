package com.ruskserver.moveearth_addtional.client.menu;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ParallaxTrackerTest {

    private static ParallaxTracker settled(double mouseX, double mouseY, int w, int h) {
        ParallaxTracker tracker = new ParallaxTracker();
        tracker.aim(mouseX, mouseY, w, h);
        tracker.advance(0L);
        for (int step = 1; step <= 200; step++) {
            tracker.advance(step * 16L);
        }
        return tracker;
    }

    @Test
    void aCentredCursorLooksStraightAhead() {
        ParallaxTracker tracker = settled(400, 300, 800, 600);
        assertEquals(0.0F, tracker.x(), 1e-3);
        assertEquals(0.0F, tracker.y(), 1e-3);
    }

    @Test
    void theEdgesAreOneAndTheCornerDoesNotGoFurther() {
        assertEquals(1.0F, settled(800, 300, 800, 600).x(), 1e-3);
        assertEquals(-1.0F, settled(0, 300, 800, 600).x(), 1e-3);
        // a cursor dragged outside the window must not pan past the image
        ParallaxTracker beyond = settled(4000, -900, 800, 600);
        assertEquals(1.0F, beyond.x(), 1e-3);
        assertEquals(-1.0F, beyond.y(), 1e-3);
    }

    @Test
    void theFirstFrameDoesNotLurch() {
        ParallaxTracker tracker = new ParallaxTracker();
        tracker.aim(800, 600, 800, 600);
        tracker.advance(1_700_000_000_000L);
        assertEquals(0.0F, tracker.x(), 1e-6, "an arbitrary first timestamp is not an elapsed time");
    }

    @Test
    void easingIsTheSameAtAnyFrameRate() {
        ParallaxTracker slow = new ParallaxTracker();
        ParallaxTracker fast = new ParallaxTracker();
        slow.aim(800, 300, 800, 600);
        fast.aim(800, 300, 800, 600);
        slow.advance(0L);
        fast.advance(0L);
        for (int step = 1; step <= 6; step++) {
            slow.advance(step * 32L);
            fast.advance(step * 32L - 16L);
            fast.advance(step * 32L);
        }
        assertEquals(slow.x(), fast.x(), 1e-4,
                "the same wall-clock time must give the same offset");
    }

    @Test
    void itApproachesWithoutOvershooting() {
        ParallaxTracker tracker = new ParallaxTracker();
        tracker.aim(800, 300, 800, 600);
        tracker.advance(0L);
        float previous = tracker.x();
        for (int step = 1; step <= 40; step++) {
            tracker.advance(step * 16L);
            assertTrue(tracker.x() >= previous, "went backwards at step " + step);
            assertTrue(tracker.x() <= 1.0F, "overshot at step " + step);
            previous = tracker.x();
        }
        assertTrue(previous > 0.9F, "never got there: " + previous);
    }

    @Test
    void aStallDoesNotJumpTheWholeWay() {
        ParallaxTracker tracker = new ParallaxTracker();
        tracker.aim(800, 300, 800, 600);
        tracker.advance(0L);
        tracker.advance(30_000L);
        assertTrue(tracker.x() < 0.75F, "a long pause should not teleport the view: " + tracker.x());
    }

    @Test
    void recentreEasesBack() {
        ParallaxTracker tracker = settled(800, 600, 800, 600);
        assertTrue(tracker.x() > 0.9F);
        tracker.recentre();
        for (int step = 201; step <= 400; step++) {
            tracker.advance(step * 16L);
        }
        assertEquals(0.0F, tracker.x(), 1e-3);
        assertEquals(0.0F, tracker.y(), 1e-3);
    }

    @Test
    void panKeepsTheSliceInsideWhatItWasGiven() {
        for (float offset = -1.0F; offset <= 1.0F; offset += 0.1F) {
            float[] slice = ParallaxTracker.pan(0.2F, 0.8F, 0.05F, offset);
            assertTrue(slice[0] >= 0.2F - 1e-6, "ran off the low edge at " + offset);
            assertTrue(slice[1] <= 0.8F + 1e-6, "ran off the high edge at " + offset);
        }
    }

    @Test
    void panKeepsTheSliceTheSameWidth() {
        float centred = width(ParallaxTracker.pan(0.0F, 1.0F, 0.03F, 0.0F));
        assertEquals(centred, width(ParallaxTracker.pan(0.0F, 1.0F, 0.03F, 1.0F)), 1e-6);
        assertEquals(centred, width(ParallaxTracker.pan(0.0F, 1.0F, 0.03F, -1.0F)), 1e-6);
        // and that width is the span less the margin it holds back on each side
        assertEquals(1.0F - 2.0F * 0.03F, centred, 1e-6);
    }

    @Test
    void panMovesTheSliceTowardsTheOffset() {
        float[] left = ParallaxTracker.pan(0.0F, 1.0F, 0.03F, -1.0F);
        float[] right = ParallaxTracker.pan(0.0F, 1.0F, 0.03F, 1.0F);
        assertTrue(right[0] > left[0], "a positive offset should show further along");
        assertEquals(0.0F, left[0], 1e-6, "fully left should start at the edge it was given");
        assertEquals(1.0F, right[1], 1e-6, "fully right should end at the edge it was given");
    }

    @Test
    void panClampsAnOffsetBeyondTheEnds() {
        float[] far = ParallaxTracker.pan(0.0F, 1.0F, 0.03F, 4.0F);
        float[] end = ParallaxTracker.pan(0.0F, 1.0F, 0.03F, 1.0F);
        assertEquals(end[0], far[0], 1e-6);
        assertEquals(end[1], far[1], 1e-6);
    }

    private static float width(float[] slice) {
        return slice[1] - slice[0];
    }
}
