package com.ruskserver.moveearth_addtional.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import com.ruskserver.moveearth_addtional.terrain.TerrainField;
import com.ruskserver.moveearth_addtional.terrain.TerrainTile;
import com.ruskserver.moveearth_addtional.terrain.TerrainTileStore;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * Diagnostics for the precomputed terrain.
 *
 * <p>The question that decides whether the terrain pipeline is wired correctly
 * is whether the ground actually lands where the tile says it should. This
 * reports both numbers and their difference, so the check can be made on a live
 * server without reading logs.
 */
@EventBusSubscriber(modid = Moveearth_addtional.MODID)
public final class TerrainCommand {
    private TerrainCommand() { }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        register(event.getDispatcher());
    }

    private static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("moveearth")
                .then(Commands.literal("terrain")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.literal("probe")
                                .executes(context -> probeHere(context.getSource()))
                                .then(Commands.argument("x", IntegerArgumentType.integer())
                                        .then(Commands.argument("z", IntegerArgumentType.integer())
                                                .executes(context -> probe(
                                                        context.getSource(),
                                                        IntegerArgumentType.getInteger(context, "x"),
                                                        IntegerArgumentType.getInteger(context, "z"))))))
                        .then(Commands.literal("status")
                                .executes(context -> status(context.getSource())))));
    }

    private static int status(CommandSourceStack source) {
        TerrainTileStore store = TerrainTileStore.active();
        if (store == null) {
            source.sendFailure(Component.literal("No terrain tiles are loaded. Vanilla worldgen is in effect."));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("Terrain tiles loaded: " + store.tileCount()
                + ", sea level Y=" + store.seaY()).withStyle(ChatFormatting.GREEN), false);
        return 1;
    }

    private static int probeHere(CommandSourceStack source) {
        var position = source.getPosition();
        return probe(source, (int) Math.floor(position.x), (int) Math.floor(position.z));
    }

    private static int probe(CommandSourceStack source, int x, int z) {
        TerrainTileStore store = TerrainTileStore.active();
        if (store == null) {
            source.sendFailure(Component.literal("No terrain tiles are loaded."));
            return 0;
        }
        TerrainTile tile = store.tileAt(x, z);
        ServerLevel level = source.getLevel();

        source.sendSuccess(() -> Component.literal("--- terrain probe at " + x + ", " + z + " ---")
                .withStyle(ChatFormatting.AQUA), false);
        if (tile == null) {
            source.sendSuccess(() -> Component.literal("  outside every tile: open ocean")
                    .withStyle(ChatFormatting.GRAY), false);
            return 1;
        }

        double rawHeight = tile.sample("height", x, z);
        // the world has the river channels cut into it, so the prediction has to
        // use the carved surface or every channel reads as a mismatch
        double carvedHeight = tile.carvedSurface(x, z);
        double offset = TerrainField.OFFSET.convert(carvedHeight, tile);
        // vanilla depth is y_clamped_gradient(1.5 at -64 .. -1.5 at 320) + offset,
        // so the surface should land near this height
        double predicted = 128.0 + 128.0 * offset;
        int actual = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1;
        double delta = actual - predicted;

        line(source, "tile", tile.directory().getFileName().toString()
                + "  (" + tile.blocksPerCell() + " blocks/cell)");
        line(source, "height layer", String.format("Y=%.1f", rawHeight));
        if (tile.has("river_width")) {
            double width = tile.sample("river_width", x, z);
            double distance = tile.sample("river_dist", x, z);
            if (width > 0.0 && rawHeight - carvedHeight > 0.01) {
                line(source, "river channel", String.format(
                        "width %.0f, %.0f blocks from centre, cut %.1f",
                        width, distance, rawHeight - carvedHeight));
            }
            int waterY = tile.riverWaterLevel(x, z);
            if (waterY != Integer.MIN_VALUE) {
                // vanilla only floods to sea level; a raised table here means the
                // fluid picker override is doing its job
                line(source, "river water", "surface Y=" + waterY
                        + (waterY > store.seaY() ? "  (above sea level)" : "  (at sea level)"));
            }
        }
        line(source, "offset", String.format("%.4f  -> predicted surface Y=%.1f", offset, predicted));
        line(source, "actual surface", "Y=" + actual);
        source.sendSuccess(() -> Component.literal(String.format("  %-16s %+.1f blocks", "difference", delta))
                .withStyle(Math.abs(delta) <= 8.0 ? ChatFormatting.GREEN : ChatFormatting.RED), false);

        for (TerrainField field : new TerrainField[] {
                TerrainField.CONTINENTS, TerrainField.EROSION, TerrainField.TEMPERATURE,
                TerrainField.VEGETATION, TerrainField.RIVER_GATE}) {
            if (tile.has(field.layer())) {
                double raw = tile.sample(field.layer(), x, z);
                line(source, field.serializedName(),
                        String.format("%.3f  (raw %.1f)", field.convert(raw, tile), raw));
            }
        }
        if (tile.has("region")) {
            line(source, "region", String.format("%.0f", tile.sample("region", x, z)));
        }
        if (tile.has("continent")) {
            line(source, "continent", String.format("%.0f", tile.sample("continent", x, z)));
        }
        return 1;
    }

    private static void line(CommandSourceStack source, String label, String value) {
        source.sendSuccess(() -> Component.literal(String.format("  %-16s %s", label, value))
                .withStyle(ChatFormatting.GRAY), false);
    }
}
