package com.ruskserver.moveearth_addtional.ui;

/** Pure-Java formatting math used by the Minecraft Component renderer. */
public final class MoveEarthMessageStyle {
    private MoveEarthMessageStyle() {
    }

    public static int interpolate(int start, int end, float progress) {
        float clamped = Math.max(0.0F, Math.min(1.0F, progress));
        int red = lerp(start >> 16 & 0xFF, end >> 16 & 0xFF, clamped);
        int green = lerp(start >> 8 & 0xFF, end >> 8 & 0xFF, clamped);
        int blue = lerp(start & 0xFF, end & 0xFF, clamped);
        return red << 16 | green << 8 | blue;
    }

    public static String plainText(String symbol, String message) {
        return "[MoveEarth] >>> " + symbol + " " + message;
    }

    private static int lerp(int start, int end, float progress) {
        return Math.round(start + (end - start) * progress);
    }
}
