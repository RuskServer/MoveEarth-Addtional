package com.ruskserver.moveearth_addtional.compat.mekanism;

/**
 * Which water washes radiation off. Water that came with the world, the sea,
 * rivers and lakes, is contaminated and never does; water a bucket placed where
 * there was none is clean. Pure, so it is tested without a level.
 *
 * <p>A water block is clean when it is a placed source, when it is fed by one
 * beside it with no world water mixing in from another side, or when it falls
 * straight down from one, so a pool, a puddle and a shower all work.
 */
public final class CleanWaterRule {
    /** How far a shower's falling water is followed up to its source. */
    static final int SHOWER_HEIGHT = 4;

    private static final int[][] SIDES = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

    /** What is at a block, as far as this rule cares. */
    public enum Water { NONE, PLACED_SOURCE, OTHER_SOURCE, FLOWING }

    @FunctionalInterface
    public interface Lookup {
        Water at(int x, int y, int z);
    }

    private CleanWaterRule() {
    }

    public static boolean clean(Lookup lookup, int x, int y, int z) {
        Water here = lookup.at(x, y, z);
        if (here == Water.NONE) return false;
        if (here == Water.PLACED_SOURCE) return true;
        if (here == Water.FLOWING && fallsFromPlacedSource(lookup, x, y, z)) return true;
        boolean fed = false;
        for (int[] side : SIDES) {
            Water beside = lookup.at(x + side[0], y, z + side[1]);
            if (beside == Water.OTHER_SOURCE) return false;
            fed |= beside == Water.PLACED_SOURCE;
        }
        return fed;
    }

    private static boolean fallsFromPlacedSource(Lookup lookup, int x, int y, int z) {
        for (int up = 1; up <= SHOWER_HEIGHT; up++) {
            Water above = lookup.at(x, y + up, z);
            if (above == Water.PLACED_SOURCE) return true;
            if (above != Water.FLOWING) return false;
        }
        return false;
    }
}
