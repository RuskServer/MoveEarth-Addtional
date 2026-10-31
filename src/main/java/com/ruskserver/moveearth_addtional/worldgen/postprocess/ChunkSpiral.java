package com.ruskserver.moveearth_addtional.worldgen.postprocess;

/**
 * Chunk offsets in a square spiral from the centre outwards, by index, so a long walk can be resumed
 * from a saved number. Pure, so it is unit tested.
 */
public final class ChunkSpiral {
    private ChunkSpiral() { }

    /** Chunks in a square of {@code radius} chunks around the centre: (2r + 1)². */
    public static long total(int radius) {
        long side = 2L * Math.max(0, radius) + 1L;
        return side * side;
    }

    /** The offset of the {@code index}th chunk: 0 is the centre, then ring 1 clockwise, ring 2, and so on. */
    public static int[] offset(long index) {
        if (index <= 0) return new int[] {0, 0};
        // Ring r holds the indices (2r-1)² .. (2r+1)² - 1, 8r of them.
        long ring = (long) Math.ceil((Math.sqrt(index + 1.0) - 1.0) / 2.0);
        while (ring > 0 && (2 * ring - 1) * (2 * ring - 1) > index) ring--;
        while ((2 * ring + 1) * (2 * ring + 1) <= index) ring++;
        long position = index - (2 * ring - 1) * (2 * ring - 1);
        long side = 2 * ring;
        int r = (int) ring;
        int along = (int) (position % side);
        return switch ((int) (position / side)) {
            case 0 -> new int[] {r, -r + 1 + along};        // east edge, going south
            case 1 -> new int[] {r - 1 - along, r};         // south edge, going west
            case 2 -> new int[] {-r, r - 1 - along};        // west edge, going north
            default -> new int[] {-r + 1 + along, -r};      // north edge, going east
        };
    }
}
