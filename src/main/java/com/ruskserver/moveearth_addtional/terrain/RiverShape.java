package com.ruskserver.moveearth_addtional.terrain;

/**
 * Channel geometry, carried in tile.json so the generator and the mod cannot drift apart.
 *
 * @param minWidth      narrowest channel the generator emits, in blocks
 * @param maxWidth      widest channel, in blocks
 * @param reachBlocks   distance at which the stored distance field saturates
 * @param depthBlocks   depth of the narrowest channel
 * @param depthScale    extra depth per square root of width above the minimum
 * @param maxDepth      cap on channel depth
 * @param bankBlocks    minimum bank width, for the narrowest streams
 * @param bankRatio     bank width as a share of the channel width
 * @param seaReachY     channels below this surface height are pulled down to hold water
 * @param blendBlocks   height range over which that pull fades in
 * @param freeboardBlocks  drop from the channel's terrain surface to its water surface
 * @param waterRadius   how far from the centre the raised water table is offered
 * @param waterElevationTolerance how far the ground may climb above the water
 *                      surface and still be offered the raised table
 * @param factorBase    vanilla shaping factor away from any channel
 * @param factorChannel shaping factor inside a channel, where the 3D noise has
 *                      to be flattened so a shallow bed is not lifted above its
 *                      own water line
 */
public record RiverShape(
        double minWidth,
        double maxWidth,
        double reachBlocks,
        double depthBlocks,
        double depthScale,
        double maxDepth,
        double bankBlocks,
        double bankRatio,
        double seaReachY,
        double blendBlocks,
        double freeboardBlocks,
        double waterRadius,
        double waterElevationTolerance,
        double factorBase,
        double factorChannel) {

    public static final RiverShape NONE =
            new RiverShape(3.0, 110.0, 200.0, 3.5, 1.15, 11.0, 5.0, 0.8, 92.0, 30.0, 2.0, 64.0, 56.0, 3.5, 16.0);
}
