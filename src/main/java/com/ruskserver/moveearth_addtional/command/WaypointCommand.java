package com.ruskserver.moveearth_addtional.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import com.ruskserver.moveearth_addtional.economy.EconomyWaypointSync;
import com.ruskserver.moveearth_addtional.economy.WaypointSavedData;
import com.ruskserver.moveearth_addtional.ui.MoveEarthMessage;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/** Universal coordinate waypoint; market stations use the same saved target and HUD. */
@EventBusSubscriber(modid = Moveearth_addtional.MODID, bus = EventBusSubscriber.Bus.GAME)
public final class WaypointCommand {
    private WaypointCommand() { }

    @SubscribeEvent public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("waypoint").requires(CommandSourceStack::isPlayer)
                .executes(context -> status(context.getSource()))
                .then(Commands.literal("clear").executes(context -> {
                    var player = context.getSource().getPlayerOrException();
                    EconomyWaypointSync.clearWaypoint(player);
                    context.getSource().sendSuccess(() -> MoveEarthMessage.success("ウェイポイントを解除しました"), false);
                    return 1;
                }))
                .then(Commands.literal("set")
                        .then(Commands.argument("x", IntegerArgumentType.integer(-29_999_984, 29_999_984))
                                .then(Commands.argument("y", IntegerArgumentType.integer())
                                        .then(Commands.argument("z", IntegerArgumentType.integer(-29_999_984, 29_999_984))
                                                .executes(context -> set(context.getSource(), "目的地",
                                                        IntegerArgumentType.getInteger(context, "x"),
                                                        IntegerArgumentType.getInteger(context, "y"),
                                                        IntegerArgumentType.getInteger(context, "z")))
                                                .then(Commands.argument("name", StringArgumentType.greedyString())
                                                        .executes(context -> set(context.getSource(),
                                                                StringArgumentType.getString(context, "name"),
                                                                IntegerArgumentType.getInteger(context, "x"),
                                                                IntegerArgumentType.getInteger(context, "y"),
                                                                IntegerArgumentType.getInteger(context, "z")))))))));
    }

    private static int set(CommandSourceStack source, String name, int x, int y, int z)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        var player = source.getPlayerOrException();
        BlockPos pos = new BlockPos(x, y, z);
        if (player.level().isOutsideBuildHeight(pos) || !player.level().getWorldBorder().isWithinBounds(pos)) {
            source.sendFailure(MoveEarthMessage.error("目的地はワールドの範囲内を指定してください"));
            return 0;
        }
        EconomyWaypointSync.setWaypoint(player, new WaypointSavedData.Waypoint(
                name, player.level().dimension().location(), pos));
        source.sendSuccess(() -> MoveEarthMessage.success("ウェイポイント: " + name + " (" + pos.toShortString() + ")"), false);
        return 1;
    }

    private static int status(CommandSourceStack source)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        var player = source.getPlayerOrException();
        var waypoint = WaypointSavedData.get(player.server).active(player.getUUID()).orElse(null);
        source.sendSuccess(() -> MoveEarthMessage.info(waypoint == null ? "目的地は未設定です"
                : "目的地: " + waypoint.name() + " / " + waypoint.dimension() + " "
                + waypoint.pos().toShortString()), false);
        return 1;
    }
}
