package com.ruskserver.moveearth_addtional.region;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import javax.imageio.ImageIO;

/**
 * Draws a tile's region layer so it can be held up against the one the tile
 * generator drew.
 *
 * <p>This paints the layer exactly as it is on disk, one pixel per cell, not
 * what {@link RegionResolver} answers. The resolver fills the sea with the
 * nearest coast's region, and the generator's own image does not, so painting
 * the filled view would guarantee the two pictures differ and prove nothing.
 * Whether the sea fill works is a separate question, answered by asking for a
 * position at sea.
 *
 * <p>The colours are not the generator's. It builds its palette from numpy's
 * PCG64 stream, which cannot be reproduced here, so matching hues is out of
 * reach; what is being compared is where the borders run. Boundaries are
 * darkened the same way for that reason.
 */
public final class RegionExport {

    /** Sea, matching the generator's background. */
    private static final int BACKGROUND = 0x121620;

    /** Boundary between two regions, matching the generator. */
    private static final int EDGE = 0x0A0A0C;

    private RegionExport() { }

    /**
     * Writes a grid of ids to a PNG and returns the path.
     *
     * <p>Takes the same grid abstraction the lookup uses rather than a tile, so
     * that what is drawn can be checked without a running server. Loading a real
     * tile pulls in the mod's config system, which a unit test has no way to
     * stand up.
     */
    public static Path write(RegionGrid.Cells cells, Path destination) throws IOException {
        int size = cells.size();
        int[] ids = new int[size * size];
        for (int z = 0; z < size; z++) {
            for (int x = 0; x < size; x++) {
                ids[z * size + x] = Math.max(0, cells.at(x, z));
            }
        }
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
        for (int z = 0; z < size; z++) {
            for (int x = 0; x < size; x++) {
                int id = ids[z * size + x];
                image.setRGB(x, z, isEdge(ids, size, x, z) ? EDGE : colourOf(id));
            }
        }
        ImageIO.write(image, "png", destination.toFile());
        return destination;
    }

    /** True where the cell differs from the one after it, as the generator does. */
    private static boolean isEdge(int[] ids, int size, int x, int z) {
        int here = ids[z * size + x];
        if (x + 1 < size && ids[z * size + x + 1] != here) {
            return true;
        }
        return z + 1 < size && ids[(z + 1) * size + x] != here;
    }

    /**
     * A colour per id, spread around the hue circle.
     *
     * <p>Generated rather than listed so that a tile with more regions than
     * anyone expected still comes out readable instead of running off the end
     * of a table. The golden-ratio step keeps consecutive ids far apart in hue,
     * which matters because neighbouring regions usually have consecutive ids.
     */
    private static int colourOf(int id) {
        if (id == 0) {
            return BACKGROUND;
        }
        float hue = (float) ((id * 0.618033988749895) % 1.0);
        return java.awt.Color.HSBtoRGB(hue, 0.55f, 0.85f) & 0xFFFFFF;
    }
}
