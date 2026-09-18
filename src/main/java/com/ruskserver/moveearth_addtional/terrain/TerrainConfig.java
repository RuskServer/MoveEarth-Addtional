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
     * back to the shared config directory.
     */
    public static Path tileDirectory(MinecraftServer server) {
        Path perWorld = perWorldDirectory(server);
        if (Files.isDirectory(perWorld)) {
            return perWorld;
        }
        return sharedDirectory();
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
