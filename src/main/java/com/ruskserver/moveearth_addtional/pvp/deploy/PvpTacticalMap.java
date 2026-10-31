package com.ruskserver.moveearth_addtional.pvp.deploy;

import java.io.ByteArrayOutputStream;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

/**
 * A PvP map's battle area seen from above, for the deploy screen: for each cell, the colour a vanilla map would
 * give its top block and that block's height. Scanned once when the map is made, never during a match, and sent
 * to players compressed. Pure, so it is tested without Minecraft.
 *
 * @param originX  block X of cell (0, 0)
 * @param originZ  block Z of cell (0, 0)
 * @param cellSize blocks per cell along each side: 1, 2 or 4
 * @param width    cells along X
 * @param depth    cells along Z
 * @param baseY    the height a height value of 0 stands for
 * @param colors   a vanilla map colour id per cell, row by row along X; 0 where nothing was found
 * @param heights  per cell, the top block's Y above {@code baseY}, unsigned
 */
public record PvpTacticalMap(String mapId, long version, int originX, int originZ, int cellSize, int width, int depth,
                             int baseY, byte[] colors, byte[] heights) {
    /** The most cells along a side; larger areas use bigger cells. */
    public static final int MAX_CELLS = 256;

    public PvpTacticalMap {
        if (width <= 0 || depth <= 0 || width > MAX_CELLS || depth > MAX_CELLS) {
            throw new IllegalArgumentException("Tactical map size out of range: " + width + "x" + depth);
        }
        if (colors.length != width * depth || heights.length != width * depth) {
            throw new IllegalArgumentException("Tactical map data does not match its size");
        }
    }

    public int color(int x, int z) {
        return colors[z * width + x] & 0xFF;
    }

    public int height(int x, int z) {
        return baseY + (heights[z * width + x] & 0xFF);
    }

    /** The colours then the heights, deflated. */
    public byte[] compressed() {
        byte[] raw = new byte[colors.length * 2];
        System.arraycopy(colors, 0, raw, 0, colors.length);
        System.arraycopy(heights, 0, raw, colors.length, heights.length);
        Deflater deflater = new Deflater(Deflater.BEST_COMPRESSION);
        deflater.setInput(raw);
        deflater.finish();
        ByteArrayOutputStream out = new ByteArrayOutputStream(raw.length / 4 + 64);
        byte[] buffer = new byte[8192];
        while (!deflater.finished()) out.write(buffer, 0, deflater.deflate(buffer));
        deflater.end();
        return out.toByteArray();
    }

    public static PvpTacticalMap fromCompressed(String mapId, long version, int originX, int originZ, int cellSize,
                                                int width, int depth, int baseY, byte[] data) {
        if (width <= 0 || depth <= 0 || width > MAX_CELLS || depth > MAX_CELLS) {
            throw new IllegalArgumentException("Tactical map size out of range: " + width + "x" + depth);
        }
        int cells = width * depth;
        byte[] raw = new byte[cells * 2];
        Inflater inflater = new Inflater();
        inflater.setInput(data);
        try {
            int read = 0;
            while (read < raw.length && !inflater.finished()) {
                int n = inflater.inflate(raw, read, raw.length - read);
                if (n == 0 && (inflater.needsInput() || inflater.needsDictionary())) break;
                read += n;
            }
            if (read != raw.length) throw new IllegalArgumentException("Tactical map data is truncated");
        } catch (DataFormatException exception) {
            throw new IllegalArgumentException("Tactical map data is corrupt", exception);
        } finally {
            inflater.end();
        }
        byte[] colors = new byte[cells];
        byte[] heights = new byte[cells];
        System.arraycopy(raw, 0, colors, 0, cells);
        System.arraycopy(raw, cells, heights, 0, cells);
        return new PvpTacticalMap(mapId, version, originX, originZ, cellSize, width, depth, baseY, colors, heights);
    }
}
