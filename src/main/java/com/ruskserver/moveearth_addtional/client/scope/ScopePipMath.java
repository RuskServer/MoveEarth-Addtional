package com.ruskserver.moveearth_addtional.client.scope;

public final class ScopePipMath {
    private ScopePipMath() { }

    public static boolean eligibleOptic(boolean scope, double zoom, double minimum, float aim, int lenses) {
        return scope && Double.isFinite(zoom) && Double.isFinite(minimum) && minimum > 1
                && zoom >= minimum && Float.isFinite(aim) && aim > 0.01F && lenses > 0 && lenses <= 127;
    }

    public static double lensFov(double fov, double zoom) {
        if (!Double.isFinite(fov) || !Double.isFinite(zoom) || fov <= 0 || fov >= 180 || zoom < 1) return fov;
        return Math.toDegrees(2 * Math.atan(Math.tan(Math.toRadians(fov) / 2) / zoom));
    }
}
