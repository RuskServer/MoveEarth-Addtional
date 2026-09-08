package com.ruskserver.moveearth_addtional.handler.occlusion;

import com.ruskserver.moveearth_addtional.config.SubChunkOcclusionConfig;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.Map;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 各プレイヤーの視界内にある可視サブチャンクを追跡・管理し、
 * エンティティのパケット送信可否を判定するサービスクラス。
 */
public class PlayerVisibilityTracker {

    private static final Map<UUID, PlayerCache> PLAYER_CACHES = new ConcurrentHashMap<>();
    private static final Direction[] DIRECTIONS = Direction.values();

    private static class PlayerCache {
        SectionPos lastSectionPos;
        float lastYaw;
        float lastPitch;
        long lastUpdateGameTime;
        final LongSet visibleSections = new LongOpenHashSet();
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
            LongSet visibleSections = getVisibleSections(player);
            SectionPos entitySection = SectionPos.of(entity.blockPosition());
            return visibleSections.contains(entitySection.asLong());
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
        PlayerCache cache = PLAYER_CACHES.computeIfAbsent(player.getUUID(), k -> new PlayerCache());
        SectionPos currentSection = SectionPos.of(player.blockPosition());
        long currentGameTime = player.serverLevel().getGameTime();

        boolean needsUpdate = cache.lastSectionPos == null
                || !cache.lastSectionPos.equals(currentSection)
                || (currentGameTime - cache.lastUpdateGameTime >= SubChunkOcclusionConfig.updateIntervalTicks)
                || Math.abs(player.getYRot() - cache.lastYaw) >= SubChunkOcclusionConfig.angleThresholdDegrees
                || Math.abs(player.getXRot() - cache.lastPitch) >= SubChunkOcclusionConfig.angleThresholdDegrees;

        if (needsUpdate) {
            updateVisibleSections(player, cache, currentSection, currentGameTime);
        }

        return cache.visibleSections;
    }

    private static void updateVisibleSections(ServerPlayer player, PlayerCache cache, SectionPos startSection, long gameTime) {
        cache.lastSectionPos = startSection;
        cache.lastYaw = player.getYRot();
        cache.lastPitch = player.getXRot();
        cache.lastUpdateGameTime = gameTime;
        cache.visibleSections.clear();

        ServerLevel level = player.serverLevel();
        Vec3 eyePos = player.getEyePosition();
        Vec3 lookVec = player.getLookAngle().normalize();

        // 視野角判定用のコサイン閾値（マージンを加味）
        // 視野角110度 + マージン30度 = 140度（半角70度） -> cos(70°) ≒ 0.342
        double halfFovRad = Math.toRadians((110.0 + SubChunkOcclusionConfig.fovMarginDegrees) / 2.0);
        double minDotProduct = Math.cos(halfFovRad);

        Queue<SearchNode> queue = new ArrayDeque<>();

        // スタート地点（プレイヤーがいるサブチャンク）を登録
        cache.visibleSections.add(startSection.asLong());

        // スタート地点から全6方向へ探索開始
        for (Direction dir : DIRECTIONS) {
            SectionPos neighbor = SectionPos.of(startSection.x() + dir.getStepX(), startSection.y() + dir.getStepY(), startSection.z() + dir.getStepZ());
            queue.add(new SearchNode(neighbor, dir.getOpposite(), 1));
        }

        int maxDepth = SubChunkOcclusionConfig.maxSearchDepth;

        while (!queue.isEmpty()) {
            SearchNode node = queue.poll();
            SectionPos sectionPos = node.sectionPos;
            long sectionLong = sectionPos.asLong();

            if (cache.visibleSections.contains(sectionLong)) {
                continue;
            }

            // 視野錐台（Frustum / FOV）による除外判定
            // プレイヤーから近距離（深さ1）以外のサブチャンクに対して視線方向チェック
            if (node.depth > 1) {
                double centerX = (sectionPos.x() << 4) + 8.0;
                double centerY = (sectionPos.y() << 4) + 8.0;
                double centerZ = (sectionPos.z() << 4) + 8.0;
                Vec3 toSection = new Vec3(centerX - eyePos.x, centerY - eyePos.y, centerZ - eyePos.z).normalize();
                double dot = lookVec.dot(toSection);

                if (dot < minDotProduct) {
                    // 視野角外のため探索打ち切り
                    continue;
                }
            }

            // サブチャンクを可視集合に追加
            cache.visibleSections.add(sectionLong);

            if (node.depth >= maxDepth) {
                continue;
            }

            // このサブチャンクの透過マスクを取得し、出射可能な面から隣接サブチャンクへ伝播
            long mask = SectionOcclusionStorage.getSectionMask(level, sectionPos);
            Direction enterFace = node.enterFace;

            for (Direction exitFace : DIRECTIONS) {
                if (exitFace == enterFace) {
                    continue; // 入ってきた方向には戻らない
                }

                if (SubChunkVisGraph.isConnected(mask, enterFace, exitFace)) {
                    SectionPos nextSection = SectionPos.of(sectionPos.x() + exitFace.getStepX(), sectionPos.y() + exitFace.getStepY(), sectionPos.z() + exitFace.getStepZ());
                    if (!cache.visibleSections.contains(nextSection.asLong())) {
                        queue.add(new SearchNode(nextSection, exitFace.getOpposite(), node.depth + 1));
                    }
                }
            }
        }
    }

    public static void removePlayer(UUID playerId) {
        PLAYER_CACHES.remove(playerId);
    }

    public static void clearAll() {
        PLAYER_CACHES.clear();
    }

    private static record SearchNode(SectionPos sectionPos, Direction enterFace, int depth) {}
}
