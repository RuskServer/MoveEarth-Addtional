package com.ruskserver.moveearth_addtional.terrain;

import com.ruskserver.moveearth_addtional.Moveearth_addtional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * The set of terrain tiles backing the world.
 *
 * <p>Built once before any chunk is generated and never mutated afterwards, so
 * the chunk generation workers only ever read. Publication happens through a
 * single volatile reference.
 *
 * <p>Outside every tile the world is open ocean. That is what makes the tile
 * scheme extensible: a new landmass is a new tile dropped beside the old ones
 * with water in between, and no existing tile changes by a single byte.
 */
public final class TerrainTileStore {
    private static volatile TerrainTileStore active;

    private final List<TerrainTile> tiles;
    private final int seaY;

    private TerrainTileStore(List<TerrainTile> tiles, int seaY) {
        this.tiles = List.copyOf(tiles);
        this.seaY = seaY;
    }

    public static TerrainTileStore active() {
        return active;
    }

    /** How far below {@code root} a tile directory is still found. */
    static final int SEARCH_DEPTH = 3;

    /**
     * Tile directories at or below {@code root}, nearest first.
     *
     * <p>A directory is a tile if it holds tile.json. The search goes a few
     * levels down rather than one, because the archive these are shipped in
     * carries its own {@code moveearth_terrain} folder, and unpacking it inside
     * a folder of that name -- the obvious reading of "put it in
     * config/moveearth_terrain" -- buries every tile one level too deep. That
     * is a server that will not start, and the layout is unambiguous enough
     * that refusing it serves nobody.
     *
     * <p>Only the shallowest level that has any tiles is returned, so a spare
     * copy kept in a subfolder cannot silently double the world.
     */
    static List<Path> findTiles(Path root) throws IOException {
        List<Path> level = List.of(root);
        for (int depth = 0; depth < SEARCH_DEPTH && !level.isEmpty(); depth++) {
            List<Path> tiles = new ArrayList<>();
            List<Path> next = new ArrayList<>();
            for (Path directory : level) {
                if (!Files.isDirectory(directory)) {
                    continue;
                }
                if (Files.isRegularFile(directory.resolve("tile.json"))) {
                    tiles.add(directory);
                    continue;
                }
                try (Stream<Path> children = Files.list(directory)) {
                    children.filter(Files::isDirectory).forEach(next::add);
                }
            }
            if (!tiles.isEmpty()) {
                return tiles.stream().sorted(Comparator.comparing(Path::toString)).toList();
            }
            level = next.stream().sorted(Comparator.comparing(Path::toString)).toList();
        }
        return List.of();
    }

    /** Loads every tile directory under {@code root}. A directory is a tile if it holds tile.json. */
    public static TerrainTileStore load(Path root) throws IOException {
        List<TerrainTile> found = new ArrayList<>();
        for (Path candidate : findTiles(root)) {
            found.add(TerrainTile.load(candidate));
        }
        if (found.isEmpty()) {
            throw new IOException("No terrain tiles found under " + root.toAbsolutePath()
                    + ". A tile is a directory holding tile.json, searched "
                    + SEARCH_DEPTH + " levels down. " + describe(root));
        }
        int seaY = found.getFirst().seaY();
        for (TerrainTile tile : found) {
            if (tile.seaY() != seaY) {
                throw new IOException("Terrain tiles disagree on sea level: "
                        + tile.directory() + " says " + tile.seaY() + ", expected " + seaY);
            }
        }
        return new TerrainTileStore(found, seaY);
    }

    /**
     * What is actually there, for the message when nothing was found.
     *
     * <p>"No tiles found" on its own does not say whether the folder is missing,
     * empty, or full of the right files one level too deep, and those need
     * different fixes.
     */
    private static String describe(Path root) {
        if (!Files.exists(root)) {
            return "That directory does not exist.";
        }
        if (!Files.isDirectory(root)) {
            return "That path is a file, not a directory.";
        }
        try (Stream<Path> children = Files.list(root)) {
            List<String> names = children.map(p -> p.getFileName()
                            + (Files.isDirectory(p) ? "/" : ""))
                    .sorted().limit(12).toList();
            return names.isEmpty() ? "That directory is empty."
                    : "It holds: " + String.join(", ", names);
        } catch (IOException exception) {
            return "It could not be listed: " + exception.getMessage();
        }
    }

    public static void install(TerrainTileStore store) {
        active = store;
        Moveearth_addtional.LOGGER.info("Terrain tile store installed with {} tile(s), sea level Y={}",
                store.tiles.size(), store.seaY);
    }

    public static void clear() {
        active = null;
    }

    /** Tile covering this position, or null when the position is open ocean. */
    public TerrainTile tileAt(int blockX, int blockZ) {
        for (TerrainTile tile : tiles) {
            if (tile.covers(blockX, blockZ)) {
                return tile;
            }
        }
        return null;
    }

    public int seaY() {
        return seaY;
    }

    public int tileCount() {
        return tiles.size();
    }

    /** Spawn anchor of the largest landmass across every tile, or null when none. */
    public TerrainTile.SpawnAnchor primarySpawnAnchor() {
        TerrainTile.SpawnAnchor best = null;
        for (TerrainTile tile : tiles) {
            for (TerrainTile.SpawnAnchor anchor : tile.spawnAnchors()) {
                if (best == null || anchor.areaBlocks() > best.areaBlocks()) {
                    best = anchor;
                }
            }
        }
        return best;
    }
}
