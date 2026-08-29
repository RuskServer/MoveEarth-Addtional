package com.ruskserver.moveearth_addtional.handler.occlusion;

import it.unimi.dsi.fastutil.longs.Long2LongMap;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ワールドごとのサブチャンク透過マスク（VisGraph）をキャッシュ・管理するストレージ。
 */
public class SectionOcclusionStorage {

    private static final Map<ResourceKey<Level>, Long2LongMap> LEVEL_CACHE = new ConcurrentHashMap<>();

    private static Long2LongMap getCacheForLevel(ServerLevel level) {
        return LEVEL_CACHE.computeIfAbsent(level.dimension(), k -> new Long2LongOpenHashMap());
    }

    /**
     * 指定されたサブチャンクの透過マスクを取得します。未計算の場合は計算してキャッシュします。
     *
     * @param level      サーバーレベル
     * @param sectionPos サブチャンク座標
     * @return 透過ビットマスク
     */
    public static long getSectionMask(ServerLevel level, SectionPos sectionPos) {
        Long2LongMap cache = getCacheForLevel(level);
        long key = sectionPos.asLong();

        synchronized (cache) {
            if (cache.containsKey(key)) {
                return cache.get(key);
            }
        }

        long mask = computeMask(level, sectionPos);

        synchronized (cache) {
            cache.put(key, mask);
        }

        return mask;
    }

    private static long computeMask(ServerLevel level, SectionPos sectionPos) {
        int chunkX = sectionPos.x();
        int chunkZ = sectionPos.z();
        int sectionY = sectionPos.y();

        if (!level.hasChunk(chunkX, chunkZ)) {
            return SubChunkVisGraph.ALL_OPEN_MASK;
        }

        LevelChunk chunk = level.getChunkSource().getChunkNow(chunkX, chunkZ);
        if (chunk == null) {
            return SubChunkVisGraph.ALL_OPEN_MASK;
        }

        int sectionIndex = chunk.getSectionIndexFromSectionY(sectionY);
        LevelChunkSection[] sections = chunk.getSections();
        if (sectionIndex < 0 || sectionIndex >= sections.length) {
            return SubChunkVisGraph.ALL_OPEN_MASK;
        }

        LevelChunkSection section = sections[sectionIndex];
        return SubChunkVisGraph.computeVisibilityMask(section);
    }

    /**
     * ブロックが変更された際に該当サブチャンクのキャッシュを無効化します。
     */
    public static void invalidate(ServerLevel level, BlockPos pos) {
        SectionPos sectionPos = SectionPos.of(pos);
        Long2LongMap cache = LEVEL_CACHE.get(level.dimension());
        if (cache != null) {
            synchronized (cache) {
                cache.remove(sectionPos.asLong());
            }
        }
    }

    /**
     * チャンクがアンロードされた際に該当チャンクの全サブチャンクのキャッシュを解放します。
     */
    public static void invalidateChunk(ServerLevel level, int chunkX, int chunkZ) {
        Long2LongMap cache = LEVEL_CACHE.get(level.dimension());
        if (cache != null) {
            synchronized (cache) {
                int minSection = level.getMinSection();
                int maxSection = level.getMaxSection();
                for (int y = minSection; y <= maxSection; y++) {
                    cache.remove(SectionPos.asLong(chunkX, y, chunkZ));
                }
            }
        }
    }

    /**
     * ディメンション破棄時またはサーバー停止時にキャッシュをクリアします。
     */
    public static void clearAll() {
        LEVEL_CACHE.clear();
    }
}
