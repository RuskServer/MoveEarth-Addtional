package com.ruskserver.moveearth_addtional.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Where MoveEarth's config files live, grouped by who owns them so a modpack
 * can ship or skip whole folders:
 *
 * <ul>
 *   <li>{@code config/moveearth/client/}: each player's own settings. Never shipped;
 *       the defaults are in code.</li>
 *   <li>{@code config/moveearth/server/}: dedicated-server operation, including
 *       secrets (Discord bot, analytics). Never shipped.</li>
 *   <li>{@code serverconfig/moveearth/} in each world: rules and balance, synced
 *       from the server. A pack overrides defaults through
 *       {@code defaultconfigs/moveearth/}.</li>
 * </ul>
 *
 * <p>Files from before this layout are moved into place before NeoForge reads them.
 */
public final class ConfigFileLayout {
    private static final String ROOT = "moveearth/";
    public static final String CLIENT = ROOT + "client/";
    public static final String SERVER_OPS = ROOT + "server/";
    /** Relative to a world's serverconfig (or defaultconfigs) folder. */
    public static final String WORLD = ROOT;

    /** Old name in {@code config/} to its new path there. */
    static final Map<String, String> CONFIG_DIRECTORY_MOVES = moves(
            "moveearth_addtional-startup.toml", CLIENT + "startup.toml",
            "moveearth_addtional-scope-pip.toml", CLIENT + "scope-pip.toml",
            "moveearth_addtional-particles.toml", CLIENT + "particles.toml",
            "moveearth_addtional-discord.toml", SERVER_OPS + "discord.toml",
            "moveearth_analytics.properties", SERVER_OPS + "analytics.properties");

    /** Old name in a world's serverconfig (and in defaultconfigs) to its new path there. */
    static final Map<String, String> WORLD_DIRECTORY_MOVES = moves(
            "moveearth_addtional-server.toml", WORLD + "oxygen.toml",
            "moveearth_addtional-tracks.toml", WORLD + "tracks.toml",
            "moveearth_addtional-dcc.toml", WORLD + "dcc.toml",
            "moveearth_addtional-regions.toml", WORLD + "regions.toml",
            "moveearth_addtional-aeronautics.toml", WORLD + "aeronautics.toml",
            "moveearth_addtional-s2-territory.toml", WORLD + "s2-territory.toml",
            "moveearth_addtional-tips.toml", WORLD + "tips.toml",
            "moveearth_addtional-market.toml", WORLD + "market.toml",
            "moveearth_addtional-chat.toml", WORLD + "chat.toml",
            "moveearth_addtional-recovery-dispatch.toml", WORLD + "recovery-dispatch.toml",
            "moveearth_addtional-water-wheels.toml", WORLD + "water-wheels.toml",
            "moveearth_addtional-create-industry.toml", WORLD + "create-industry.toml",
            "moveearth_addtional-mekanism.toml", WORLD + "mekanism.toml");

    private ConfigFileLayout() { }

    /** Moves the old files in {@code config/} and {@code defaultconfigs/}. Run before any config is registered. */
    public static List<String> migrateGameDirectories(Path configDirectory, Path defaultConfigsDirectory) {
        List<String> moved = new ArrayList<>(migrate(configDirectory, CONFIG_DIRECTORY_MOVES));
        moved.addAll(migrate(defaultConfigsDirectory, WORLD_DIRECTORY_MOVES));
        return moved;
    }

    /** Moves the old files in a world's serverconfig folder. Run before NeoForge loads server configs. */
    public static List<String> migrateWorldDirectory(Path serverConfigDirectory) {
        return migrate(serverConfigDirectory, WORLD_DIRECTORY_MOVES);
    }

    /**
     * Moves each old file that exists to its new path, unless something is
     * already there. Returns a line per file moved or left behind.
     */
    static List<String> migrate(Path directory, Map<String, String> moves) {
        List<String> report = new ArrayList<>();
        if (!Files.isDirectory(directory)) return report;
        for (Map.Entry<String, String> move : moves.entrySet()) {
            Path from = directory.resolve(move.getKey());
            if (!Files.isRegularFile(from)) continue;
            Path to = directory.resolve(move.getValue());
            if (Files.exists(to)) {
                report.add("kept " + from + " (" + to + " already exists)");
                continue;
            }
            try {
                Files.createDirectories(to.getParent());
                Files.move(from, to);
                report.add("moved " + from + " -> " + to);
            } catch (IOException exception) {
                report.add("failed to move " + from + ": " + exception);
            }
        }
        return report;
    }

    private static Map<String, String> moves(String... pairs) {
        Map<String, String> map = new LinkedHashMap<>();
        for (int index = 0; index < pairs.length; index += 2) map.put(pairs[index], pairs[index + 1]);
        return java.util.Collections.unmodifiableMap(map);
    }
}
