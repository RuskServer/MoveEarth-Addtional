package com.ruskserver.moveearth_addtional.terrain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Where the store will look for tiles.
 *
 * <p>Getting this wrong is a server that refuses to start, so the layouts a
 * person actually produces when unpacking the archive each have a case here.
 */
class TileSearchTest {

    private static Path tile(Path parent, String name) throws IOException {
        Path directory = Files.createDirectories(parent.resolve(name));
        Files.writeString(directory.resolve("tile.json"), "{}");
        return directory;
    }

    @Test
    void findsTilesOneLevelDown(@TempDir Path root) throws IOException {
        Path expected = tile(root, "tile_0_0");
        assertEquals(List.of(expected), TerrainTileStore.findTiles(root));
    }

    @Test
    void findsARootThatIsItselfATile(@TempDir Path root) throws IOException {
        Files.writeString(root.resolve("tile.json"), "{}");
        assertEquals(List.of(root), TerrainTileStore.findTiles(root));
    }

    @Test
    void findsTilesUnpackedOneFolderTooDeep(@TempDir Path root) throws IOException {
        // config/moveearth_terrain/moveearth_terrain/tile_0_0: what you get from
        // unpacking the archive inside the folder it is meant to become
        Path expected = tile(root.resolve("moveearth_terrain"), "tile_0_0");
        assertEquals(List.of(expected), TerrainTileStore.findTiles(root));
    }

    @Test
    void takesOnlyTheShallowestLevelThatHasTiles(@TempDir Path root) throws IOException {
        Path shallow = tile(root, "tile_0_0");
        tile(root.resolve("old_copy/nested"), "tile_0_0");
        // a spare copy kept deeper must not be loaded alongside the real one
        assertEquals(List.of(shallow), TerrainTileStore.findTiles(root));
    }

    @Test
    void findsEveryTileAtTheSameLevel(@TempDir Path root) throws IOException {
        tile(root, "tile_0_0");
        tile(root, "tile_1_0");
        assertEquals(2, TerrainTileStore.findTiles(root).size());
    }

    @Test
    void reportsNothingRatherThanFailingWhenThereIsNothing(@TempDir Path root) throws IOException {
        Files.createDirectories(root.resolve("empty"));
        assertTrue(TerrainTileStore.findTiles(root).isEmpty());
        assertTrue(TerrainTileStore.findTiles(root.resolve("does_not_exist")).isEmpty());
    }
}
