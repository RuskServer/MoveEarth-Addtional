package com.ruskserver.moveearth_addtional.pvp;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public record PvpTaskDefinition(
        String id,
        PvpTaskCategory category,
        PvpTaskType type,
        String title,
        String description,
        int target,
        int pointReward,
        Item itemReward,
        int itemRewardCount
) {
    public static final List<PvpTaskDefinition> ALL = List.of(
            new PvpTaskDefinition("daily_kills", PvpTaskCategory.DAILY, PvpTaskType.KILL,
                    "戦闘任務", "敵プレイヤーを5人倒す", 5, 25, Items.IRON_INGOT, 16),
            new PvpTaskDefinition("daily_close_kills", PvpTaskCategory.DAILY, PvpTaskType.CLOSE_RANGE_KILL,
                    "接近戦", "8ブロック以内から3キルする", 3, 35, Items.GUNPOWDER, 16),
            new PvpTaskDefinition("daily_zone", PvpTaskCategory.DAILY, PvpTaskType.ZONE_SECONDS,
                    "ゾーン確保", "ゾーンを合計120秒占領する", 120, 30, Items.QUARTZ, 24),
            new PvpTaskDefinition("event_matches", PvpTaskCategory.EVENT, PvpTaskType.MATCH_PLAY,
                    "作戦参加", "報酬対象の試合を3回完了する", 3, 50, Items.GOLD_INGOT, 8),
            new PvpTaskDefinition("event_wins", PvpTaskCategory.EVENT, PvpTaskType.MATCH_WIN,
                    "勝利への貢献", "報酬対象の試合で2勝する", 2, 100, Items.NETHERITE_INGOT, 1),
            new PvpTaskDefinition("event_champion", PvpTaskCategory.EVENT, PvpTaskType.MATCH_WIN,
                    "イベント制覇", "報酬対象の試合で5勝する", 5, 250, Items.NETHER_STAR, 1)
    );
    public static final Map<String, PvpTaskDefinition> BY_ID = ALL.stream()
            .collect(Collectors.toUnmodifiableMap(PvpTaskDefinition::id, Function.identity()));
}
