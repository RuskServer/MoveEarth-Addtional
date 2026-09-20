package com.ruskserver.moveearth_addtional.terrain;

import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import net.minecraft.server.MinecraftServer;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import com.ruskserver.moveearth_addtional.region.RegionEvents;
import com.ruskserver.moveearth_addtional.region.RegionProfiles;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Loads the tiles before anything can sample the world.
 *
 * <p>{@code ServerAboutToStartEvent} is fired by {@code initServer} immediately
 * before {@code loadLevel}, so it precedes the construction of any
 * {@code ServerLevel}.
 *
 * <p>{@code LevelEvent.Load} looks like the natural hook and is not: it is posted
 * after {@code levels.put}, but the {@code ServerLevel} constructor itself calls
 * {@code ensureStructuresGenerated}, which dispatches stronghold ring placement
 * onto the common pool. Those tasks sample the biome source -- and therefore the
 * density functions -- while the constructor is still running, well before the
 * event fires. Hooking the level load throws on a background thread during world
 * creation.
 *
 * <p>Nothing here needs a level: the tile directory is derived from the server's
 * world path, which is set in the {@code MinecraftServer} constructor.
 */
public final class TerrainEvents {
    private TerrainEvents() { }

    /**
     * Whether the world's own noise router reads one of our map fields.
     *
     * <p>Asked of the density functions themselves rather than assumed from the
     * build or from a config flag, so it stays right however the datapacks are
     * arranged: a server that turns ours off, a player build that never had it,
     * and a world made before it existed all answer correctly.
     */
    private static boolean usesOurTerrain(MinecraftServer server) {
        Registry<NoiseGeneratorSettings> settings =
                server.registryAccess().registryOrThrow(Registries.NOISE_SETTINGS);
        NoiseGeneratorSettings overworld = settings.get(NoiseGeneratorSettings.OVERWORLD);
        if (overworld == null) {
            return false;
        }
        boolean[] found = {false};
        overworld.noiseRouter().mapAll(new DensityFunction.Visitor() {
            @Override
            public DensityFunction apply(DensityFunction function) {
                if (function instanceof MapFieldDensityFunction) {
                    found[0] = true;
                }
                return function;
            }
        });
        return found[0];
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, TerrainEvents::onServerAboutToStart);
        NeoForge.EVENT_BUS.addListener(TerrainEvents::onServerStarted);
        NeoForge.EVENT_BUS.addListener(TerrainEvents::onServerStopped);
    }

    /**
     * Allocates region resources, from the materials the pack really produces.
     *
     * <p>The deposit list is read first because the allocation must not hand a
     * region a resource nothing yields. Both happen here, before the first
     * chunk exists, because that is the last moment the answer can still be
     * decided for a world.
     */
    private static void buildRegions(MinecraftServer server) {
        java.util.Set<String> available = java.util.Set.of();
        java.util.Map<String, Double> density = java.util.Map.of();
        if (net.neoforged.fml.ModList.get().isLoaded(RegionEvents.RNS_MOD_ID)) {
            com.ruskserver.moveearth_addtional.compat.rns.RnsDepositGate.rebuild(server);
            available = com.ruskserver.moveearth_addtional.compat.rns.RnsDepositGate
                    .availableMaterials();
            density = com.ruskserver.moveearth_addtional.compat.rns.RnsDepositGate
                    .depositsPerChunk(server);
        }
        RegionProfiles.build(server, available, density);
    }

    private static void onServerAboutToStart(ServerAboutToStartEvent event) {
        if (TerrainTileStore.active() != null) {
            buildRegions(event.getServer());
            return;
        }
        MinecraftServer server = event.getServer();
        if (!usesOurTerrain(server)) {
            // Nothing in this world reads a tile, so there is nothing to load and
            // nothing to protect. The player build ships without the worldgen
            // data, so a single player world on it is a vanilla world; demanding
            // tiles there would stop it opening at all.
            Moveearth_addtional.LOGGER.info(
                    "World does not use the MoveEarth density functions; terrain tiles are not loaded.");
            return;
        }
        Path root = TerrainConfig.tileDirectory(server);
        try {
            TerrainTileStore.install(TerrainTileStore.load(root));
            // Straight after the tiles and still before the first chunk: the
            // allocation reads the tiles, and the worldgen threads read the
            // allocation, so it must exist before either of them runs.
            buildRegions(server);
        } catch (IOException exception) {
            // Deliberately not a soft failure. Generating with a neutral height
            // field would write a wrong world to disk, and chunks already saved
            // cannot be regenerated without discarding player work.
            // Name both places they can live. A new world does not inherit the
            // last one's copy, so a server that ran yesterday can fail today
            // with the files still sitting where they always were.
            Path perWorld = TerrainConfig.perWorldDirectory(server);
            Path shared = TerrainConfig.sharedDirectory();
            throw new IllegalStateException("Failed to load terrain tiles from " + root.toAbsolutePath()
                    + ". They are read from " + perWorld.toAbsolutePath() + " when that exists, and"
                    + " otherwise from " + shared.toAbsolutePath() + "."
                    + " Unpack the tile archive into one of those so that a tile_0_0 directory"
                    + " holding tile.json ends up inside it, or disable the custom terrain.", exception);
        }
    }

    /**
     * Moves the world spawn to the middle of the main landmass.
     *
     * <p>Vanilla searches outwards from the origin, which on a tiled map lands
     * wherever the tile happens to have coastline. The generator records the
     * point of each landmass furthest from any water, and the largest one is
     * used here.
     *
     * <p>This also steers the random spawn system: it searches in a ring around
     * {@code getSharedSpawnPos}, so moving the shared spawn moves the whole ring
     * inland with it.
     */
    private static void onServerStarted(ServerStartedEvent event) {
        TerrainTileStore store = TerrainTileStore.active();
        if (store == null) {
            return;
        }
        TerrainTile.SpawnAnchor anchor = store.primarySpawnAnchor();
        if (anchor == null) {
            return;
        }
        ServerLevel overworld = event.getServer().getLevel(Level.OVERWORLD);
        if (overworld == null) {
            return;
        }
        BlockPos target = new BlockPos(anchor.x(), anchor.surfaceY() + 1, anchor.z());
        overworld.setDefaultSpawnPos(target, 0.0F);
        Moveearth_addtional.LOGGER.info(
                "World spawn moved to the middle of continent {} at {}, {} ({} blocks from the nearest coast)",
                anchor.continent(), anchor.x(), anchor.z(), Math.round(anchor.inlandBlocks()));
    }

    private static void onServerStopped(ServerStoppedEvent event) {
        TerrainTileStore.clear();
        Moveearth_addtional.LOGGER.info("Terrain tile store released");
    }
}
