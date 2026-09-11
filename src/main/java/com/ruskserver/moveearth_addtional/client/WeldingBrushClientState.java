package com.ruskserver.moveearth_addtional.client;

import com.ruskserver.moveearth_addtional.s2.reinforcement.ReinforcementBrushPattern;

public final class WeldingBrushClientState {
    private static int radius;

    private WeldingBrushClientState() {
    }

    public static int radius() { return radius; }
    public static int size() { return ReinforcementBrushPattern.size(radius); }

    public static boolean adjust(int direction) {
        int updated = ReinforcementBrushPattern.clamp(radius + Integer.signum(direction));
        if (updated == radius) return false;
        radius = updated;
        return true;
    }

    public static void clear() { radius = 0; }
}
