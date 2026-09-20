package com.ruskserver.moveearth_addtional.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import com.ruskserver.moveearth_addtional.handler.RandomSpawnMappingService;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

@EventBusSubscriber(modid = Moveearth_addtional.MODID, bus = EventBusSubscriber.Bus.GAME)
public final class SpawnPoolCommand {
    private SpawnPoolCommand() { }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        register(event.getDispatcher());
    }

    private static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("spawnpool")
                .requires(source -> source.hasPermission(2))
                .executes(context -> showStatus(context.getSource()))
                .then(Commands.literal("status")
                        .executes(context -> showStatus(context.getSource())))
                .then(Commands.literal("prune")
                        .executes(context -> reply(context.getSource(),
                                RandomSpawnMappingService.prune(context.getSource().getServer()))))
                .then(Commands.literal("map")
                        .then(Commands.literal("start")
                                .executes(context -> start(context.getSource(), false,
                                        RandomSpawnMappingService.recommendedTarget(
                                                context.getSource().getServer())))
                                .then(Commands.argument("target", IntegerArgumentType.integer(32, 8192))
                                        .executes(context -> start(context.getSource(), false,
                                                IntegerArgumentType.getInteger(context, "target")))))
                        .then(Commands.literal("generate")
                                .executes(context -> start(context.getSource(), true,
                                        RandomSpawnMappingService.recommendedTarget(
                                                context.getSource().getServer())))
                                .then(Commands.argument("target", IntegerArgumentType.integer(32, 8192))
                                        .executes(context -> start(context.getSource(), true,
                                                IntegerArgumentType.getInteger(context, "target")))))
                        .then(Commands.literal("pause")
                                .executes(context -> reply(context.getSource(),
                                        RandomSpawnMappingService.pause(context.getSource().getServer()))))
                        .then(Commands.literal("resume")
                                .executes(context -> reply(context.getSource(),
                                        RandomSpawnMappingService.resume(context.getSource().getServer()))))
                        .then(Commands.literal("stop")
                                .executes(context -> reply(context.getSource(),
                                        RandomSpawnMappingService.stop(context.getSource().getServer()))))));
    }

    private static int start(CommandSourceStack source, boolean generate, int target) {
        RandomSpawnMappingService.Result result = RandomSpawnMappingService.start(
                source.getServer(), generate, target);
        if (generate && result.success()) {
            source.sendSuccess(() -> Component.literal(
                    "注意: このモードは未生成チャンクを1件ずつ生成します。必要時以外は map start を使用してください。")
                    .withStyle(ChatFormatting.YELLOW), true);
        }
        return reply(source, result);
    }

    private static int showStatus(CommandSourceStack source) {
        RandomSpawnMappingService.Status status = RandomSpawnMappingService.status(source.getServer());
        String mapping;
        if (!status.active()) {
            mapping = "停止中";
        } else {
            mapping = (status.paused() ? "一時停止"
                    : status.waitingForEmptyServer() ? "プレイヤー退出待ち" : "実行中")
                    + " / " + (status.generate() ? "新規生成あり" : "生成済み限定")
                    + " / 目標 " + status.target()
                    + " / 検査 " + status.checked()
                    + " / 追加 " + status.accepted();
        }
        source.sendSuccess(() -> Component.literal(
                "ランダムスポーンプール: " + status.poolSize() + "地点"
                        + " / 推奨 " + status.recommendedTarget() + "地点 / マッピング: " + mapping)
                .withStyle(ChatFormatting.AQUA), false);
        return 1;
    }

    private static int reply(CommandSourceStack source, RandomSpawnMappingService.Result result) {
        if (result.success()) {
            source.sendSuccess(() -> Component.literal(result.message()).withStyle(ChatFormatting.GREEN), true);
            return 1;
        }
        source.sendFailure(Component.literal(result.message()));
        return 0;
    }
}
