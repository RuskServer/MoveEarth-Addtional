package com.ruskserver.moveearth_addtional.pvp;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.*;

public final class PvpSpawnSelector {
    public static final PvpSpawnSelector INSTANCE = new PvpSpawnSelector();
    private static final int RECENT_SPAWN_EXPIRY_TICKS = 10 * 20;

    private final Map<BlockPos, Integer> recentSpawns = new HashMap<>();

    private PvpSpawnSelector() {}

    public BlockPos selectSpawn(ServerPlayer player, PvpMapDefinition map, PvpTeam team, MinecraftServer server) {
        List<BlockPos> candidates = team == PvpTeam.RED ? map.allRedSpawns() : map.allBlueSpawns();
        if (candidates.isEmpty()) {
            return team == PvpTeam.RED ? map.redSpawn() : map.blueSpawn();
        }
        if (candidates.size() == 1) {
            recordRecentSpawn(candidates.get(0), server);
            return candidates.get(0);
        }

        // 周辺状況の収集
        List<ServerPlayer> enemies = new ArrayList<>();
        List<ServerPlayer> allies = new ArrayList<>();
        for (ServerPlayer other : PvpMatchManager.INSTANCE.participants(server, true)) {
            if (other.getUUID().equals(player.getUUID())) continue;
            if (!other.level().dimension().equals(PvpMatchManager.ARENA)) continue;
            if (PvpMatchManager.INSTANCE.isRespawning(other.getUUID())) continue;

            if (PvpMatchManager.INSTANCE.team(other) == team) {
                allies.add(other);
            } else {
                enemies.add(other);
            }
        }

        BlockPos hillCenter = new BlockPos(
                (map.hillMin().getX() + map.hillMax().getX()) / 2,
                (map.hillMin().getY() + map.hillMax().getY()) / 2,
                (map.hillMin().getZ() + map.hillMax().getZ()) / 2
        );

        int now = server.getTickCount();
        cleanupRecentSpawns(now);

        BlockPos bestCandidate = candidates.get(0);
        double bestScore = Double.NEGATIVE_INFINITY;
        Random random = new Random();

        for (BlockPos candidate : candidates) {
            double score = 0.0;

            // 1. 敵プレイヤーとの距離（リスキル回避）
            double minEnemyDistSq = Double.MAX_VALUE;
            for (ServerPlayer enemy : enemies) {
                double distSq = candidate.distSqr(enemy.blockPosition());
                if (distSq < minEnemyDistSq) minEnemyDistSq = distSq;
            }
            if (!enemies.isEmpty()) {
                double minEnemyDist = Math.sqrt(minEnemyDistSq);
                if (minEnemyDist < 12.0) {
                    score -= 1500.0; // 至近距離に敵がいる場合は極めて危険
                } else {
                    score += Math.min(minEnemyDist, 60.0) * 12.0;
                }
            } else {
                score += 300.0; // 敵が生存していない場合は安全
            }

            // 2. 味方プレイヤーとの距離（合流支援）
            double minAllyDistSq = Double.MAX_VALUE;
            for (ServerPlayer ally : allies) {
                double distSq = candidate.distSqr(ally.blockPosition());
                if (distSq < minAllyDistSq) minAllyDistSq = distSq;
            }
            if (!allies.isEmpty()) {
                double minAllyDist = Math.sqrt(minAllyDistSq);
                if (minAllyDist >= 10.0 && minAllyDist <= 35.0) {
                    score += 150.0; // 適切な合流距離
                } else if (minAllyDist < 6.0) {
                    score -= 50.0; // 近すぎて味方と重なる
                }
            }

            // 3. 拠点（Hill）へのアクセス性
            double hillDist = Math.sqrt(candidate.distSqr(hillCenter));
            if (hillDist >= 15.0 && hillDist <= 45.0) {
                score += 120.0; // 拠点へスムーズに復帰可能
            } else if (hillDist > 80.0) {
                score -= 100.0; // 遠すぎる
            }

            // 4. 直近スポーン履歴ペナルティ（分散）
            if (recentSpawns.containsKey(candidate)) {
                int spawnTick = recentSpawns.get(candidate);
                if (now - spawnTick < RECENT_SPAWN_EXPIRY_TICKS) {
                    score -= 120.0;
                }
            }

            // 5. 微小ランダム揺らぎ（僅差時の分散）
            score += random.nextDouble() * 25.0;

            if (score > bestScore) {
                bestScore = score;
                bestCandidate = candidate;
            }
        }

        recordRecentSpawn(bestCandidate, server);
        return bestCandidate;
    }

    private void recordRecentSpawn(BlockPos pos, MinecraftServer server) {
        recentSpawns.put(pos.immutable(), server.getTickCount());
    }

    private void cleanupRecentSpawns(int now) {
        recentSpawns.entrySet().removeIf(entry -> now - entry.getValue() > RECENT_SPAWN_EXPIRY_TICKS);
    }

    public void clear() {
        recentSpawns.clear();
    }
}
