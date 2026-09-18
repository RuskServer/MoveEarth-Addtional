package com.ruskserver.moveearth_addtional.terrain;

/**
 * Cuts channels into the terrain at block resolution.
 *
 * <p>Carving is done here rather than baked into the tile's height layer because
 * a baked carve cannot be narrower than the tile's own resolution: the value is
 * interpolated bilinearly, so at sixteen blocks per cell every river came out
 * about thirty blocks wide and small streams were impossible. The tile instead
 * carries the distance to the channel centre, which interpolates cleanly, and
 * the profile is evaluated here against real block coordinates.
 *
 * <p>Minecraft only puts water at sea level, so a channel above it stays a dry
 * valley however deep it is cut. Lower reaches are therefore pulled down to hold
 * water while upstream reaches are only incised, blended so the long profile has
 * no step in it. Vanilla's own rivers are sea-level channels for the same reason.
 *
 * <p>No Minecraft types, so the shape can be checked in tests.
 */
public final class RiverProfile {
    private RiverProfile() { }

    private static double smoothstep(double t) {
        double x = t < 0.0 ? 0.0 : Math.min(t, 1.0);
        return x * x * (3.0 - 2.0 * x);
    }

    /** Depth of a channel of this width, in blocks. */
    public static double depth(double width, RiverShape shape) {
        double above = Math.max(0.0, width - shape.minWidth());
        return Math.min(shape.maxDepth(), shape.depthBlocks() + shape.depthScale() * Math.sqrt(above));
    }

    /**
     * How far the banks slope out on each side, in blocks.
     *
     * <p>Proportional to the channel, so a wide river gets a wide valley. Scaling
     * it against the widest possible channel instead left every river with much
     * the same narrow bank, and a big one came out as a rectangular trench.
     */
    public static double bank(double width, RiverShape shape) {
        return Math.max(shape.bankBlocks(), width * shape.bankRatio());
    }

    /**
     * The generated water surface and bed share a datum, including upland streams.
     *
     * <p>Inside the channel the bed is a shallow bowl that rises to exactly the
     * water line at the edge, so the water comes out the requested width and the
     * cross section has no flat floor or vertical wall. Beyond that the banks
     * ease up to the surrounding ground.
     */
    public static double carveAtWater(double distance, double width, double surfaceY,
                                      double waterY, RiverShape shape) {
        if (width <= 0) {
            return 0.0;
        }
        double half = width * 0.5;
        double bank = bank(width, shape);
        if (distance >= half + bank) {
            return 0.0;
        }
        // how far the ground has to come down just to reach the water line
        double toWater = Math.max(0.0, surfaceY - waterY);
        if (distance <= half) {
            double t = half <= 0.0 ? 0.0 : distance / half;
            return toWater + depth(width, shape) * (1.0 - t * t);
        }
        return toWater * (1.0 - smoothstep((distance - half) / bank));
    }

    /**
     * Blocks to cut out of the surface at a point.
     *
     * @param distance distance to the channel centre, in blocks
     * @param width    width of that channel, in blocks; zero means no channel
     * @param surfaceY the uncarved surface height
     * @param seaY     the world's sea level
     */
    public static double carve(double distance, double width, double surfaceY,
                               int seaY, RiverShape shape) {
        if (width <= 0.0) {
            return 0.0;
        }
        double half = width * 0.5;
        double bank = bank(width, shape);
        if (distance >= half + bank) {
            return 0.0;
        }

        double depth = depth(width, shape);
        double dryBed = surfaceY - depth;
        double wetBed = seaY - depth;
        double pull = smoothstep((shape.seaReachY() - surfaceY) / Math.max(shape.blendBlocks(), 1.0E-6));
        double bed = dryBed + (Math.min(wetBed, dryBed) - dryBed) * pull;

        double full = Math.max(0.0, surfaceY - bed);
        if (distance <= half) {
            return full;
        }
        return full * (1.0 - smoothstep((distance - half) / bank));
    }
}
