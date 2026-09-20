package com.ruskserver.moveearth_addtional.terrain;

/**
 * Which way the river runs at a point, as a unit vector on the ground plane.
 *
 * <p>Our channels hold still water on purpose: the level steps down in whole
 * blocks, and letting Minecraft's fluid settle those steps would have the river
 * spreading into whatever lies beside it. Still water has no flow to read,
 * though, so anything that asks the world which way the water is going -- a
 * water wheel, most obviously -- gets nothing. The tile knows the answer
 * regardless, because its channel segments run from each cell to the one it
 * drains into, and that is downstream by construction.
 */
public record RiverCurrent(double x, double z, double strength) {
    public static final RiverCurrent NONE = new RiverCurrent(0.0, 0.0, 0.0);

    public RiverCurrent {
        strength = Math.max(0.0D, Math.min(1.0D, strength));
    }

    /** Whether there is a direction here at all. */
    public boolean present() {
        return strength > 0.0D && (x != 0.0D || z != 0.0D);
    }
}
