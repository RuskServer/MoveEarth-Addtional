package com.ruskserver.moveearth_addtional.compat.aeronautics;

/**
 * Pure policy for deciding whether a rotary constraint is safe to replace.
 */
public final class SwivelConstraintPolicy {
    static final double AXIS_EPSILON = 1.0e-4;

    private SwivelConstraintPolicy() {
    }

    public static boolean shouldReplace(
            boolean enabled,
            boolean horizontalOnly,
            double normal1X,
            double normal1Y,
            double normal1Z,
            double normal2X,
            double normal2Y,
            double normal2Z
    ) {
        if (!enabled) {
            return false;
        }
        if (!horizontalOnly) {
            return isUsableAxis(normal1X, normal1Y, normal1Z)
                    && isUsableAxis(normal2X, normal2Y, normal2Z);
        }
        return isVertical(normal1X, normal1Y, normal1Z)
                && isVertical(normal2X, normal2Y, normal2Z);
    }

    private static boolean isUsableAxis(double x, double y, double z) {
        return x * x + y * y + z * z > AXIS_EPSILON;
    }

    private static boolean isVertical(double x, double y, double z) {
        if (!isUsableAxis(x, y, z)) {
            return false;
        }
        double length = Math.sqrt(x * x + y * y + z * z);
        return Math.abs(x / length) <= AXIS_EPSILON
                && Math.abs(z / length) <= AXIS_EPSILON
                && Math.abs(Math.abs(y / length) - 1.0) <= AXIS_EPSILON;
    }
}
