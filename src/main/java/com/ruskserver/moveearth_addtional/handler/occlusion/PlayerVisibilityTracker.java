package com.ruskserver.moveearth_addtional.handler.occlusion;

import com.ruskserver.moveearth_addtional.config.SubChunkOcclusionConfig;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.core.SectionPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 各プレイヤーの視界内にある可視サブチャンクを追跡・管理し、
 * エンティティのパケット送信可否を判定するサービスクラス。
 */
public class PlayerVisibilityTracker {

    /** Movement and turning recompute no more often than this per player. */
    static final int MIN_RECOMPUTE_INTERVAL_TICKS = 5;
    /** Node budget per search; past it the unreached sections fail open. */
    static final int MAX_SEARCH_SECTIONS = 2048;

    private static final Map<UUID, PlayerCache> PLAYER_CACHES = new ConcurrentHashMap<>();

    private static class PlayerCache {
        boolean computed;
        ResourceKey<Level> dimension;
        int startX;
        int startY;
        int startZ;
        float lastYaw;
        float lastPitch;
        long lastUpdateGameTime;
        int truncatedDepth = SectionVisibilitySearch.COMPLETE;
        final LongOpenHashSet visibleSections = new LongOpenHashSet();
        final LongOpenHashSet visited = new LongOpenHashSet();
        final SectionVisibilitySearch search = new SectionVisibilitySearch(MAX_SEARCH_SECTIONS);
    }

    /**
     * 指定されたプレイヤーがエンティティをトラッキング（パケット受信）すべきかを判定します。
     *
     * @param player プレイヤー
     * @param entity 対象エンティティ
     * @return パケットを送信する場合 true, 遮蔽・視界外のため送信しない場合 false
     */
    public static boolean canPlayerTrackEntity(ServerPlayer player, Entity entity) {
        if (!SubChunkOcclusionConfig.enabled) {
            return true;
        }

        // 対象エンティティ判定（他Modエンティティやモンスター、乗り物等は常に送信）
        if (!isTargetEntity(entity)) {
            return true;
        }

        // 至近距離バイパス（至近距離は壁越しでも常に送信して違和感・遅延・回収不具合を防止）
        double distSq = player.distanceToSqr(entity);
        if (distSq <= SubChunkOcclusionConfig.getBypassDistanceSq()) {
            return true;
        }

        try {
            PlayerCache cache = refresh(player);
            int sectionX = SectionPos.blockToSectionCoord(entity.getBlockX());
            int sectionY = SectionPos.blockToSectionCoord(entity.getBlockY());
            int sectionZ = SectionPos.blockToSectionCoord(entity.getBlockZ());
            if (cache.visibleSections.contains(SectionVisibilitySearch.key(sectionX, sectionY, sectionZ))) return true;
            return OcclusionRecomputePolicy.beyondTruncation(cache.truncatedDepth,
                    cache.startX, cache.startY, cache.startZ, sectionX, sectionY, sectionZ);
        } catch (Exception e) {
            // フェイルセーフ: 例外発生時は送信側にフォールバック
            return true;
        }
    }

    private static boolean isTargetEntity(Entity entity) {
        if (entity instanceof ItemEntity && SubChunkOcclusionConfig.affectItems) {
            return true;
        }
        if (entity instanceof ExperienceOrb && SubChunkOcclusionConfig.affectXpOrbs) {
            return true;
        }
        return false;
    }

    /**
     * プレイヤーの現在の可視サブチャンク集合を取得します（必要に応じて再探索）。
     */
    public static LongSet getVisibleSections(ServerPlayer player) {
        return refresh(player).visibleSections;
    }

    private static PlayerCache refresh(ServerPlayer player) {
        PlayerCache cache = PLAYER_CACHES.computeIfAbsent(player.getUUID(), k -> new PlayerCache());
        ServerLevel level = player.serverLevel();
        int sectionX = SectionPos.blockToSectionCoord(player.getBlockX());
        int sectionY = SectionPos.blockToSectionCoord(player.getBlockY());
        int sectionZ = SectionPos.blockToSectionCoord(player.getBlockZ());
        long currentGameTime = level.getGameTime();

        boolean sectionChanged = sectionX != cache.startX || sectionY != cache.startY || sectionZ != cache.startZ;
        double threshold = SubChunkOcclusionConfig.angleThresholdDegrees;
        boolean turned = Math.abs(Mth.wrapDegrees(player.getYRot() - cache.lastYaw)) >= threshold
                || Math.abs(player.getXRot() - cache.lastPitch) >= threshold;
        boolean hasResult = cache.computed && level.dimension().equals(cache.dimension);
        if (OcclusionRecomputePolicy.shouldRecompute(hasResult, currentGameTime - cache.lastUpdateGameTime,
                sectionChanged, turned, MIN_RECOMPUTE_INTERVAL_TICKS,
                Math.max(MIN_RECOMPUTE_INTERVAL_TICKS, SubChunkOcclusionConfig.updateIntervalTicks))) {
            updateVisibleSections(player, level, cache, sectionX, sectionY, sectionZ, currentGameTime);
        }
        return cache;
    }

    private static void updateVisibleSections(ServerPlayer player, ServerLevel level, PlayerCache cache,
                                              int sectionX, int sectionY, int sectionZ, long gameTime) {
        cache.computed = true;
        cache.dimension = level.dimension();
        cache.startX = sectionX;
        cache.startY = sectionY;
        cache.startZ = sectionZ;
        cache.lastYaw = player.getYRot();
        cache.lastPitch = player.getXRot();
        cache.lastUpdateGameTime = gameTime;
        cache.visibleSections.clear();
        cache.visited.clear();

        Vec3 eyePos = player.getEyePosition();
        Vec3 lookVec = player.getLookAngle().normalize();

        // 視野角判定用のコサイン閾値（マージンを加味）
        // 視野角110度 + マージン30度 = 140度（半角70度） -> cos(70°) ≒ 0.342
        double halfFovRad = Math.toRadians((110.0 + SubChunkOcclusionConfig.fovMarginDegrees) / 2.0);
        double minDotProduct = Math.cos(halfFovRad);

        LongOpenHashSet visited = cache.visited;
        LongOpenHashSet visible = cache.visibleSections;
        cache.truncatedDepth = cache.search.run(sectionX, sectionY, sectionZ,
                eyePos.x, eyePos.y, eyePos.z, lookVec.x, lookVec.y, lookVec.z,
                minDotProduct, SubChunkOcclusionConfig.maxSearchDepth,
                (x, y, z) -> SectionOcclusionStorage.getSectionMask(level, x, y, z),
                visited::add, visible::add);
    }

    public static void removePlayer(UUID playerId) {
        PLAYER_CACHES.remove(playerId);
    }

    public static void clearAll() {
        PLAYER_CACHES.clear();
    }
}
