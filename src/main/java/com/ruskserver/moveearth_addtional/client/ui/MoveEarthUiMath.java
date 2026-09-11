package com.ruskserver.moveearth_addtional.client.ui;

/** Pure layout calculations that can be tested without a Minecraft runtime. */
public final class MoveEarthUiMath {
    private MoveEarthUiMath() {
    }

    public static int scroll(int current, double wheelDelta, int step, int contentHeight, int viewportHeight) {
        int maxScroll = Math.max(0, contentHeight - viewportHeight);
        return clamp(current - (int) (wheelDelta * step), 0, maxScroll);
    }

    static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(value, max));
    }
}
