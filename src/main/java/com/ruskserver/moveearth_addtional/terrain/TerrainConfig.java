package com.ruskserver.moveearth_addtional.terrain;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.fml.loading.FMLPaths;

import java.nio.file.Files;
import java.nio.file.Path;

/** Where the terrain tiles live. */
public final class TerrainConfig {
    private static final String DIRECTORY = "moveearth_terrain";

    private TerrainConfig() { }

    /**
     * Prefers a per-world copy so a server can hold several worlds, and falls
     * back to the shared config directory. A per-world directory with no tiles
     * in it, such as one left empty, does not hide tiles in the shared one.
     */
    public static Path tileDirectory(MinecraftServer server) {
        Path perWorld = perWorldDirectory(server);
        Path shared = sharedDirectory();
        if (TerrainTileStore.holdsTiles(perWorld)) return perWorld;
        if (TerrainTileStore.holdsTiles(shared)) return shared;
        // Neither has tiles: name the directory the operator set up, so the
        // startup error describes the place they were looking at.
        return Files.isDirectory(perWorld) ? perWorld : shared;
    }

    /** The per-world location, whether or not anything is in it. */
    public static Path perWorldDirectory(MinecraftServer server) {
        return server.getWorldPath(LevelResource.ROOT).resolve(DIRECTORY);
    }

    /** The fallback shared location, whether or not anything is in it. */
    public static Path sharedDirectory() {
        return FMLPaths.CONFIGDIR.get().resolve(DIRECTORY);
    }
}
