package com.ruskserver.moveearth_addtional.config;

/**
 * サブチャンク透過グラフ（VisGraph / PVS）によるエンティティパケット制御の設定。
 */
public class SubChunkOcclusionConfig {

    /**
     * 機能全体の有効/無効フラグ
     */
    public static boolean enabled = true;

    /**
     * 至近距離バイパス半径（ブロック数）。
     * この距離以内のエンティティは壁越しであっても常にパケットを送信し、
     * 回収遅延やマグネット系Modとの互換性を確保します。
     */
    public static double bypassDistance = 3.5;

    /**
     * ドロップアイテム（ItemEntity）をカリング対象にするか
     */
    public static boolean affectItems = true;

    /**
     * 経験値オーブ（ExperienceOrb）をカリング対象にするか
     */
    public static boolean affectXpOrbs = true;

    /**
     * 視野角（FOV）マージン角度（度数法）。
     * 画面外から視界に入ってきた瞬間の遅延（ポップイン）を防ぐため、
     * プレイヤーの視野角に加算する余裕角度です。
     */
    public static double fovMarginDegrees = 30.0;

    /**
     * サブチャンク探索（BFS）の最大深度（サブチャンク数）。
     */
    public static int maxSearchDepth = 12;

    /**
     * プレイヤーの可視サブチャンク再計算を行う最大インターバル（Tick単位）。
     */
    public static int updateIntervalTicks = 10;

    /**
     * プレイヤーの視線角度（Yaw / Pitch）の変化により再計算をトリガーする閾値（度数法）。
     */
    public static double angleThresholdDegrees = 15.0;

    public static double getBypassDistanceSq() {
        return bypassDistance * bypassDistance;
    }
}
