package com.ruskserver.moveearth_addtional.client.menu;

/**
 * Where the title background is looking, eased towards the cursor.
 *
 * <p>Kept apart from the drawing and free of Minecraft so the easing can be
 * tested. Two things about it are easy to get wrong and both are visible: the
 * offset has to be clamped, or a cursor at the screen edge pans past the image
 * and shows its border, and it has to be eased over wall-clock time rather than
 * per frame, or the background drifts at a speed that depends on the frame rate.
 */
public final class ParallaxTracker {
    /** Time to close roughly two thirds of the remaining distance. */
    private static final double EASE_MILLIS = 220.0D;
    /** A frame longer than this is a stall, not motion, and should not lurch. */
    private static final long MAX_STEP_MILLIS = 200L;

    private float x;
    private float y;
    private float targetX;
    private float targetY;
    private long lastMillis;
    // A separate flag rather than a zero sentinel: zero is a legitimate
    // timestamp, and treating it as "no previous frame" silently drops a step
    // every time one arrives.
    private boolean started;

    /** Aim at a cursor. Centre is zero, each edge is one. */
    public void aim(double mouseX, double mouseY, int width, int height) {
        targetX = normalise(mouseX, width);
        targetY = normalise(mouseY, height);
    }

    /** Aim back at the centre, for when motion is not wanted. */
    public void recentre() {
        targetX = 0.0F;
        targetY = 0.0F;
    }

    /**
     * Ease towards the target as of now.
     *
     * <p>Exponential, which composes exactly: easing once over a frame and
     * easing twice over its halves land in the same place, so the motion is the
     * same at thirty frames a second as at two hundred.
     */
    public void advance(long nowMillis) {
        long elapsed = started ? Math.min(nowMillis - lastMillis, MAX_STEP_MILLIS) : 0L;
        lastMillis = nowMillis;
        started = true;
        if (elapsed <= 0L) {
            return;
        }
        float factor = (float) (1.0D - Math.exp(-elapsed / EASE_MILLIS));
        x += (targetX - x) * factor;
        y += (targetY - y) * factor;
    }

    /** Current horizontal offset, minus one to one. */
    public float x() {
        return x;
    }

    /** Current vertical offset, minus one to one. */
    public float y() {
        return y;
    }

    /**
     * The slice of {@code [low, high]} to show, panned by {@code offset}.
     *
     * <p>{@code fraction} of the span is held back for the pan to spend, so the
     * slice is that much narrower than what it was given and never leaves it --
     * which is the whole point, because a slice that runs past the edge of a
     * texture shows whatever is beyond it. The width does not change with the
     * offset either, or the picture would breathe as the cursor moved.
     *
     * @return the new low and high, in that order
     */
    public static float[] pan(float low, float high, float fraction, float offset) {
        float margin = (high - low) * fraction;
        float shift = Math.max(-1.0F, Math.min(1.0F, offset));
        return new float[]{low + margin * (1.0F + shift), high - margin * (1.0F - shift)};
    }

    private static float normalise(double position, int size) {
        if (size <= 0) {
            return 0.0F;
        }
        double half = size / 2.0D;
        double value = (position - half) / half;
        return (float) Math.max(-1.0D, Math.min(1.0D, value));
    }
}
