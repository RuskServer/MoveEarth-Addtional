package com.ruskserver.moveearth_addtional.client;

/** Horizontal bearing relative to the player's view; no client/world dependencies for tests. */
public final class WaypointNavigation {
    private WaypointNavigation() { }

    public static String arrow(double playerX, double playerZ, float yaw, double targetX, double targetZ) {
        double dx = targetX - playerX;
        double dz = targetZ - playerZ;
        if (dx * dx + dz * dz < 4.0) return "◆";
        double targetYaw = Math.toDegrees(Math.atan2(-dx, dz));
        double relative = ((targetYaw - yaw + 540.0) % 360.0) - 180.0;
        if (relative < -135.0 || relative >= 135.0) return "↓";
        if (relative < -67.5) return "←";
        if (relative < -22.5) return "↖";
        if (relative < 22.5) return "↑";
        if (relative < 67.5) return "↗";
        return "→";
    }

    public static long horizontalDistance(double playerX, double playerZ, double targetX, double targetZ) {
        return Math.round(Math.hypot(targetX - playerX, targetZ - playerZ));
    }

    /** Keep the world-space label roughly the same apparent size after projecting a remote waypoint. */
    public static float worldMarkerScale(double renderedDistance) {
        return (float) Math.clamp(renderedDistance / 16.0D, 1.0D, 12.0D);
    }

    /** Projects a camera-relative direction onto a safe HUD area, including targets behind the view. */
    public static ScreenMarker screenMarker(double right, double up, double forward,
                                            int width, int height, double verticalFovDegrees) {
        double halfVertical = Math.toRadians(Math.clamp(verticalFovDegrees, 30.0D, 110.0D)) * 0.5D;
        double halfHorizontal = Math.atan(Math.tan(halfVertical) * Math.max(1.0D, (double) width / height));
        double xAngle = Math.atan2(right, forward);
        double yAngle = Math.atan2(up, Math.hypot(right, forward));
        double rawX = width * (0.5D + xAngle / (2.0D * halfHorizontal));
        double rawY = height * (0.5D - yAngle / (2.0D * halfVertical)) - 25.0D;
        int marginX = Math.min(105, Math.max(40, width / 5));
        int minY = 43;
        int maxY = Math.max(minY, (int) (height * 0.72D));
        int x = (int) Math.round(Math.clamp(rawX, marginX, width - marginX));
        int y = (int) Math.round(Math.clamp(rawY, minY, maxY));
        String direction = rawX < marginX ? "◀" : rawX > width - marginX ? "▶"
                : rawY < minY ? "▲" : rawY > maxY ? "▼" : "";
        return new ScreenMarker(x, y, direction);
    }

    public record ScreenMarker(int x, int y, String direction) { }
}
