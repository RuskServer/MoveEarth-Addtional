package com.ruskserver.moveearth_addtional.analytics.query.format;

import com.ruskserver.moveearth_addtional.analytics.query.dto.*;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.text.DecimalFormat;
import java.util.List;

/**
 * プレイヤー分析KPIをチャット画面向けに見やすく整形するフォーマッター
 */
public class AnalyticsTextFormatter {

    public static String formatDuration(long totalSeconds) {
        return AnalyticsFormatUtil.formatDuration(totalSeconds);
    }

    public static String formatDistance(double blocks) {
        return AnalyticsFormatUtil.formatDistance(blocks);
    }

    public static String formatPercent(long part, long total) {
        return AnalyticsFormatUtil.formatPercent(part, total);
    }

    /**
     * プレイヤー活動サマリーの整形
     */
    public static Component formatPlayerSummary(PlayerSummaryDto dto, TimeWindow window) {
        MutableComponent root = Component.empty();

        root.append(Component.literal("=== プレイヤー分析: " + dto.lastKnownName() + " (期間: " + window.getId() + ") ===\n")
                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));

        // 参加性
        long online = dto.totalOnlineSeconds();
        long active = dto.totalActiveSeconds();
        long afk = dto.totalAfkSeconds();
        String activePct = formatPercent(active, online > 0 ? online : active + afk);

        root.append(Component.literal("【活動時間】 ").withStyle(ChatFormatting.YELLOW))
                .append(Component.literal("オンライン: " + formatDuration(online) + " | ").withStyle(ChatFormatting.WHITE))
                .append(Component.literal("実アクティブ: " + formatDuration(active) + " (" + activePct + ") | ").withStyle(ChatFormatting.GREEN))
                .append(Component.literal("AFK: " + formatDuration(afk) + "\n").withStyle(ChatFormatting.GRAY));

        root.append(Component.literal("【セッション】 ").withStyle(ChatFormatting.YELLOW))
                .append(Component.literal("回数: " + dto.sessionCount() + "回 | ").withStyle(ChatFormatting.WHITE))
                .append(Component.literal("平均: " + formatDuration(dto.avgSessionDurationSeconds()) + " | ").withStyle(ChatFormatting.WHITE))
                .append(Component.literal("アクティブ日数: " + dto.activeDays() + "日\n").withStyle(ChatFormatting.AQUA));

        // 行動実績
        root.append(Component.literal("【行動実績】 ").withStyle(ChatFormatting.YELLOW))
                .append(Component.literal("採掘: " + AnalyticsFormatUtil.formatNumber(dto.totalBreaks()) + " | ").withStyle(ChatFormatting.WHITE))
                .append(Component.literal("設置: " + AnalyticsFormatUtil.formatNumber(dto.totalPlaces()) + " | ").withStyle(ChatFormatting.WHITE))
                .append(Component.literal("クラフト: " + AnalyticsFormatUtil.formatNumber(dto.totalCrafts()) + "\n").withStyle(ChatFormatting.WHITE));

        root.append(Component.literal("【戦闘・Jobs】 ").withStyle(ChatFormatting.YELLOW))
                .append(Component.literal("PvEキル: " + AnalyticsFormatUtil.formatNumber(dto.totalPveKills()) + " / PvP: " + AnalyticsFormatUtil.formatNumber(dto.totalPvpKills()) + " | ").withStyle(ChatFormatting.RED))
                .append(Component.literal("死亡: " + AnalyticsFormatUtil.formatNumber(dto.totalDeaths()) + " | ").withStyle(ChatFormatting.DARK_RED))
                .append(Component.literal("Jobs XP: " + AnalyticsFormatUtil.formatDecimal(dto.totalJobsXp()) + "\n").withStyle(ChatFormatting.LIGHT_PURPLE));

        root.append(Component.literal("【移動・拠点】 ").withStyle(ChatFormatting.YELLOW))
                .append(Component.literal("移動距離: " + formatDistance(dto.totalDistanceBlocks()) + " | ").withStyle(ChatFormatting.WHITE))
                .append(Component.literal("TPA: " + dto.totalTpaSuccesses() + "回 | ").withStyle(ChatFormatting.WHITE))
                .append(Component.literal("主要世界: " + dto.primaryDimension()).withStyle(ChatFormatting.BLUE));

