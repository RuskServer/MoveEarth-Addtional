package com.ruskserver.moveearth_addtional.s2.territory;

/** Pure world-to-vanilla-map projection shared by map overlay implementations. */
public final class TerritoryMapProjection {
    public static final float MAP_SIZE = 128.0F;

    private TerritoryMapProjection() { }

    public static ProjectedRect projectChunks(int mapCenterX, int mapCenterZ, int mapScale,
                                               int centerChunkX, int centerChunkZ, int radius) {
        int safeScale = Math.max(0, Math.min(4, mapScale));
        int safeRadius = Math.max(0, radius);
        double blocksPerPixel = 1 << safeScale;
        double minWorldX = (long) (centerChunkX - safeRadius) * 16L;
        double minWorldZ = (long) (centerChunkZ - safeRadius) * 16L;
        double maxWorldX = (long) (centerChunkX + safeRadius + 1) * 16L;
        double maxWorldZ = (long) (centerChunkZ + safeRadius + 1) * 16L;
        float minX = clip((float) (64.0D + (minWorldX - mapCenterX) / blocksPerPixel));
        float minY = clip((float) (64.0D + (minWorldZ - mapCenterZ) / blocksPerPixel));
        float maxX = clip((float) (64.0D + (maxWorldX - mapCenterX) / blocksPerPixel));
        float maxY = clip((float) (64.0D + (maxWorldZ - mapCenterZ) / blocksPerPixel));
        boolean visible = maxX > minX && maxY > minY
                && maxWorldX > mapCenterX - 64.0D * blocksPerPixel
                && minWorldX < mapCenterX + 64.0D * blocksPerPixel
                && maxWorldZ > mapCenterZ - 64.0D * blocksPerPixel
                && minWorldZ < mapCenterZ + 64.0D * blocksPerPixel;
        return new ProjectedRect(minX, minY, maxX, maxY, visible);
    }

    /**
     * A block-aligned area from ({@code minX}, {@code minZ}) inclusive to ({@code maxX}, {@code maxZ}) exclusive.
     * At coarse map scales a small area would project to under a pixel, so it is grown about its centre to
     * at least {@code minimumPixels} across before being clipped to the map.
     */
    public static ProjectedRect projectBlocks(int mapCenterX, int mapCenterZ, int mapScale,
                                              int minX, int minZ, int maxX, int maxZ, float minimumPixels) {
        double blocksPerPixel = 1 << Math.max(0, Math.min(4, mapScale));
        double left = 64.0D + (minX - mapCenterX) / blocksPerPixel;
        double top = 64.0D + (minZ - mapCenterZ) / blocksPerPixel;
        double right = 64.0D + (maxX - mapCenterX) / blocksPerPixel;
        double bottom = 64.0D + (maxZ - mapCenterZ) / blocksPerPixel;
        double grow = Math.max(0.0D, minimumPixels);
        if (right - left < grow) {
            double centre = (left + right) / 2.0D;
            left = centre - grow / 2.0D;
            right = centre + grow / 2.0D;
        }
        if (bottom - top < grow) {
            double centre = (top + bottom) / 2.0D;
            top = centre - grow / 2.0D;
            bottom = centre + grow / 2.0D;
        }
        boolean visible = right > 0.0D && left < MAP_SIZE && bottom > 0.0D && top < MAP_SIZE;
        return new ProjectedRect(clip((float) left), clip((float) top), clip((float) right), clip((float) bottom),
                visible);
    }

    public static Point projectBlock(int mapCenterX, int mapCenterZ, int mapScale, int worldX, int worldZ) {
        double blocksPerPixel = 1 << Math.max(0, Math.min(4, mapScale));
        return new Point((float) (64.0D + (worldX - mapCenterX) / blocksPerPixel),
                (float) (64.0D + (worldZ - mapCenterZ) / blocksPerPixel));
    }

    private static float clip(float value) {
        return Math.max(0.0F, Math.min(MAP_SIZE, value));
    }

    public record ProjectedRect(float minX, float minY, float maxX, float maxY, boolean visible) { }
    public record Point(float x, float y) {
        public boolean visible() { return x >= 0.0F && x <= MAP_SIZE && y >= 0.0F && y <= MAP_SIZE; }
    }
}
