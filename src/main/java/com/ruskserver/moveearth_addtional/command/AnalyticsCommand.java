package com.ruskserver.moveearth_addtional.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import com.ruskserver.moveearth_addtional.analytics.config.AnalyticsConfig;
import com.ruskserver.moveearth_addtional.analytics.query.AnalyticsQueryService;
import com.ruskserver.moveearth_addtional.analytics.query.dto.TimeWindow;
import com.ruskserver.moveearth_addtional.analytics.query.export.AnalyticsExportService;
import com.ruskserver.moveearth_addtional.analytics.query.format.AnalyticsTextFormatter;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.UUID;

/**
 * プレイヤー分析システムのOP管理コマンド (/analytics)
 */
@EventBusSubscriber(modid = Moveearth_addtional.MODID, bus = EventBusSubscriber.Bus.GAME)
public final class AnalyticsCommand {

    private static final int ADMIN_PERMISSION_LEVEL = 2;

    private AnalyticsCommand() {
    }

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();

        dispatcher.register(Commands.literal("analytics")
                .requires(source -> source.hasPermission(ADMIN_PERMISSION_LEVEL))
                // /analytics web
                .then(Commands.literal("web")
                        .executes(ctx -> showWebUrl(ctx.getSource())))
                // /analytics health
                .then(Commands.literal("health")
                        .executes(ctx -> showHealth(ctx.getSource())))
                // /analytics player <target> [window]
                .then(Commands.literal("player")
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(ctx -> showPlayer(ctx.getSource(), EntityArgument.getPlayer(ctx, "player").getUUID(), TimeWindow.DAYS_7))
                                .then(Commands.argument("window", StringArgumentType.word())
                                        .suggests((c, b) -> SharedSuggestionProvider.suggest(new String[]{"7d", "30d", "all"}, b))
                                        .executes(ctx -> showPlayer(ctx.getSource(), EntityArgument.getPlayer(ctx, "player").getUUID(), parseWindow(StringArgumentType.getString(ctx, "window")))))))
                // /analytics ranking [window] [limit]
                .then(Commands.literal("ranking")
                        .executes(ctx -> showRanking(ctx.getSource(), TimeWindow.DAYS_7, 5))
                        .then(Commands.argument("window", StringArgumentType.word())
                                .suggests((c, b) -> SharedSuggestionProvider.suggest(new String[]{"7d", "30d", "all"}, b))
                                .executes(ctx -> showRanking(ctx.getSource(), parseWindow(StringArgumentType.getString(ctx, "window")), 5))
                                .then(Commands.argument("limit", IntegerArgumentType.integer(1, 20))
                                        .executes(ctx -> showRanking(ctx.getSource(), parseWindow(StringArgumentType.getString(ctx, "window")), IntegerArgumentType.getInteger(ctx, "limit"))))))
                // /analytics group <target_owner> [window]
                .then(Commands.literal("group")
                        .then(Commands.argument("owner", EntityArgument.player())
                                .executes(ctx -> showGroup(ctx.getSource(), EntityArgument.getPlayer(ctx, "owner").getUUID(), TimeWindow.DAYS_7))
                                .then(Commands.argument("window", StringArgumentType.word())
                                        .suggests((c, b) -> SharedSuggestionProvider.suggest(new String[]{"7d", "30d", "all"}, b))
                                        .executes(ctx -> showGroup(ctx.getSource(), EntityArgument.getPlayer(ctx, "owner").getUUID(), parseWindow(StringArgumentType.getString(ctx, "window")))))))
                // /analytics heatmap [dimension] [window] [limit]
                .then(Commands.literal("heatmap")
                        .executes(ctx -> showHeatmap(ctx.getSource(), "minecraft:overworld", TimeWindow.DAYS_7, 10))
                        .then(Commands.argument("dimension", StringArgumentType.string())
                                .suggests((c, b) -> SharedSuggestionProvider.suggest(new String[]{"minecraft:overworld", "minecraft:the_nether", "minecraft:the_end", "pvp_arena"}, b))
                                .executes(ctx -> showHeatmap(ctx.getSource(), StringArgumentType.getString(ctx, "dimension"), TimeWindow.DAYS_7, 10))
                                .then(Commands.argument("window", StringArgumentType.word())
                                        .suggests((c, b) -> SharedSuggestionProvider.suggest(new String[]{"7d", "30d", "all"}, b))
                                        .executes(ctx -> showHeatmap(ctx.getSource(), StringArgumentType.getString(ctx, "dimension"), parseWindow(StringArgumentType.getString(ctx, "window")), 10))
                                        .then(Commands.argument("limit", IntegerArgumentType.integer(1, 30))
                                                .executes(ctx -> showHeatmap(ctx.getSource(), StringArgumentType.getString(ctx, "dimension"), parseWindow(StringArgumentType.getString(ctx, "window")), IntegerArgumentType.getInteger(ctx, "limit")))))))
                // /analytics export <format> [window]
                .then(Commands.literal("export")
                        .then(Commands.argument("format", StringArgumentType.word())
                                .suggests((c, b) -> SharedSuggestionProvider.suggest(new String[]{"csv", "jsonl"}, b))
                                .executes(ctx -> exportData(ctx.getSource(), StringArgumentType.getString(ctx, "format"), TimeWindow.DAYS_7))
                                .then(Commands.argument("window", StringArgumentType.word())
                                        .suggests((c, b) -> SharedSuggestionProvider.suggest(new String[]{"7d", "30d", "all"}, b))
                                        .executes(ctx -> exportData(ctx.getSource(), StringArgumentType.getString(ctx, "format"), parseWindow(StringArgumentType.getString(ctx, "window")))))))
        );
    }

    private static int showWebUrl(CommandSourceStack source) {
        int port = AnalyticsConfig.WEB_SERVER_PORT;
        String url = "http://localhost:" + port;

        Component linkComponent = Component.literal(url)
                .withStyle(ChatFormatting.AQUA, ChatFormatting.UNDERLINE)
                .withStyle(style -> style
                        .withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, url))
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("クリックしてブラウザで開く"))));

        source.sendSuccess(() -> Component.literal("[MoveEarth] 分析Webダッシュボード: ")
                .withStyle(ChatFormatting.GOLD)
                .append(linkComponent), false);

        return 1;
    }

    private static int showHealth(CommandSourceStack source) {
        AnalyticsQueryService.INSTANCE.getCollectorHealthAsync().thenAccept(health -> {
            Component msg = AnalyticsTextFormatter.formatCollectorHealth(health);
            source.sendSuccess(() -> msg, false);
        });
        return 1;
    }

    private static int showPlayer(CommandSourceStack source, UUID playerUuid, TimeWindow window) {
        AnalyticsQueryService.INSTANCE.getPlayerSummaryAsync(playerUuid, window).thenAccept(opt -> {
            if (opt.isPresent()) {
                Component msg = AnalyticsTextFormatter.formatPlayerSummary(opt.get(), window);
                source.sendSuccess(() -> msg, false);
            } else {
                source.sendFailure(Component.literal("該当プレイヤーの分析データが見つかりませんでした。"));
            }
        });
        return 1;
    }

    private static int showRanking(CommandSourceStack source, TimeWindow window, int limit) {
        AnalyticsQueryService.INSTANCE.getTopActivePlayersAsync(window, limit).thenAccept(list -> {
            Component msg = AnalyticsTextFormatter.formatTopPlayers(list, window);
            source.sendSuccess(() -> msg, false);
        });
        return 1;
    }

    private static int showGroup(CommandSourceStack source, UUID ownerUuid, TimeWindow window) {
        AnalyticsQueryService.INSTANCE.getGroupSummaryAsync(ownerUuid, window).thenAccept(opt -> {
            if (opt.isPresent()) {
                Component msg = AnalyticsTextFormatter.formatGroupSummary(opt.get(), window);
                source.sendSuccess(() -> msg, false);
            } else {
                source.sendFailure(Component.literal("該当拠点の分析データが見つかりませんでした。"));
            }
        });
        return 1;
    }

    private static int showHeatmap(CommandSourceStack source, String dimension, TimeWindow window, int limit) {
        AnalyticsQueryService.INSTANCE.getSpatialHeatmapAsync(dimension, window, limit).thenAccept(cells -> {
            Component msg = AnalyticsTextFormatter.formatSpatialHeatmap(cells, dimension, window);
            source.sendSuccess(() -> msg, false);
        });
        return 1;
    }

    private static int exportData(CommandSourceStack source, String formatStr, TimeWindow window) {
        AnalyticsExportService.ExportFormat format = "jsonl".equalsIgnoreCase(formatStr)
                ? AnalyticsExportService.ExportFormat.JSONL
                : AnalyticsExportService.ExportFormat.CSV;

        source.sendSuccess(() -> Component.literal("[MoveEarth] プレイヤー分析データをエクスポート中...").withStyle(ChatFormatting.YELLOW), false);

        AnalyticsExportService.INSTANCE.exportPlayersAsync(source.getServer(), format, window)
                .thenAccept(path -> source.sendSuccess(() -> Component.literal("[MoveEarth] エクスポート完了: " + path.toAbsolutePath()).withStyle(ChatFormatting.GREEN), true))
                .exceptionally(e -> {
                    source.sendFailure(Component.literal("[MoveEarth] エクスポートに失敗しました: " + e.getMessage()));
                    return null;
                });

        return 1;
    }

    private static TimeWindow parseWindow(String val) {
        if (val == null) return TimeWindow.DAYS_7;
        return switch (val.toLowerCase()) {
            case "30d", "30days" -> TimeWindow.DAYS_30;
            case "all", "all_time" -> TimeWindow.ALL_TIME;
            default -> TimeWindow.DAYS_7;
        };
    }
}
