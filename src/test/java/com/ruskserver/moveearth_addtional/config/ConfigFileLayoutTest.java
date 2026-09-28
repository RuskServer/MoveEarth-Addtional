package com.ruskserver.moveearth_addtional.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigFileLayoutTest {
    @TempDir
    Path directory;

    @Test
    void movesOldFilesIntoTheirFolderKeepingTheirContents() throws IOException {
        Files.writeString(directory.resolve("moveearth_addtional-discord.toml"), "token = \"kept\"\n");
        Files.writeString(directory.resolve("moveearth_addtional-startup.toml"), "setupCompleted = true\n");

        List<String> report = ConfigFileLayout.migrate(directory, ConfigFileLayout.CONFIG_DIRECTORY_MOVES);

        assertEquals(2, report.size());
        assertFalse(Files.exists(directory.resolve("moveearth_addtional-discord.toml")));
        assertEquals("token = \"kept\"\n", Files.readString(directory.resolve("moveearth/server/discord.toml")));
        assertEquals("setupCompleted = true\n", Files.readString(directory.resolve("moveearth/client/startup.toml")));
    }

    @Test
    void neverOverwritesAFileAlreadyAtTheNewPath() throws IOException {
        Files.writeString(directory.resolve("moveearth_addtional-mekanism.toml"), "old\n");
        Files.createDirectories(directory.resolve("moveearth"));
        Files.writeString(directory.resolve("moveearth/mekanism.toml"), "new\n");

        ConfigFileLayout.migrateWorldDirectory(directory);

        assertEquals("new\n", Files.readString(directory.resolve("moveearth/mekanism.toml")));
        assertTrue(Files.exists(directory.resolve("moveearth_addtional-mekanism.toml")));
    }

    @Test
    void theOxygenConfigLeavesTheDefaultServerFileName() throws IOException {
        Files.writeString(directory.resolve("moveearth_addtional-server.toml"), "x\n");

        ConfigFileLayout.migrateWorldDirectory(directory);

        assertTrue(Files.exists(directory.resolve("moveearth/oxygen.toml")));
    }

    @Test
    void doesNothingWithoutOldFilesOrWithoutTheDirectory() {
        assertTrue(ConfigFileLayout.migrateWorldDirectory(directory).isEmpty());
        assertTrue(ConfigFileLayout.migrateWorldDirectory(directory.resolve("missing")).isEmpty());
    }

    @Test
    void everyFileLandsInItsOwnersFolderWithoutCollisions() {
        Set<String> targets = new HashSet<>();
        ConfigFileLayout.CONFIG_DIRECTORY_MOVES.values().forEach(target -> {
            assertTrue(target.startsWith(ConfigFileLayout.CLIENT) || target.startsWith(ConfigFileLayout.SERVER_OPS), target);
            assertTrue(targets.add("config/" + target), target);
        });
        ConfigFileLayout.WORLD_DIRECTORY_MOVES.values().forEach(target -> {
            assertTrue(target.startsWith(ConfigFileLayout.WORLD), target);
            assertTrue(targets.add("world/" + target), target);
        });
    }
}
