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
