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

    /** Masks use 36 bits, so a negative value can mean "not cached" without a second lookup. */
    private static final long MISSING = -1L;

    private static Long2LongMap getCacheForLevel(ServerLevel level) {
        return LEVEL_CACHE.computeIfAbsent(level.dimension(), k -> {
            Long2LongOpenHashMap map = new Long2LongOpenHashMap();
            map.defaultReturnValue(MISSING);
            return map;
        });
    }

    /**
     * 指定されたサブチャンクの透過マスクを取得します。未計算の場合は計算してキャッシュします。
     *
     * @param level      サーバーレベル
     * @param sectionPos サブチャンク座標
     * @return 透過ビットマスク
     */
    public static long getSectionMask(ServerLevel level, SectionPos sectionPos) {
        return getSectionMask(level, sectionPos.x(), sectionPos.y(), sectionPos.z());
    }

    /** Allocation-free form for the per-player search, which asks for hundreds of sections. */
    public static long getSectionMask(ServerLevel level, int sectionX, int sectionY, int sectionZ) {
        Long2LongMap cache = getCacheForLevel(level);
        long key = SectionPos.asLong(sectionX, sectionY, sectionZ);

        long cached;
        synchronized (cache) {
            cached = cache.get(key);
        }
        if (cached != MISSING) {
            return cached;
        }

        // Unloaded chunks and heights outside the world answer "open" without being cached:
        // nothing would ever invalidate such an entry, so the map would only grow.
        if (sectionY < level.getMinSection() || sectionY >= level.getMaxSection()
                || level.getChunkSource().getChunkNow(sectionX, sectionZ) == null) {
            return SubChunkVisGraph.ALL_OPEN_MASK;
        }

        long mask = computeMask(level, sectionX, sectionY, sectionZ);

        synchronized (cache) {
            cache.put(key, mask);
        }

        return mask;
    }

    private static long computeMask(ServerLevel level, int chunkX, int sectionY, int chunkZ) {
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
        Long2LongMap cache = LEVEL_CACHE.get(level.dimension());
        if (cache != null) {
            long key = SectionPos.asLong(pos);
            synchronized (cache) {
                cache.remove(key);
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
