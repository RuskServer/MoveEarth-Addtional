package com.ruskserver.moveearth_addtional.oxygen;

import net.neoforged.neoforge.common.ModConfigSpec;

public class OxygenConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    // 高度設定
    public static final ModConfigSpec.IntValue DEPTH_DANGER_Y;
    public static final ModConfigSpec.IntValue DEPTH_EXTREME_Y;

    // フィルター＆酸素設定
    public static final ModConfigSpec.IntValue FILTER_BASE_DURATION_SECONDS;
    public static final ModConfigSpec.DoubleValue EXTREME_DEPTH_MULTIPLIER;
    public static final ModConfigSpec.DoubleValue SPRINTING_MULTIPLIER;
    public static final ModConfigSpec.DoubleValue MINING_MULTIPLIER;
    public static final ModConfigSpec.DoubleValue COMBAT_MULTIPLIER;

    // ダメージ・窒息設定
    public static final ModConfigSpec.IntValue OXYGEN_DEPLETION_TICKS;
    public static final ModConfigSpec.DoubleValue SUFFOCATION_DAMAGE_PERCENT;
    public static final ModConfigSpec.IntValue DAMAGE_INTERVAL_TICKS;

    // 松明消灯設定
    public static final ModConfigSpec.BooleanValue EXTINGUISH_TORCHES;

    static {
        BUILDER.push("Oxygen_System");

        DEPTH_DANGER_Y = BUILDER
                .comment("酸素欠乏・有害ガスが始まるY高度（この高度以下でマスクが必要）")
                .defineInRange("depthDangerY", 0, -64, 320);

        DEPTH_EXTREME_Y = BUILDER
                .comment("最深部・超高圧猛毒ゾーンのY高度（この高度以下でフィルター消費がさらに加速）")
                .defineInRange("depthExtremeY", -32, -64, 320);

        FILTER_BASE_DURATION_SECONDS = BUILDER
                .comment("活性炭フィルター1個の基本持続秒数（静止状態・浅深部）")
                .defineInRange("filterBaseDurationSeconds", 180, 10, 3600);

        EXTREME_DEPTH_MULTIPLIER = BUILDER
                .comment("最深部（depthExtremeY以下）でのフィルター消費速度倍率")
                .defineInRange("extremeDepthMultiplier", 2.0, 1.0, 10.0);

        SPRINTING_MULTIPLIER = BUILDER
                .comment("ダッシュ移動時のフィルター消費倍率")
                .defineInRange("sprintingMultiplier", 1.5, 1.0, 5.0);

        MINING_MULTIPLIER = BUILDER
                .comment("ブロック採掘中のフィルター消費倍率")
                .defineInRange("miningMultiplier", 2.0, 1.0, 5.0);

        COMBAT_MULTIPLIER = BUILDER
                .comment("戦闘（攻撃・被弾）時のフィルター消費倍率")
                .defineInRange("combatMultiplier", 2.5, 1.0, 5.0);

        OXYGEN_DEPLETION_TICKS = BUILDER
                .comment("マスク未装備またはフィルター切れ時に酸素が100%から0%になるまでの時間（ティック数、20ticks=1秒）")
                .defineInRange("oxygenDepletionTicks", 100, 20, 1200);

        SUFFOCATION_DAMAGE_PERCENT = BUILDER
                .comment("酸素枯渇時に受ける窒息ダメージ（最大HPに対する割合、例: 0.15 = 15%）")
                .defineInRange("suffocationDamagePercent", 0.15, 0.01, 1.0);

        DAMAGE_INTERVAL_TICKS = BUILDER
                .comment("窒息ダメージの発生間隔（ティック数）")
                .defineInRange("damageIntervalTicks", 20, 5, 100);

        EXTINGUISH_TORCHES = BUILDER
                .comment("危険深度（depthDangerY以下）で松明を設置した際に即座に消火・消灯させるか")
                .define("extinguishTorches", true);

        BUILDER.pop();
        SPEC = BUILDER.build();
    }
}
