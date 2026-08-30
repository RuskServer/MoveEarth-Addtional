package com.ruskserver.moveearth_addtional.config;

/**
 * Delayed Chunk Cache（遅延チャンクキャッシュ / DCC）機能の設定。
 * プレイヤーが視界外に出たチャンクの忘却パケット送信を一時保留し、
 * 往復移動時の巨大なチャンクデータパケット再送信をスキップして帯域を節約します。
 */
public class DelayedChunkCacheConfig {

    /**
     * 機能全体の有効/無効フラグ
     */
    public static boolean enabled = true;

    /**
     * プレイヤーあたりの最大保留チャンク数（LRU方式で最古のエントリーから破棄）。
     */
    public static int sizeLimit = 64;

    /**
     * 視界距離（View Distance）に加算する猶予チャンク半径。
     * この距離を超えて離れたチャンクは即座にエビクション（忘却パケット送信）されます。
     */
    public static int extraDistance = 2;

    /**
     * 保留チャンクのタイムアウト時間（秒）。
     * この時間を超えて視界外にいたチャンクは正式に忘却パケットが送信されます。
     */
    public static int timeoutSeconds = 30;

    /**
     * エビクション監視を行うTick間隔（10 Tick = 0.5秒）。
     */
    public static int checkIntervalTicks = 10;

    public static long getTimeoutTicks() {
        return (long) timeoutSeconds * 20L;
    }
}
