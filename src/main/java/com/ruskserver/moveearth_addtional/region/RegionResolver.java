package com.ruskserver.moveearth_addtional.region;

import com.ruskserver.moveearth_addtional.terrain.TerrainTile;
import com.ruskserver.moveearth_addtional.terrain.TerrainTileStore;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Says which region a world position belongs to, by reading the terrain tile.
 *
 * <p>This is the whole of layer one. The tile generator already split the world
 * into continents and regions along watersheds and wrote both as layers, so
 * nothing here detects landmasses or clusters anything; it reads two numbers
 * off a file. Replacing the world generator later costs this one class.
 *
 * <p>Called from chunk generation worker threads, so it only ever reads. The
 * tile store is filled before the first chunk exists, and if it somehow is not,
 * every query answers {@link RegionGrid#NONE}, which the gates treat as "allow
 * everything". A missing region map must not silently delete ores from a world.
 */
public final class RegionResolver {

    /** Layer names in the tile. */
    private static final String REGION_LAYER = "region";
    private static final String CONTINENT_LAYER = "continent";

    // How far to look for a coast is deliberately not a constant. A fixed count
    // of cells means a different distance on every tile: the production tile is
    // 1024 cells of 8 blocks, a coarser one was 512 cells of 16, and a limit
    // tuned on one silently halves its reach on the other. The bound is the tile
    // itself, and the search stops as soon as no further ring can beat what it
    // already found, so the real cost is the distance to the nearest land — on
    // the production tile the sea is 70% of the map, but no part of it is more
    // than a few hundred cells from a coast.

    /**
     * Chunk results, kept because worldgen asks for the same chunk many times.
     *
     * <p>A coast search walks rings of cells outwards. Ore placement runs that
     * query once per ore feature per chunk, and Create Ore Excavation's own
     * substance lookup runs tens of thousands of times per chunk, so an uncached
     * search would be the whole cost of the system. The map is keyed by packed
     * chunk position and never invalidated: the tile does not change while the
     * server runs.
     */
    private static final Map<Long, Integer> CHUNK_CACHE = new ConcurrentHashMap<>();

    private RegionResolver() { }

    /** The region at a block position, or {@link RegionGrid#NONE}. */
    public static int regionAt(int blockX, int blockZ) {
        long key = (((long) (blockX >> 4)) << 32) | ((blockZ >> 4) & 0xFFFFFFFFL);
        Integer cached = CHUNK_CACHE.get(key);
        if (cached != null) {
            return cached;
        }
        int region = lookup(blockX, blockZ);
        CHUNK_CACHE.put(key, region);
        return region;
    }

    /**
     * The continent at a block position, or {@link RegionGrid#NONE}.
     *
     * <p>Read straight, with no coast search: a point at sea genuinely belongs
     * to no continent, and the region is the thing the gates need.
     */
    public static int continentAt(int blockX, int blockZ) {
        TerrainTile tile = tileAt(blockX, blockZ);
        if (tile == null || !tile.has(CONTINENT_LAYER)) {
            return RegionGrid.NONE;
        }
        return Math.max(RegionGrid.NONE, tile.sampleNearest(CONTINENT_LAYER, blockX, blockZ));
    }

    /** True once a tile with a region layer is loaded and queries mean something. */
    public static boolean ready() {
        TerrainTileStore store = TerrainTileStore.active();
        return store != null && store.tileCount() > 0;
    }

    /** Drops cached results. For use when the tiles themselves are reloaded. */
    public static void invalidate() {
        CHUNK_CACHE.clear();
    }

    private static int lookup(int blockX, int blockZ) {
        TerrainTile tile = tileAt(blockX, blockZ);
        if (tile == null || !tile.has(REGION_LAYER)) {
            return RegionGrid.NONE;
        }
        return RegionGrid.regionAt(cellsOf(tile, REGION_LAYER),
                tile.cellX(blockX), tile.cellZ(blockZ), tile.sizeCells());
    }

    /** A tile layer seen as a plain grid, shared with the image export. */
    public static RegionGrid.Cells cellsOf(TerrainTile tile, String layer) {
        return new RegionGrid.Cells() {
            @Override public int at(int cellX, int cellZ) {
                return tile.cellValue(layer, cellX, cellZ);
            }
            @Override public int size() {
                return tile.sizeCells();
            }
        };
    }

    private static TerrainTile tileAt(int blockX, int blockZ) {
        TerrainTileStore store = TerrainTileStore.active();
        return store == null ? null : store.tileAt(blockX, blockZ);
    }
}
