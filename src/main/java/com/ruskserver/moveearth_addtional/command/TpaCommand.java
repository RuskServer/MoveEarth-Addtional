package com.ruskserver.moveearth_addtional.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import com.ruskserver.moveearth_addtional.tpa.TpaRequestManager;
import com.ruskserver.moveearth_addtional.tpa.TpaUsageSavedData;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

@EventBusSubscriber(modid = Moveearth_addtional.MODID, bus = EventBusSubscriber.Bus.GAME)
public final class TpaCommand {
    private static final int ADMIN_PERMISSION_LEVEL = 2;

    private TpaCommand() {
    }

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        register(event.getDispatcher());
    }

    private static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(tpaRoot("tpa"));
        dispatcher.register(tpaRoot("moveearthtpa"));
        dispatcher.register(acceptCommand("tpaccept"));
        dispatcher.register(denyCommand("tpdeny"));
        dispatcher.register(cancelCommand("tpcancel"));
        dispatcher.register(cancelCommand("tpacancel"));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> tpaRoot(String name) {
        return Commands.literal(name)
                .requires(source -> source.hasPermission(0))
                .executes(context -> status(context.getSource().getPlayerOrException()))
                .then(Commands.literal("status")
                        .requires(source -> source.hasPermission(0))
                        .executes(context -> status(context.getSource().getPlayerOrException())))
                .then(Commands.literal("request")
                        .requires(source -> source.hasPermission(0))
                        .then(playerNameArgument()
                                .executes(context -> request(context.getSource(),
                                        StringArgumentType.getString(context, "player")))))
                .then(acceptCommand("accept"))
                .then(denyCommand("deny"))
                .then(cancelCommand("cancel"))
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
                .then(playerNameArgument()
                        .executes(context -> request(context.getSource(),
                                StringArgumentType.getString(context, "player"))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> acceptCommand(String name) {
        return Commands.literal(name)
                .requires(source -> source.hasPermission(0))
                .executes(context -> TpaRequestManager.INSTANCE.acceptOnly(
                        context.getSource().getPlayerOrException()))
                .then(playerNameArgument()
                        .executes(context -> accept(context.getSource(),
                                StringArgumentType.getString(context, "player"))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> denyCommand(String name) {
        return Commands.literal(name)
                .requires(source -> source.hasPermission(0))
                .executes(context -> TpaRequestManager.INSTANCE.denyOnly(
                        context.getSource().getPlayerOrException()))
                .then(playerNameArgument()
                        .executes(context -> deny(context.getSource(),
                                StringArgumentType.getString(context, "player"))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> cancelCommand(String name) {
        return Commands.literal(name)
                .requires(source -> source.hasPermission(0))
                .executes(context -> TpaRequestManager.INSTANCE.cancel(
                        context.getSource().getPlayerOrException()));
    }

    private static RequiredArgumentBuilder<CommandSourceStack, String> playerNameArgument() {
        return Commands.argument("player", StringArgumentType.word())
                .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                        context.getSource().getOnlinePlayerNames(), builder));
    }

    private static int request(CommandSourceStack source, String playerName) throws CommandSyntaxException {
        ServerPlayer target = onlinePlayer(source, playerName);
        return target == null ? 0 : TpaRequestManager.INSTANCE.request(source.getPlayerOrException(), target);
    }

    private static int accept(CommandSourceStack source, String playerName) throws CommandSyntaxException {
        ServerPlayer requester = onlinePlayer(source, playerName);
        return requester == null ? 0 : TpaRequestManager.INSTANCE.accept(source.getPlayerOrException(), requester);
    }

    private static int deny(CommandSourceStack source, String playerName) throws CommandSyntaxException {
        ServerPlayer requester = onlinePlayer(source, playerName);
        return requester == null ? 0 : TpaRequestManager.INSTANCE.deny(source.getPlayerOrException(), requester);
    }

    private static ServerPlayer onlinePlayer(CommandSourceStack source, String playerName) {
        ServerPlayer player = source.getServer().getPlayerList().getPlayerByName(playerName);
        if (player == null) {
            source.sendFailure(Component.literal("オンラインのプレイヤーが見つかりません: " + playerName));
        }
        return player;
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
