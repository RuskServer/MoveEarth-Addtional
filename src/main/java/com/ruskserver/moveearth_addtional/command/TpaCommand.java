package com.ruskserver.moveearth_addtional.command;

import com.mojang.brigadier.CommandDispatcher;
import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import com.ruskserver.moveearth_addtional.tpa.TpaRequestManager;
import com.ruskserver.moveearth_addtional.tpa.TpaUsageSavedData;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

@EventBusSubscriber(modid = Moveearth_addtional.MODID, bus = EventBusSubscriber.Bus.GAME)
public final class TpaCommand {
    private static final int PLAYER_PERMISSION_LEVEL = 0;
    private static final int ADMIN_PERMISSION_LEVEL = 2;

    private TpaCommand() {
    }

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();

        dispatcher.register(Commands.literal("tpa")
                .requires(source -> source.hasPermission(PLAYER_PERMISSION_LEVEL))
                .executes(context -> status(context.getSource().getPlayerOrException()))
                .then(Commands.literal("status")
                        .executes(context -> status(context.getSource().getPlayerOrException())))
                .then(Commands.literal("request")
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(context -> TpaRequestManager.INSTANCE.request(
                                        context.getSource().getPlayerOrException(),
                                        EntityArgument.getPlayer(context, "player")))))
                .then(Commands.literal("admin")
                        .requires(source -> source.hasPermission(ADMIN_PERMISSION_LEVEL))
                        .then(Commands.literal("inspect")
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(context -> inspect(context.getSource(),
                                                EntityArgument.getPlayer(context, "player")))))
                        .then(Commands.literal("reset")
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(context -> reset(context.getSource(),
                                                EntityArgument.getPlayer(context, "player"))))))
                .then(Commands.argument("player", EntityArgument.player())
                        .executes(context -> TpaRequestManager.INSTANCE.request(
                                context.getSource().getPlayerOrException(),
                                EntityArgument.getPlayer(context, "player")))));

        dispatcher.register(Commands.literal("tpaccept")
                .requires(source -> source.hasPermission(PLAYER_PERMISSION_LEVEL))
                .executes(context -> TpaRequestManager.INSTANCE.acceptOnly(
                        context.getSource().getPlayerOrException()))
                .then(Commands.argument("player", EntityArgument.player())
                        .executes(context -> TpaRequestManager.INSTANCE.accept(
                                context.getSource().getPlayerOrException(),
                                EntityArgument.getPlayer(context, "player")))));

        dispatcher.register(Commands.literal("tpdeny")
                .requires(source -> source.hasPermission(PLAYER_PERMISSION_LEVEL))
                .executes(context -> TpaRequestManager.INSTANCE.denyOnly(
                        context.getSource().getPlayerOrException()))
                .then(Commands.argument("player", EntityArgument.player())
                        .executes(context -> TpaRequestManager.INSTANCE.deny(
                                context.getSource().getPlayerOrException(),
                                EntityArgument.getPlayer(context, "player")))));

        dispatcher.register(Commands.literal("tpacancel")
                .requires(source -> source.hasPermission(PLAYER_PERMISSION_LEVEL))
                .executes(context -> TpaRequestManager.INSTANCE.cancel(
                        context.getSource().getPlayerOrException())));
    }

    private static int status(ServerPlayer player) {
        TpaRequestManager.INSTANCE.sendStatus(player);
        return 1;
    }

    private static int inspect(CommandSourceStack source, ServerPlayer player) {
        TpaUsageSavedData usage = TpaUsageSavedData.get(source.getServer());
        source.sendSuccess(() -> Component.literal(player.getScoreboardName()
                + " のTPA利用回数: " + usage.used(player.getUUID())
                + "/" + TpaUsageSavedData.DAILY_LIMIT), false);
        return 1;
    }

    private static int reset(CommandSourceStack source, ServerPlayer player) {
        TpaUsageSavedData.get(source.getServer()).reset(player.getUUID());
        source.sendSuccess(() -> Component.literal(player.getScoreboardName()
                + " のTPA利用回数をリセットしました。"), true);
        player.sendSystemMessage(Component.literal("管理者がTPA利用回数をリセットしました。"));
        return 1;
    }
}
