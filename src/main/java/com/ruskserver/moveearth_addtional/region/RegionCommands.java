package com.ruskserver.moveearth_addtional.region;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.ruskserver.moveearth_addtional.region.worldgen.RegionFeatureAudit;
import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import com.ruskserver.moveearth_addtional.compat.rns.RnsDepositDensity;
import com.ruskserver.moveearth_addtional.compat.rns.RnsDepositGate;
import com.ruskserver.moveearth_addtional.terrain.TerrainTile;
import com.ruskserver.moveearth_addtional.terrain.TerrainTileStore;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * Operator commands for the region resource system.
 *
 * <p>{@code probe} comes first and on purpose. The design's central bet is that
 * convention tags alone can name every ore in the pack, and that bet is only
 * worth making if it can be checked against the running server rather than
 * argued about. The dump goes to a file because the interesting part is the
 * list of things that failed to resolve, and chat truncates exactly the case
 * that matters.
 */
@EventBusSubscriber(modid = Moveearth_addtional.MODID)
public final class RegionCommands {

    private RegionCommands() { }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        register(event.getDispatcher());
    }

    private static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("moveearth")
                .then(Commands.literal("region")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.literal("probe")
                                .executes(context -> probe(context.getSource())))
                        .then(Commands.literal("export")
                                .executes(context -> export(context.getSource(), "region"))
                                .then(Commands.literal("continent")
                                        .executes(context -> export(context.getSource(), "continent"))))
                        .then(Commands.literal("features")
                                .executes(context -> features(context.getSource(), null))
                                .then(Commands.argument("biome", ResourceLocationArgument.id())
                                        .executes(context -> features(context.getSource(),
                                                ResourceLocationArgument.getId(context, "biome")))))
                        .then(Commands.literal("reallocate")
                                .executes(context -> reallocate(context.getSource())))
                        .then(Commands.literal("density")
                                .executes(context -> density(context.getSource(), null, null))
                                .then(Commands.argument("minChunk", IntegerArgumentType.integer())
                                        .then(Commands.argument("maxChunk", IntegerArgumentType.integer())
                                                .executes(context -> density(context.getSource(),
                                                        IntegerArgumentType.getInteger(context, "minChunk"),
                                                        IntegerArgumentType.getInteger(context, "maxChunk"))))))
                        .then(Commands.literal("info")
                                .executes(context -> infoHere(context.getSource()))
                                .then(Commands.argument("x", IntegerArgumentType.integer())
                                        .then(Commands.argument("z", IntegerArgumentType.integer())
                                                .executes(context -> info(
                                                        context.getSource(),
                                                        IntegerArgumentType.getInteger(context, "x"),
                                                        IntegerArgumentType.getInteger(context, "z"))))))));
    }

    private static int export(CommandSourceStack source, String layer) {
        TerrainTileStore store = TerrainTileStore.active();
        if (store == null || store.tiles().isEmpty()) {
            source.sendFailure(Component.literal("No terrain tiles are loaded."));
            return 0;
        }
        int written = 0;
        for (TerrainTile tile : store.tiles()) {
            if (!tile.has(layer)) {
                source.sendFailure(Component.literal(
                        tile.directory().getFileName() + " has no " + layer + " layer."));
                continue;
            }
            Path path = source.getServer().getServerDirectory().resolve(
                    "moveearth_" + layer + "_" + tile.directory().getFileName() + ".png");
            try {
                RegionExport.write(RegionResolver.cellsOf(tile, layer), path);
            } catch (IOException e) {
                source.sendFailure(Component.literal("Could not write " + path + ": " + e.getMessage()));
                continue;
            }
            written++;
            source.sendSuccess(() -> Component.literal("  " + tile.sizeCells() + "x" + tile.sizeCells()
                    + " cells -> " + path).withStyle(ChatFormatting.GREEN), false);
        }
        if (written > 0) {
            // The generator's own image uses a numpy palette that cannot be
            // reproduced here, so say plainly what a comparison is worth.
            source.sendSuccess(() -> Component.literal(
                    "  Compare the borders with tools/terrain output regions.png; the colours differ.")
                    .withStyle(ChatFormatting.GRAY), false);
        }
        return written;
    }

    /**
     * Throws away this world's allocation so the next start decides again.
     *
     * <p>Deliberately not applied on the spot. Chunks already generated were
     * built from the current allocation and would not be regenerated, so
     * swapping it live would leave a world holding two different answers at
     * once. Requiring a restart keeps the world consistent with whatever it
     * ends up with.
     */
    /**
     * Reports which ore features carry a region gate.
     *
     * <p>The one check the rest of the system cannot make for itself. Ore
     * generates either way; only this says whether it is obeying the region
     * rules while it does.
     */
    private static int features(CommandSourceStack source, ResourceLocation biome) {
        List<RegionFeatureAudit.Entry> entries = RegionFeatureAudit.run(source.getServer(), biome);
        if (entries.isEmpty()) {
            source.sendFailure(Component.literal(biome == null
                    ? "No biome carries an ore feature."
                    : "No such biome, or it has no ore features: " + biome));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("--- ore features "
                + (biome == null ? "(all biomes)" : "in " + biome) + " ---")
                .withStyle(ChatFormatting.AQUA), false);
        for (RegionFeatureAudit.Entry entry : entries) {
            // An ore left open is the interesting line, so it says why. A
            // feature that never was ore is expected to be open and stays quiet.
            boolean isOre = entry.suspectOre();
            String tail = entry.gated() ? "gated"
                    : entry.reason() == null ? "open"
                    : "open  -- " + entry.reason();
            source.sendSuccess(() -> Component.literal(String.format("  %-44s %-12s %s",
                    entry.featureId(), entry.material(), tail))
                    .withStyle(entry.gated() ? ChatFormatting.GREEN
                            : isOre ? ChatFormatting.YELLOW : ChatFormatting.GRAY), false);
        }
        source.sendSuccess(() -> Component.literal("  " + RegionFeatureAudit.summarise(entries)), false);
        // An ore feature nobody could name still generates everywhere. If the
        // material is one the regions are supposed to keep apart, it is not
        // being kept apart, and nothing else in the system will say so.
        long openOres = entries.stream()
                .filter(entry -> !entry.gated() && entry.suspectOre())
                .count();
        if (openOres > 0) {
            source.sendSuccess(() -> Component.literal("  " + openOres
                    + " ore feature(s) could not be named and generate in every region")
                    .withStyle(ChatFormatting.YELLOW), false);
        }
        // Gating a feature does nothing if the map it asks was not there yet.
        // That failure leaves no trace in the world -- ore generated without
        // rules looks exactly like ore generated under them -- so the only
        // place it can be seen is a count kept while it happened.
        long ungated = RegionProfiles.ungatedBeforeBuild();
        if (ungated > 0) {
            source.sendSuccess(() -> Component.literal("  " + ungated
                    + " ore placement(s) generated before the region map existed; "
                    + "those chunks do not obey the region rules")
                    .withStyle(ChatFormatting.RED), false);
        }
        // A named ore without a gate is the failure this command exists to find:
        // the biome modifier did not reach it, and that region rule is not being
        // enforced although everything else says it is.
        RegionFeatureAudit.missing(entries).ifPresent(gaps ->
                source.sendSuccess(() -> Component.literal(
                        "  NOT GATED although named: " + gaps).withStyle(ChatFormatting.RED), false));
        return entries.size();
    }

    private static int reallocate(CommandSourceStack source) {
        if (!RegionAllocationStore.clear(source.getServer())) {
            source.sendFailure(Component.literal(
                    "No allocation was recorded for this world, so there is nothing to discard."));
            return 0;
        }
        source.sendSuccess(() -> Component.literal(
                "Allocation discarded. The next start will decide again.")
                .withStyle(ChatFormatting.GREEN), false);
        source.sendSuccess(() -> Component.literal(
                "  Chunks already generated keep the resources they were given; only land "
                        + "generated from now on follows the new allocation.")
                .withStyle(ChatFormatting.YELLOW), false);
        return 1;
    }

    private static int density(CommandSourceStack source, Integer minChunk, Integer maxChunk) {
        if (!ModList.get().isLoaded(RegionEvents.RNS_MOD_ID)) {
            source.sendFailure(Component.literal(
                    "Create: Rock & Stone is not loaded, so there are no deposits to count."));
            return 0;
        }
        if (!RegionProfiles.ready()) {
            source.sendFailure(Component.literal(
                    "No region allocation, so nothing is gated and the counts would mean nothing."));
            return 0;
        }
        int[] range = minChunk != null && maxChunk != null
                ? new int[] { Math.min(minChunk, maxChunk), Math.max(minChunk, maxChunk) }
                : tileChunkRange();
        if (range == null) {
            source.sendFailure(Component.literal("No terrain tiles, so there is no area to count over."));
            return 0;
        }
        final int low = range[0];
        final int high = range[1];
        List<VeinDensity> measured = RnsDepositDensity.measure(source.getServer(), low, high);
        if (measured.isEmpty()) {
            source.sendFailure(Component.literal("No chunk in that range belonged to a region."));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("--- deposit density (chunks " + low + " to " + high
                + ") ---").withStyle(ChatFormatting.AQUA), false);
        double lowest = Double.MAX_VALUE;
        double highest = 0.0;
        for (VeinDensity row : measured) {
            lowest = Math.min(lowest, row.per1024Blocks());
            highest = Math.max(highest, row.per1024Blocks());
            source.sendSuccess(() -> Component.literal(String.format(
                    "  region %d: %.2f per 1024 blocks (%d kept, %d refused, %d chunks) %s",
                    row.regionId(), row.per1024Blocks(), row.veins(), row.blockedByGate(),
                    row.chunksSampled(), row.byMaterial())), false);
        }
        // The spread decides whether exclusivity has left some regions barren;
        // one region's figure on its own says nothing.
        final double spread = lowest <= 0 ? Double.POSITIVE_INFINITY : highest / lowest;
        source.sendSuccess(() -> Component.literal(Double.isInfinite(spread)
                ? "  richest / poorest = a region has no veins at all"
                : String.format("  richest / poorest = %.2fx", spread))
                .withStyle(spread <= 2.0 ? ChatFormatting.GREEN : ChatFormatting.YELLOW), false);
        return measured.size();
    }

    /**
     * The chunk range the loaded tiles cover.
     *
     * <p>Derived rather than guessed. The production tile starts at the world
     * origin and runs 8192 blocks out, so a range centred on 0 sees a quarter
     * of it and silently reports the other three quarters as empty.
     */
    private static int[] tileChunkRange() {
        TerrainTileStore store = TerrainTileStore.active();
        if (store == null || store.tiles().isEmpty()) {
            return null;
        }
        int low = Integer.MAX_VALUE;
        int high = Integer.MIN_VALUE;
        for (TerrainTile tile : store.tiles()) {
            int span = tile.sizeCells() * tile.blocksPerCell();
            low = Math.min(low, Math.min(tile.originX(), tile.originZ()) >> 4);
            high = Math.max(high, (Math.max(tile.originX(), tile.originZ()) + span - 1) >> 4);
        }
        return new int[] { low, high };
    }

    private static int infoHere(CommandSourceStack source) {
        var position = source.getPosition();
        return info(source, (int) Math.floor(position.x), (int) Math.floor(position.z));
    }

    private static int info(CommandSourceStack source, int x, int z) {
        if (!RegionResolver.ready()) {
            source.sendFailure(Component.literal(
                    "No terrain tiles are loaded, so every position has no region."));
            return 0;
        }
        int region = RegionResolver.regionAt(x, z);
        int continent = RegionResolver.continentAt(x, z);
        source.sendSuccess(() -> Component.literal("--- region at " + x + ", " + z + " ---")
                .withStyle(ChatFormatting.AQUA), false);
        source.sendSuccess(() -> Component.literal(region == RegionGrid.NONE
                ? "  region: none -- open ocean, nothing is gated here"
                : "  region: " + region), false);
        // Sea has a region (the nearest coast's) but belongs to no continent, so
        // printing both is what makes an unexpected answer readable.
        source.sendSuccess(() -> Component.literal(continent == RegionGrid.NONE
                ? "  continent: none (sea)"
                : "  continent: " + continent), false);
        return 1;
    }

    private static int probe(CommandSourceStack source) {
        MinecraftServer server = source.getServer();
        RegionProbe.Report report = RegionProbe.run(server);

        List<String> lines = format(report);
        Path path = server.getServerDirectory()
                .resolve("moveearth_region_probe-" + Util.getFilenameFormattedDateTime() + ".txt");
        try {
            Files.write(path, lines, StandardCharsets.UTF_8);
        } catch (IOException e) {
            source.sendFailure(Component.literal("Could not write the probe dump: " + e.getMessage()));
            return 0;
        }

        long unresolved = report.unresolvedCount();
        source.sendSuccess(() -> Component.literal("--- region probe ---")
                .withStyle(ChatFormatting.AQUA), false);
        source.sendSuccess(() -> Component.literal(RegionProfiles.ready()
                ? "  allocation: " + RegionProfiles.assignments().size() + " region(s)"
                : "  allocation: NOT BUILT -- nothing is gated")
                .withStyle(RegionProfiles.ready() ? ChatFormatting.GREEN : ChatFormatting.RED), false);
        if (ModList.get().isLoaded(RegionEvents.RNS_MOD_ID)) {
            // "Nothing was blocked" and "the hook never attached" look the same
            // from outside, and the injection is deliberately optional, so the
            // difference has to be reported rather than inferred.
            source.sendSuccess(() -> Component.literal(RnsDepositGate.consulted()
                    ? "  deposit gate: attached, " + RnsDepositGate.namedDepositCount()
                            + " deposit(s) named, " + RnsDepositGate.yieldedToOtherSets()
                            + " of ours stood aside to avoid overlapping"
                    : "  deposit gate: NEVER CONSULTED -- no chunk has generated yet, or the "
                            + "injection did not attach")
                    .withStyle(RnsDepositGate.consulted() ? ChatFormatting.GREEN : ChatFormatting.YELLOW),
                    false);
        }
        report.tiles().forEach(tile -> source.sendSuccess(() -> Component.literal(
                "  tile " + tile.directory() + ": "
                        + (tile.regionCells().size() - (tile.regionCells().containsKey(0) ? 1 : 0))
                        + " regions, "
                        + (tile.continentCells().size() - (tile.continentCells().containsKey(0) ? 1 : 0))
                        + " continents, " + tile.blocksPerCell() + " blocks/cell"), false));
        if (report.tiles().isEmpty()) {
            source.sendSuccess(() -> Component.literal("  tiles: none loaded")
                    .withStyle(ChatFormatting.RED), false);
        }
        source.sendSuccess(() -> Component.literal("  ore features: " + report.oreFeatures().size()
                + ", named: " + report.resolvedCount()
                + ", unnamed: " + unresolved
                + ", conflicting tags: " + report.ambiguousCount()), false);
        source.sendSuccess(() -> Component.literal("  c:ores/* tags: " + report.oreTags().size()), false);
        source.sendSuccess(() -> Component.literal("  written to " + path)
                .withStyle(unresolved == 0 ? ChatFormatting.GREEN : ChatFormatting.YELLOW), false);
        return 1;
    }

    private static List<String> format(RegionProbe.Report report) {
        List<String> lines = new ArrayList<>();
        lines.add("# MoveEarth region probe");
        lines.add("# Facts read from the running server. Nothing here is configuration.");
        lines.add("");

        lines.add("## terrain tiles in play (" + report.tiles().size() + ")");
        if (report.tiles().isEmpty()) {
            lines.add("  none loaded -- every position has no region");
        }
        for (RegionProbe.TileFacts tile : report.tiles()) {
            lines.add("  " + tile.directory() + "  origin " + tile.originX() + "," + tile.originZ()
                    + "  " + tile.sizeCells() + "x" + tile.sizeCells() + " cells of "
                    + tile.blocksPerCell() + " blocks = "
                    + (tile.sizeCells() * tile.blocksPerCell()) + " blocks square");
            lines.add("      regions   (id=cells): " + tile.regionCells());
            lines.add("      continents(id=cells): " + tile.continentCells());
        }
        lines.add("");

        lines.add("## ore features in UNDERGROUND_ORES (" + report.oreFeatures().size() + ")");
        for (RegionProbe.OreFeature feature : report.oreFeatures()) {
            String material = feature.resolution().material()
                    .orElse("UNNAMED -- the gate must allow this everywhere");
            lines.add("  " + feature.featureId() + "  -> " + material);
            lines.add("      blocks: " + String.join(", ", feature.blocks()));
            if (feature.resolution().material().isEmpty()) {
                lines.add("      c: tags: " + (feature.conventionTags().isEmpty()
                        ? "(none -- nothing to go on)"
                        : String.join(", ", feature.conventionTags())));
            }
            if (feature.resolution().ambiguous()) {
                lines.add("      CONFLICT: tags also suggest "
                        + String.join(", ", feature.resolution().candidates()));
            }
        }
        lines.add("");

        lines.add("## c:ores/* tags (" + report.oreTags().size() + ")");
        report.oreTags().forEach((tag, members) -> {
            lines.add("  " + tag);
            members.forEach(m -> lines.add("      " + m));
        });
        lines.add("");

        Map<String, String> deposits = RnsDepositGate.resolutionReport();
        if (deposits.isEmpty()) {
            lines.add("## Create: Rock & Stone: no deposits resolved (mod absent, or not yet built)");
            return lines;
        }
        lines.add("## Create: Rock & Stone deposits (" + deposits.size() + ")");
        deposits.forEach((structure, material) -> lines.add("  " + structure + "  -> " + material));
        return lines;
    }
}