        return root;
    }

    /**
     * 拠点（検知グループ）サマリーの整形
     */
    public static Component formatGroupSummary(GroupSummaryDto dto, TimeWindow window) {
        MutableComponent root = Component.empty();

        root.append(Component.literal("=== 拠点分析: " + dto.ownerName() + " (期間: " + window.getId() + ") ===\n")
                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));

        root.append(Component.literal("【設備規模】 ").withStyle(ChatFormatting.YELLOW))
                .append(Component.literal("検知ブロック稼働数: " + dto.detectorCount() + "台\n").withStyle(ChatFormatting.WHITE));

        root.append(Component.literal("【滞在実績】 ").withStyle(ChatFormatting.YELLOW))
                .append(Component.literal("メンバー滞在: " + AnalyticsFormatUtil.formatDecimal(dto.totalMemberMinutes()) + "分 | ").withStyle(ChatFormatting.GREEN))
                .append(Component.literal("訪問者滞在: " + AnalyticsFormatUtil.formatDecimal(dto.totalVisitorMinutes()) + "分\n").withStyle(ChatFormatting.AQUA));

        root.append(Component.literal("【防犯・侵入】 ").withStyle(ChatFormatting.YELLOW))
                .append(Component.literal("侵入セッション数: " + dto.totalIntrusionSessions() + "回 | ").withStyle(ChatFormatting.RED))
                .append(Component.literal("最大同時メンバー: " + dto.maxDistinctMembers() + "人 | ").withStyle(ChatFormatting.WHITE))
                .append(Component.literal("最大同時訪問者: " + dto.maxDistinctVisitors() + "人").withStyle(ChatFormatting.WHITE));

        return root;
    }

    /**
     * アクティブプレイヤーランキングの整形
     */
    public static Component formatTopPlayers(List<PlayerSummaryDto> list, TimeWindow window) {
        MutableComponent root = Component.empty();

        root.append(Component.literal("=== アクティブプレイヤーランキング (期間: " + window.getId() + ") ===\n")
                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));

        if (list.isEmpty()) {
            root.append(Component.literal("該当するプレイヤーデータがありません。").withStyle(ChatFormatting.GRAY));
            return root;
        }

        for (int i = 0; i < list.size(); i++) {
            PlayerSummaryDto p = list.get(i);
            root.append(Component.literal((i + 1) + ". ").withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD))
                    .append(Component.literal(p.lastKnownName() + " ").withStyle(ChatFormatting.AQUA))
                    .append(Component.literal("- 実アクティブ: " + formatDuration(p.totalActiveSeconds()) + " ").withStyle(ChatFormatting.GREEN))
                    .append(Component.literal("(採掘: " + AnalyticsFormatUtil.formatNumber(p.totalBreaks()) + ", Jobs: " + AnalyticsFormatUtil.formatDecimal(p.totalJobsXp()) + ")\n").withStyle(ChatFormatting.GRAY));
        }

        return root;
    }

    /**
     * 空間ヒートマップ集計の整形
     */
    public static Component formatSpatialHeatmap(List<SpatialHeatmapCellDto> cells, String dimension, TimeWindow window) {
        MutableComponent root = Component.empty();

        root.append(Component.literal("=== 空間ヒートマップ: " + dimension + " (期間: " + window.getId() + ") ===\n")
                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));

        if (cells.isEmpty()) {
            root.append(Component.literal("該当する空間データがありません。").withStyle(ChatFormatting.GRAY));
            return root;
        }

        for (int i = 0; i < cells.size(); i++) {
            SpatialHeatmapCellDto c = cells.get(i);
            int blockX = c.cellX() * 32;
            int blockZ = c.cellZ() * 32;

            root.append(Component.literal((i + 1) + ". ").withStyle(ChatFormatting.YELLOW))
                    .append(Component.literal("[X: " + blockX + "~, Z: " + blockZ + "~] ").withStyle(ChatFormatting.WHITE))
                    .append(Component.literal("(" + c.yBand() + ") ").withStyle(ChatFormatting.GRAY))
                    .append(Component.literal("サンプル: " + AnalyticsFormatUtil.formatNumber(c.totalActiveSamples()) + "回 ").withStyle(ChatFormatting.GREEN))
                    .append(Component.literal("(プレイヤー: " + c.maxUniquePlayers() + "人) ").withStyle(ChatFormatting.AQUA))
                    .append(Component.literal("[" + c.relation() + "]\n").withStyle(ChatFormatting.DARK_AQUA));
        }

        return root;
    }

    /**
     * システムヘルスの整形
     */
    public static Component formatCollectorHealth(CollectorHealthDto health) {
        MutableComponent root = Component.empty();

        root.append(Component.literal("=== 分析システムヘルス情報 ===\n")
                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));

        root.append(Component.literal("キュー深度: ").withStyle(ChatFormatting.YELLOW))
                .append(Component.literal(health.queueDepth() + " / 10,000 | ").withStyle(health.queueDepth() > 5000 ? ChatFormatting.RED : ChatFormatting.GREEN));

        root.append(Component.literal("破棄イベント数: ").withStyle(ChatFormatting.YELLOW))
                .append(Component.literal(health.droppedEventsTotal() + "件 | ").withStyle(health.droppedEventsTotal() > 0 ? ChatFormatting.RED : ChatFormatting.GREEN));

        root.append(Component.literal("直近Flush: ").withStyle(ChatFormatting.YELLOW))
                .append(Component.literal(health.lastFlushDurationMs() + "ms | ").withStyle(ChatFormatting.AQUA));

        double dbMb = (double) health.databaseSizeBytes() / (1024.0 * 1024.0);
        root.append(Component.literal("DBサイズ: ").withStyle(ChatFormatting.YELLOW))
                .append(Component.literal(AnalyticsFormatUtil.formatDecimal(dbMb) + " MB").withStyle(ChatFormatting.WHITE));

        return root;
    }
}
