package com.ruskserver.moveearth_addtional.handler.dcc;

import com.ruskserver.moveearth_addtional.config.DelayedChunkCacheConfig;
import net.minecraft.network.protocol.game.ClientboundForgetLevelChunkPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Delayed Chunk Cache（DCC）のライフサイクル・パケット制御を統括するマネージャー。
 */
public class DelayedChunkCacheManager {

    private static final Map<UUID, PlayerDelayedChunkCache> PLAYER_CACHES = new ConcurrentHashMap<>();

    private static final ThreadLocal<Boolean> IS_FLUSHING = ThreadLocal.withInitial(() -> false);

    /**
     * プレイヤーがチャンクの視界外に出た際に、即時アンロードを保留（DCCに格納）すべきか判定します。
     *
     * @param player   対象プレイヤー
     * @param chunkPos 離脱したチャンク座標
     * @return 即時アンロードを保留する場合 true（バニラの忘却パケット送信をキャンセル）
     */
    public static boolean shouldDelayChunkDrop(ServerPlayer player, ChunkPos chunkPos) {
        if (!DelayedChunkCacheConfig.enabled || IS_FLUSHING.get()) {
            return false;
        }

        PlayerDelayedChunkCache cache = PLAYER_CACHES.computeIfAbsent(player.getUUID(), k -> new PlayerDelayedChunkCache());
        long gameTime = player.serverLevel().getGameTime();

        // キャッシュに追加し、容量制限を超えた最古のチャンクがあれば即時忘却パケットを送信
        ChunkPos overflowChunk = cache.add(chunkPos, gameTime);
        if (overflowChunk != null) {
            sendForgetPacket(player, overflowChunk);
        }

        return true;
    }

    /**
     * プレイヤーがチャンクの視界内に進入した際、DCCキャッシュヒットにより再送信をスキップできるか判定します。
     *
     * @param player   対象プレイヤー
     * @param chunkPos 進入したチャンク座標
     * @return キャッシュヒット（再送信スキップ）の場合 true
     */
    public static boolean consumeDelayedChunk(ServerPlayer player, ChunkPos chunkPos) {
        if (!DelayedChunkCacheConfig.enabled || IS_FLUSHING.get()) {
            return false;
        }

        PlayerDelayedChunkCache cache = PLAYER_CACHES.get(player.getUUID());
        if (cache != null && cache.consume(chunkPos)) {
            // クライアント側にはチャンクが残っているため、巨大なチャンクデータパケット送信をスキップ
            return true;
        }
        return false;
    }

    /**
     * サーバーTick毎に実行され、タイムアウトまたは距離超過した保留チャンクをエビクションします。
     */
    public static void tickServer(MinecraftServer server) {
        if (!DelayedChunkCacheConfig.enabled || server.getTickCount() % DelayedChunkCacheConfig.checkIntervalTicks != 0) {
            return;
        }

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            PlayerDelayedChunkCache cache = PLAYER_CACHES.get(player.getUUID());
            if (cache == null || cache.size() == 0) {
                continue;
            }

            long gameTime = player.serverLevel().getGameTime();
            int viewDistance = server.getPlayerList().getViewDistance();
            List<ChunkPos> expired = cache.evictExpired(player, gameTime, viewDistance);

            for (ChunkPos pos : expired) {
                sendForgetPacket(player, pos);
            }
        }
    }

    /**
     * プレイヤーのログアウトまたはディメンション移動時に、保留中の全チャンクをフラッシュします。
     */
    public static void flushPlayer(ServerPlayer player) {
        PlayerDelayedChunkCache cache = PLAYER_CACHES.remove(player.getUUID());
        if (cache != null) {
            List<ChunkPos> all = cache.flushAll();
            for (ChunkPos pos : all) {
                sendForgetPacket(player, pos);
            }
        }
    }

    private static void sendForgetPacket(ServerPlayer player, ChunkPos chunkPos) {
        if (player.connection != null) {
            IS_FLUSHING.set(true);
            try {
                player.connection.send(new ClientboundForgetLevelChunkPacket(chunkPos));
            } finally {
                IS_FLUSHING.set(false);
            }
        }
    }

    public static void clearAll() {
        PLAYER_CACHES.clear();
    }
}
