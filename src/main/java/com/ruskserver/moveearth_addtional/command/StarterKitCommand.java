package com.ruskserver.moveearth_addtional.command;

import com.mojang.brigadier.CommandDispatcher;
import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import com.ruskserver.moveearth_addtional.beginner.BeginnerKitService;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

@EventBusSubscriber(modid = Moveearth_addtional.MODID, bus = EventBusSubscriber.Bus.GAME)
public final class StarterKitCommand {
    private static final int ADMIN_PERMISSION_LEVEL = 2;

    private StarterKitCommand() {
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        register(event.getDispatcher());
    }

    private static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("starterkit")
                .executes(context -> claim(context.getSource().getPlayerOrException()))
                .then(Commands.literal("status")
                        .executes(context -> status(
                                context.getSource(), context.getSource().getPlayerOrException())))
                .then(Commands.literal("grant")
                        .requires(source -> source.hasPermission(ADMIN_PERMISSION_LEVEL))
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(context -> grant(
                                        context.getSource(), EntityArgument.getPlayer(context, "player")))))
                .then(Commands.literal("reset")
                        .requires(source -> source.hasPermission(ADMIN_PERMISSION_LEVEL))
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(context -> reset(
                                        context.getSource(), EntityArgument.getPlayer(context, "player")))))
                .then(Commands.literal("inspect")
                        .requires(source -> source.hasPermission(ADMIN_PERMISSION_LEVEL))
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(context -> status(
                                        context.getSource(), EntityArgument.getPlayer(context, "player"))))));
    }

    private static int claim(ServerPlayer player) {
        BeginnerKitService.GrantResult result = BeginnerKitService.grant(player, false, "self-command");
        switch (result) {
            case GRANTED -> player.sendSystemMessage(Component.translatable(
                    "message.moveearth_addtional.starterkit.granted"));
            case ALREADY_RECEIVED -> player.sendSystemMessage(Component.translatable(
                    "message.moveearth_addtional.starterkit.already_received"));
            case PLAY_TIME_EXCEEDED -> player.sendSystemMessage(Component.translatable(
                    "message.moveearth_addtional.starterkit.play_time_exceeded"));
            case CONTENT_UNAVAILABLE -> player.sendSystemMessage(Component.translatable(
                    "message.moveearth_addtional.starterkit.content_unavailable"));
        }
        return result == BeginnerKitService.GrantResult.GRANTED ? 1 : 0;
    }

    private static int grant(CommandSourceStack source, ServerPlayer player) {
        BeginnerKitService.GrantResult result = BeginnerKitService.grant(
                player, true, "admin:" + source.getTextName());
        if (result != BeginnerKitService.GrantResult.GRANTED) {
            source.sendFailure(Component.translatable(
                    "message.moveearth_addtional.starterkit.content_unavailable"));
            return 0;
        }

        player.sendSystemMessage(Component.translatable(
                "message.moveearth_addtional.starterkit.admin_granted_player"));
        source.sendSuccess(() -> Component.translatable(
                "message.moveearth_addtional.starterkit.admin_granted", player.getScoreboardName()), true);
        return 1;
    }

    private static int reset(CommandSourceStack source, ServerPlayer player) {
        BeginnerKitService.reset(player);
        source.sendSuccess(() -> Component.translatable(
                "message.moveearth_addtional.starterkit.reset", player.getScoreboardName()), true);
        return 1;
    }

    private static int status(CommandSourceStack source, ServerPlayer player) {
        int playedMinutes = BeginnerKitService.playTimeTicks(player) / (20 * 60);
        source.sendSuccess(() -> Component.translatable(
                "message.moveearth_addtional.starterkit.status",
                player.getScoreboardName(),
                playedMinutes / 60,
                playedMinutes % 60,
                BeginnerKitService.hasReceived(player)
                        ? Component.translatable("message.moveearth_addtional.starterkit.status.received")
                        : Component.translatable("message.moveearth_addtional.starterkit.status.not_received"),
                BeginnerKitService.isEligible(player)
                        ? Component.translatable("message.moveearth_addtional.starterkit.status.eligible")
                        : Component.translatable("message.moveearth_addtional.starterkit.status.ineligible")), false);
        return 1;
    }
}
