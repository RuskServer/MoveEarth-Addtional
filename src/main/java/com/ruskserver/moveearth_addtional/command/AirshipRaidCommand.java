package com.ruskserver.moveearth_addtional.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import com.ruskserver.moveearth_addtional.raid.AirshipRaidDifficulty;
import com.ruskserver.moveearth_addtional.raid.AirshipRaidInstance;
import com.ruskserver.moveearth_addtional.raid.AirshipRaidManager;
import com.ruskserver.moveearth_addtional.raid.AirshipRaidSavedData;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.Collection;

@EventBusSubscriber(modid = Moveearth_addtional.MODID)
public final class AirshipRaidCommand {
    private AirshipRaidCommand() {
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        register(event.getDispatcher());
    }

    private static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("airshipraid")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("auto")
                        .then(Commands.literal("on").executes(context -> setAutomatic(context.getSource(), true)))
                        .then(Commands.literal("off").executes(context -> setAutomatic(context.getSource(), false)))
                        .then(Commands.literal("status").executes(context -> automaticStatus(context.getSource()))))
                .then(Commands.literal("start")
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(context -> start(context.getSource(), EntityArgument.getPlayer(context, "player"),
                                        AirshipRaidDifficulty.NORMAL))
                                .then(Commands.argument("difficulty", StringArgumentType.word())
                                        .suggests((context, builder) -> {
                                            builder.suggest("normal");
                                            builder.suggest("elite");
                                            builder.suggest("large");
                                            return builder.buildFuture();
                                        })
                                        .executes(context -> start(context.getSource(),
                                                EntityArgument.getPlayer(context, "player"),
                                                AirshipRaidDifficulty.parse(StringArgumentType.getString(context, "difficulty")))))))
                .then(Commands.literal("stop")
                        .then(Commands.literal("all").executes(context -> stopAll(context.getSource())))
                        .then(Commands.argument("raidId", IntegerArgumentType.integer(1))
                                .executes(context -> stop(context.getSource(), IntegerArgumentType.getInteger(context, "raidId")))))
                .then(Commands.literal("list").executes(context -> list(context.getSource())))
                .then(Commands.literal("status")
                        .then(Commands.argument("raidId", IntegerArgumentType.integer(1))
                                .executes(context -> status(context.getSource(), IntegerArgumentType.getInteger(context, "raidId"))))));
    }

    private static int setAutomatic(CommandSourceStack source, boolean enabled) {
        AirshipRaidSavedData.get(source.getServer()).setAutomaticEnabled(enabled);
        source.sendSuccess(() -> Component.literal("飛行船の自動襲撃を" + (enabled ? "有効" : "無効") + "にしました。"), true);
        return 1;
    }

    private static int automaticStatus(CommandSourceStack source) {
        MinecraftServer server = source.getServer();
        AirshipRaidSavedData data = AirshipRaidSavedData.get(server);
        long elapsed = server.overworld().getGameTime() - data.getLastAutomaticCheck();
        long remainingTicks = Math.max(0, AirshipRaidManager.AUTOMATIC_CHECK_INTERVAL - elapsed);
        source.sendSuccess(() -> Component.literal("自動襲撃: " + (data.isAutomaticEnabled() ? "ON" : "OFF")
                + " / 次回抽選まで約" + (remainingTicks / 1200L) + "分"
                + " / 進行中: " + AirshipRaidManager.activeRaids().size()), false);
        return data.isAutomaticEnabled() ? 1 : 0;
    }

    private static int start(CommandSourceStack source, ServerPlayer target, AirshipRaidDifficulty difficulty) {
        AirshipRaidInstance raid = AirshipRaidManager.start(source.getServer(), target, difficulty, false);
        source.sendSuccess(() -> Component.literal("襲撃 #" + raid.id() + " を開始しました。"), true);
        return raid.id();
    }

    private static int stop(CommandSourceStack source, int id) {
        if (!AirshipRaidManager.stop(source.getServer(), id)) {
            source.sendFailure(Component.literal("襲撃 #" + id + " は存在しません。"));
            return 0;
        }
        return 1;
    }

    private static int stopAll(CommandSourceStack source) {
        int count = AirshipRaidManager.stopAll(source.getServer());
        source.sendSuccess(() -> Component.literal(count + "件の襲撃を停止しました。"), true);
        return count;
    }

    private static int list(CommandSourceStack source) {
        Collection<AirshipRaidInstance> raids = AirshipRaidManager.activeRaids();
        if (raids.isEmpty()) {
            source.sendSuccess(() -> Component.literal("進行中の飛行船襲撃はありません。"), false);
            return 0;
        }
        for (AirshipRaidInstance raid : raids) {
            source.sendSuccess(() -> describe(raid), false);
        }
        return raids.size();
    }

    private static int status(CommandSourceStack source, int id) {
        AirshipRaidInstance raid = AirshipRaidManager.getRaid(id).orElse(null);
        if (raid == null) {
            source.sendFailure(Component.literal("襲撃 #" + id + " は存在しません。"));
            return 0;
        }
        source.sendSuccess(() -> describe(raid), false);
        return 1;
    }

    private static Component describe(AirshipRaidInstance raid) {
        return Component.literal("#" + raid.id() + " 対象=" + raid.targetName()
                + " 難易度=" + raid.difficulty().name().toLowerCase()
                + " フェーズ=" + raid.phase().name().toLowerCase());
    }
}
