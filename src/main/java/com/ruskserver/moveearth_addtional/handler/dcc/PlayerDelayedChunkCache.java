package com.ruskserver.moveearth_addtional.handler.dcc;

import com.ruskserver.moveearth_addtional.config.DelayedChunkCacheConfig;
import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 各プレイヤーごとの保留チャンク（DCC）を管理するクラス。
 * LRU（最も長く参照されていない順）による容量制限、距離超過、タイムアウトに基づくエビクションを処理します。
 */
public class PlayerDelayedChunkCache {

    public static record DelayedEntry(ChunkPos pos, long registeredGameTime, int x, int z) {}

    private final Long2ObjectLinkedOpenHashMap<DelayedEntry> cache = new Long2ObjectLinkedOpenHashMap<>();

    /**
     * チャンクを保留キャッシュに追加します。
     * 容量上限を超えた場合、溢れた最古のチャンクをエビクション対象として返します。
     *
     * @param pos      対象チャンク座標
     * @param gameTime 登録時のゲーム時間
     * @return 容量超過によりエビクションされたチャンク（存在しない場合は null）
     */
    public synchronized ChunkPos add(ChunkPos pos, long gameTime) {
        long key = pos.toLong();
        cache.remove(key); // 既存があれば削除して末尾（最新）に挿入
        cache.put(key, new DelayedEntry(pos, gameTime, pos.x, pos.z));

        int limit = Math.max(1, DelayedChunkCacheConfig.sizeLimit);
        if (cache.size() > limit) {
            // 最古のエントリー（先頭）を取り出して削除
            DelayedEntry oldest = cache.removeFirst();
            return oldest.pos();
        }
        return null;
    }

    /**
     * プレイヤーがチャンクに再侵入した際にキャッシュから取り出します。
     *
     * @param pos 対象チャンク座標
     * @return キャッシュに存在していた場合（＝再送信スキップ可能）true
     */
    public synchronized boolean consume(ChunkPos pos) {
        return cache.remove(pos.toLong()) != null;
    }

    /**
     * チャンクが現在保留中か判定します。
     */
    public synchronized boolean contains(ChunkPos pos) {
        return cache.containsKey(pos.toLong());
    }

    /**
     * タイムアウトまたは距離超過により期限切れとなったチャンクを抽出・削除して返します。
     *
     * @param player          対象プレイヤー
     * @param currentGameTime 現在のゲーム時間
     * @param viewDistance    プレイヤーの視界距離（チャンク単位）
     * @return エビクション（忘却パケット送信）すべきチャンクのリスト
     */
    public synchronized List<ChunkPos> evictExpired(ServerPlayer player, long currentGameTime, int viewDistance) {
        if (cache.isEmpty()) {
            return Collections.emptyList();
        }

        long timeoutTicks = DelayedChunkCacheConfig.getTimeoutTicks();
        int maxDist = viewDistance + Math.max(0, DelayedChunkCacheConfig.extraDistance);
        ChunkPos playerChunk = player.chunkPosition();
        int px = playerChunk.x;
        int pz = playerChunk.z;

        List<ChunkPos> evicted = new ArrayList<>();
        var iterator = cache.long2ObjectEntrySet().fastIterator();

        while (iterator.hasNext()) {
            Long2ObjectMap.Entry<DelayedEntry> entry = iterator.next();
            DelayedEntry val = entry.getValue();

            boolean timedOut = (currentGameTime - val.registeredGameTime()) >= timeoutTicks;
            boolean outOfDistance = Math.max(Math.abs(px - val.x()), Math.abs(pz - val.z())) > maxDist;

            if (timedOut || outOfDistance) {
                evicted.add(val.pos());
                iterator.remove();
            }
        }

        return evicted;
    }

    /**
     * キャッシュ内の全チャンクを取り出してクリアします（ログアウト・ディメンション移動時用）。
     *
     * @return 保留されていた全チャンクのリスト
     */
    public synchronized List<ChunkPos> flushAll() {
        if (cache.isEmpty()) {
            return Collections.emptyList();
        }
        List<ChunkPos> all = new ArrayList<>(cache.size());
        for (DelayedEntry entry : cache.values()) {
            all.add(entry.pos());
        }
        cache.clear();
        return all;
    }

    public synchronized int size() {
        return cache.size();
    }
}
