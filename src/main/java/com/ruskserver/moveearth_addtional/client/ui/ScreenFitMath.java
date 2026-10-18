package com.ruskserver.moveearth_addtional.client.ui;

/** The GUI scale a screen of a given size needs. Pure, so it is unit tested. */
public final class ScreenFitMath {
    /**
     * A whole-number scale is crisper, so it is used when it shrinks the screen by at most this much more
     * than fitting needs. Below that it would shrink too far, as 2 to 1 halves everything, and the exact
     * fractional scale is used instead.
     */
    static final double WHOLE_STEP_TOLERANCE = 0.8D;
    /** Text smaller than this stops being readable; a window too small for it keeps this and overflows. */
    static final double MIN_SCALE = 1.0D;

    private ScreenFitMath() { }

    /**
     * The scale at which a {@code needWidth} by {@code needHeight} screen fits the window. Never above
     * {@code normal}: the player chose it, and a screen that fits is left alone.
     */
    public static double scale(int windowWidth, int windowHeight, int normal, int needWidth, int needHeight) {
        double exact = Math.min(normal, Math.min(windowWidth / (double) Math.max(1, needWidth),
                windowHeight / (double) Math.max(1, needHeight)));
        if (exact >= normal) return normal;
        double whole = Math.floor(exact);
        if (whole >= MIN_SCALE && whole >= exact * WHOLE_STEP_TOLERANCE) return whole;
        return Math.max(MIN_SCALE, exact);
    }
}
