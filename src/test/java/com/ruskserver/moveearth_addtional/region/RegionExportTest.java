package com.ruskserver.moveearth_addtional.region;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RegionExportTest {

    private static RegionGrid.Cells grid(String... rows) {
        return new RegionGrid.Cells() {
            @Override public int at(int cellX, int cellZ) {
                return rows[cellZ].charAt(cellX) - '0';
            }
            @Override public int size() {
                return rows.length;
            }
        };
    }

    @Test
    @DisplayName("one pixel per cell, in the same orientation as the grid")
    void keepsSizeAndOrientation(@TempDir Path directory) throws IOException {
        // Region 2 sits top-right, in a block so that its corner is interior and
        // keeps its own colour instead of the darkened border. Off-diagonal on
        // purpose: a transposed image would still be square and still hold the
        // right colours, so only an asymmetric cell catches the mistake.
        var cells = grid(
                "1122",
                "1122",
                "1111",
                "1111");
        Path png = RegionExport.write(cells, directory.resolve("region.png"));
        BufferedImage image = ImageIO.read(png.toFile());
        assertEquals(4, image.getWidth());
        assertEquals(4, image.getHeight());
        assertEquals(colour(2), image.getRGB(3, 0) & 0xFFFFFF, "top-right should be region 2");
        assertEquals(colour(1), image.getRGB(0, 3) & 0xFFFFFF, "bottom-left should be region 1");
    }

    @Test
    @DisplayName("regions with different ids get different colours")
    void idsStayApart(@TempDir Path directory) throws IOException {
        assertNotEquals(colour(1), colour(2));
        assertNotEquals(colour(6), colour(7));
        var cells = grid(
                "1122",
                "1122",
                "3344",
                "3344");
        Path png = RegionExport.write(cells, directory.resolve("r.png"));
        BufferedImage image = ImageIO.read(png.toFile());
        // Corners are interior to their block, so they keep their region colour
        // rather than the darkened boundary.
        assertEquals(colour(1), image.getRGB(0, 0) & 0xFFFFFF);
        assertEquals(colour(2), image.getRGB(3, 0) & 0xFFFFFF);
        assertEquals(colour(4), image.getRGB(3, 3) & 0xFFFFFF);
    }

    @Test
    @DisplayName("sea is painted as background, and borders are darkened")
    void seaAndBordersAreMarked(@TempDir Path directory) throws IOException {
        var cells = grid(
                "0000",
                "0110",
                "0110",
                "0000");
        Path png = RegionExport.write(cells, directory.resolve("r.png"));
        BufferedImage image = ImageIO.read(png.toFile());
        assertEquals(0x121620, image.getRGB(0, 0) & 0xFFFFFF);
        // (0,1) is sea with region 1 to its right, so it is a boundary pixel.
        assertEquals(0x0A0A0C, image.getRGB(0, 1) & 0xFFFFFF);
    }

    private static int colour(int id) {
        return java.awt.Color.HSBtoRGB((float) ((id * 0.618033988749895) % 1.0), 0.55f, 0.85f)
                & 0xFFFFFF;
    }
}
