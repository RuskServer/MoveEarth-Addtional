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
    private static final int PLAYER_PERMISSION_LEVEL = 0;
    private static final int ADMIN_PERMISSION_LEVEL = 2;

    private StarterKitCommand() {
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        register(event.getDispatcher());
    }

    private static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("starterkit")
                .requires(source -> source.hasPermission(PLAYER_PERMISSION_LEVEL))
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
            case GRANTED -> player.sendSystemMessage(Component.translatableWithFallback(
                    "message.moveearth_addtional.starterkit.granted",
                    "初心者キットを受け取りました。"));
            case ALREADY_RECEIVED -> player.sendSystemMessage(Component.translatableWithFallback(
                    "message.moveearth_addtional.starterkit.already_received",
                    "初心者キットはすでに受け取っています。"));
            case PLAY_TIME_EXCEEDED -> player.sendSystemMessage(Component.translatableWithFallback(
                    "message.moveearth_addtional.starterkit.play_time_exceeded",
                    "初心者キットはプレイ時間8時間未満のプレイヤーのみ受け取れます。"));
            case CONTENT_UNAVAILABLE -> player.sendSystemMessage(contentUnavailableMessage());
        }
        return result == BeginnerKitService.GrantResult.GRANTED ? 1 : 0;
    }

    private static int grant(CommandSourceStack source, ServerPlayer player) {
        BeginnerKitService.GrantResult result = BeginnerKitService.grant(
                player, true, "admin:" + source.getTextName());
        if (result != BeginnerKitService.GrantResult.GRANTED) {
            source.sendFailure(contentUnavailableMessage());
            return 0;
        }

        player.sendSystemMessage(Component.translatableWithFallback(
                "message.moveearth_addtional.starterkit.admin_granted_player",
                "管理者から初心者キットが支給されました。"));
        source.sendSuccess(() -> Component.translatableWithFallback(
                "message.moveearth_addtional.starterkit.admin_granted",
                "%sに初心者キットを支給しました。",
                player.getScoreboardName()), true);
        return 1;
    }

    private static int reset(CommandSourceStack source, ServerPlayer player) {
        BeginnerKitService.reset(player);
        source.sendSuccess(() -> Component.translatableWithFallback(
                "message.moveearth_addtional.starterkit.reset",
                "%sの初心者キット受取履歴をリセットしました。",
                player.getScoreboardName()), true);
        return 1;
    }

    private static int status(CommandSourceStack source, ServerPlayer player) {
        int playedMinutes = BeginnerKitService.playTimeTicks(player) / (20 * 60);
        source.sendSuccess(() -> Component.translatableWithFallback(
                "message.moveearth_addtional.starterkit.status",
                "%s: プレイ時間 %s時間%s分 / 受取 %s / 対象 %s",
                player.getScoreboardName(),
                playedMinutes / 60,
                playedMinutes % 60,
                BeginnerKitService.hasReceived(player)
                        ? Component.translatableWithFallback(
                                "message.moveearth_addtional.starterkit.status.received", "済み")
                        : Component.translatableWithFallback(
                                "message.moveearth_addtional.starterkit.status.not_received", "未受取"),
                BeginnerKitService.isEligible(player)
                        ? Component.translatableWithFallback(
                                "message.moveearth_addtional.starterkit.status.eligible", "対象")
                        : Component.translatableWithFallback(
                                "message.moveearth_addtional.starterkit.status.ineligible", "対象外")), false);
        return 1;
    }

    private static Component contentUnavailableMessage() {
        return Component.translatableWithFallback(
                "message.moveearth_addtional.starterkit.content_unavailable",
                "初心者キットを作成できません。CIBR GunPackの三八式歩兵銃が読み込まれているか確認してください。");
    }
}
