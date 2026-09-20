package com.ruskserver.moveearth_addtional.region;

import java.util.Map;

/**
 * How many ore veins a region ends up with, in terms this mod owns.
 *
 * @param regionId       the region measured
 * @param chunksSampled  land chunks of this region in the range measured
 * @param veins          veins that survived both the placement grid and the gate
 * @param blockedByGate  veins the grid offered that the region rules refused
 * @param byMaterial     surviving veins per material name
 */
public record VeinDensity(int regionId, long chunksSampled, long veins, long blockedByGate,
                          Map<String, Long> byMaterial) {

    /**
     * Veins per 1024x1024 blocks, which is 4096 chunks.
     *
     * <p>Stated per area rather than per chunk because the number that matters
     * is how far a player walks between veins, and regions differ in size by
     * more than three to one. The area is land: a region's share of open sea
     * says nothing about that walk.
     *
     * <p>Small regions hold few veins outright — the smallest is about one
     * 1024-block square of land — so a difference of one or two veins moves
     * their figure a long way. The number is exact, not sampled; it is the
     * sample size that is small, and that is a fact about the world.
     */
    public double per1024Blocks() {
        return chunksSampled == 0 ? 0.0 : veins * 4096.0 / chunksSampled;
    }
}
