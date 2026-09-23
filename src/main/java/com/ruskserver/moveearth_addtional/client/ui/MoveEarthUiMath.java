package com.ruskserver.moveearth_addtional.client.ui;

/** Pure layout calculations that can be tested without a Minecraft runtime. */
public final class MoveEarthUiMath {
    private MoveEarthUiMath() {
    }

    public static int scroll(int current, double wheelDelta, int step, int contentHeight, int viewportHeight) {
        int maxScroll = Math.max(0, contentHeight - viewportHeight);
        return clamp(current - (int) (wheelDelta * step), 0, maxScroll);
    }

    /**
     * Where a line of text must start for it to sit in the middle of a box.
     *
     * <p>Whole pixels: half a pixel of offset blurs the glyphs. The remainder of
     * an odd gap goes below the text, which is what vanilla does too, so a field
     * here lines up with a vanilla-bordered one of the same height.
     */
    public static int textTop(int boxHeight, int lineHeight) {
        return Math.max(0, (boxHeight - lineHeight) / 2);
    }

    /** How much of a box is left for text once the padding on both sides is taken out. */
    public static int textWidth(int boxWidth, int padding) {
        return Math.max(0, boxWidth - 2 * padding);
    }

    static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(value, max));
    }
}
