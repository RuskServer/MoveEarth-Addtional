package com.ruskserver.moveearth_addtional.raid;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Original, block-built steam pirate ship. Coordinates are relative to its main deck anchor. */
public final class RaidAirshipBlueprint {
    public enum Part {
        HULL, DECK, TRIM, RAIL, CHAIN, BLACK_ENVELOPE, GRAY_ENVELOPE,
        COPPER, MACHINERY, GLASS, SMOKESTACK, CORE, PROPELLER, BURNER, CANNON,
        BARREL, MAST, BLACK_FLAG, WHITE_FLAG
    }
    public enum Facing { NORTH, SOUTH, WEST, UP }
    public record Position(int x, int y, int z) { }
    public record Block(Position position, Part part, Facing facing) { }
    public record Bounds(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) { }
    private static final List<Block> BLOCKS = build();
    private static final Bounds BOUNDS = bounds(BLOCKS);

    private RaidAirshipBlueprint() { }
    public static List<Block> blocks() { return BLOCKS; }
    public static Bounds bounds() { return BOUNDS; }

    private static List<Block> build() {
        Map<Position, Block> blocks = new LinkedHashMap<>();
        // A curved keel and closed wooden hull, not a flat platform under a balloon.
        for (int x = -16; x <= 16; x++) {
            int width = width(x);
            for (int z = -width; z <= width; z++) {
                put(blocks, x, 0, z, Part.DECK);
                if (Math.abs(z) == width) put(blocks, x, 1, z, Part.RAIL);
                for (int y = -3; y < 0; y++) {
                    int lowerWidth = Math.max(0, width + y + 1);
                    if (Math.abs(z) <= lowerWidth) {
                        put(blocks, x, y, z, y == -1 ? Part.TRIM : Part.HULL);
                    }
                }
            }
        }
        // Raised stern cabin: windows and a roof leave a usable center entrance from the deck.
        for (int x = -13; x <= -7; x++) {
            for (int z = -2; z <= 2; z++) {
                put(blocks, x, 4, z, Part.TRIM);
                for (int y = 1; y <= 3; y++) {
                    if (x != -13 && x != -7 && Math.abs(z) != 2) continue;
                    if (x == -7 && z == 0 && y <= 2) continue;
                    put(blocks, x, y, z, y == 2 ? Part.GLASS : Part.HULL);
                }
            }
        }
        // Forecastle and bowsprit, connected to the hull.
        for (int x = 10; x <= 15; x++) {
            for (int z = -width(x) + 1; z < width(x); z++) put(blocks, x, 1, z, Part.TRIM);
        }
        for (int x = 14; x <= 22; x++) put(blocks, x, 2, 0, Part.MAST);
        put(blocks, 14, 1, 0, Part.MAST);

        // Closed stepped ellipsoid with connected edges and regular black/grey bands.
        for (int x = -20; x <= 20; x++) {
            for (int y = 6; y <= 18; y++) {
                for (int z = -6; z <= 6; z++) {
                    if (!inside(x, y, z) || !surface(x, y, z)) continue;
                    boolean band = Math.abs(x) == 5 || Math.abs(x) == 13 || y <= 8;
                    put(blocks, x, y, z, band ? Part.GRAY_ENVELOPE : Part.BLACK_ENVELOPE);
                }
            }
        }
        // Six visible suspension cores remain shootable from outside. No extra loot/core count.
        for (int x : new int[]{-8, 0, 8}) {
            for (int z : new int[]{-3, 3}) {
                for (int y = 1; y < 12 && !inside(x, y, z); y++) put(blocks, x, y, z, Part.CHAIN);
                put(blocks, x, 5, z, Part.CORE);
            }
        }
        // Copper steam pods outside the deck, with outward propellers and broadside weapons.
        for (int sign : new int[]{-1, 1}) {
            for (int x = -5; x <= -1; x++) {
                for (int z = 4; z <= 6; z++) {
                    put(blocks, x, 0, sign * z, Part.COPPER);
                    put(blocks, x, 1, sign * z, z == 5 ? Part.MACHINERY : Part.COPPER);
                }
            }
            put(blocks, -3, 1, sign * 7, Part.PROPELLER, sign < 0 ? Facing.NORTH : Facing.SOUTH);
            for (int y = 2; y <= 5; y++) put(blocks, -3, y, sign * 5, Part.SMOKESTACK);
            put(blocks, -3, 6, sign * 5, Part.COPPER);
            put(blocks, 8, 1, sign * 4, Part.COPPER);
            put(blocks, 8, 2, sign * 4, Part.CANNON, sign < 0 ? Facing.NORTH : Facing.SOUTH);
        }
        // Central burner, connected by a mast to the underside of the balloon.
        for (int y = 1; y <= 5; y++) put(blocks, 0, y, 0, y == 5 ? Part.BURNER : Part.MAST,
                y == 5 ? Facing.WEST : Facing.UP);
        put(blocks, -5, 1, 1, Part.BARREL, Facing.UP);
        put(blocks, 5, 1, 1, Part.BARREL, Facing.UP);
        // Tail fins and a small, readable skull-like pennant above the black balloon.
        for (int x = -19; x <= -14; x++) {
            for (int y = 17; y <= 20; y++) {
                if (x + 19 >= y - 17) put(blocks, x, y, 0, Part.GRAY_ENVELOPE);
            }
            for (int z = -8; z <= 8; z++) {
                if (Math.abs(z) >= 4 && Math.abs(z) <= 8 - Math.abs(x + 16)) {
                    put(blocks, x, 12, z, Part.GRAY_ENVELOPE);
                }
            }
        }
        for (int y = 19; y <= 23; y++) put(blocks, 0, y, 0, Part.MAST, Facing.UP);
        for (int x = 1; x <= 5; x++) {
            for (int y = 21; y <= 23; y++) {
                boolean skull = y == 23 && x >= 2 && x <= 4 || y == 22 && (x == 2 || x == 4)
                        || y == 21 && x == 3;
                put(blocks, x, y, 0, skull ? Part.WHITE_FLAG : Part.BLACK_FLAG);
            }
        }
        return List.copyOf(blocks.values());
    }

    private static int width(int x) { return Math.max(1, 5 - Math.max(0, Math.abs(x) - 7) / 2); }
    private static boolean inside(int x, int y, int z) {
        return x * x / 400.0 + (y - 12) * (y - 12) / 36.0 + z * z / 36.0 <= 1.0;
    }
    private static boolean surface(int x, int y, int z) {
        // Include diagonal neighbors so curved steps overlap instead of touching only at corners.
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (!inside(x + dx, y + dy, z + dz)) return true;
                }
            }
        }
        return false;
    }
    private static void put(Map<Position, Block> blocks, int x, int y, int z, Part part) {
        put(blocks, x, y, z, part, Facing.WEST);
    }
    private static void put(Map<Position, Block> blocks, int x, int y, int z, Part part, Facing facing) {
        Position pos = new Position(x, y, z);
        blocks.put(pos, new Block(pos, part, facing));
    }
    private static Bounds bounds(List<Block> blocks) {
        return new Bounds(blocks.stream().mapToInt(b -> b.position.x).min().orElseThrow(),
                blocks.stream().mapToInt(b -> b.position.y).min().orElseThrow(),
                blocks.stream().mapToInt(b -> b.position.z).min().orElseThrow(),
                blocks.stream().mapToInt(b -> b.position.x).max().orElseThrow(),
                blocks.stream().mapToInt(b -> b.position.y).max().orElseThrow(),
                blocks.stream().mapToInt(b -> b.position.z).max().orElseThrow());
    }
}
