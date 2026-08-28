package com.ruskserver.moveearth_addtional.analytics.activity;

import com.ruskserver.moveearth_addtional.analytics.config.AnalyticsConfig;
import net.minecraft.world.phys.Vec3;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * プレイヤーごとの活動状態およびAFK判定を管理するトラッカー
 */
public class PlayerActivityTracker {

    public static final PlayerActivityTracker INSTANCE = new PlayerActivityTracker();

    /** 1サンプリング（30秒）での最大有効徒歩・走行移動距離（これ以上はテレポート等とみなす） */
    public static final double MAX_WALKING_DISTANCE_PER_SAMPLE = 100.0D;

    public static class PlayerState {
        private final long sessionStartTimeMs;
        private long lastActiveTimeMs;
        private double lastX;
        private double lastY;
        private double lastZ;
        private String lastDimension;
        private boolean hasPos;

        public PlayerState(long sessionStartTimeMs, double x, double y, double z, String dimension) {
            this.sessionStartTimeMs = sessionStartTimeMs;
            this.lastActiveTimeMs = sessionStartTimeMs;
            this.lastX = x;
            this.lastY = y;
            this.lastZ = z;
            this.lastDimension = dimension;
            this.hasPos = true;
        }

        public PlayerState(long sessionStartTimeMs) {
            this.sessionStartTimeMs = sessionStartTimeMs;
            this.lastActiveTimeMs = sessionStartTimeMs;
            this.hasPos = false;
        }

        public long getSessionStartTimeMs() {
            return sessionStartTimeMs;
        }

        public long getLastActiveTimeMs() {
            return lastActiveTimeMs;
        }

        public void setLastActiveTimeMs(long lastActiveTimeMs) {
            this.lastActiveTimeMs = lastActiveTimeMs;
        }

        public boolean hasPos() {
            return hasPos;
        }

        public double getLastX() {
            return lastX;
        }

        public double getLastY() {
            return lastY;
        }

        public double getLastZ() {
            return lastZ;
        }

        public String getLastDimension() {
            return lastDimension;
        }

        public void setPos(double x, double y, double z, String dimension) {
            this.lastX = x;
            this.lastY = y;
            this.lastZ = z;
            this.lastDimension = dimension;
            this.hasPos = true;
        }
    }

    private final Map<UUID, PlayerState> playerStates = new ConcurrentHashMap<>();

    public PlayerActivityTracker() {
    }

    /**
     * プレイヤーがログインしたときの初期化 (Vec3)
     */
    public void onPlayerLogin(UUID playerUuid, Vec3 initialPos, String dimension, long currentTimeMs) {
        if (initialPos != null) {
            onPlayerLogin(playerUuid, initialPos.x, initialPos.y, initialPos.z, dimension, currentTimeMs);
        } else {
            playerStates.put(playerUuid, new PlayerState(currentTimeMs));
        }
    }

    /**
     * プレイヤーがログインしたときの初期化 (double座標)
     */
    public void onPlayerLogin(UUID playerUuid, double x, double y, double z, String dimension, long currentTimeMs) {
        playerStates.put(playerUuid, new PlayerState(currentTimeMs, x, y, z, dimension));
    }

    public void onPlayerLogin(UUID playerUuid, double x, double y, double z, long currentTimeMs) {
        onPlayerLogin(playerUuid, x, y, z, "minecraft:overworld", currentTimeMs);
    }

    /**
     * プレイヤーがログアウトしたときのクリーンアップ
     */
    public void onPlayerLogout(UUID playerUuid) {
        playerStates.remove(playerUuid);
    }

    /**
     * 各種アクション（ブロック破壊・設置・クラフト・戦闘等）による活動記録
     */
    public void recordActivity(UUID playerUuid, ActivityCategory category, long currentTimeMs) {
        PlayerState state = playerStates.get(playerUuid);
        if (state != null) {
            state.setLastActiveTimeMs(currentTimeMs);
        } else {
            PlayerState newState = new PlayerState(currentTimeMs);
            playerStates.put(playerUuid, newState);
        }
    }

    /**
     * 移動による活動更新判定（Vec3）
     */
    public boolean updatePosition(UUID playerUuid, Vec3 currentPos, String dimension, long currentTimeMs) {
        if (currentPos == null) {
            return false;
        }
        return updatePosition(playerUuid, currentPos.x, currentPos.y, currentPos.z, dimension, currentTimeMs);
    }

    public boolean updatePosition(UUID playerUuid, double x, double y, double z, long currentTimeMs) {
        return updatePosition(playerUuid, x, y, z, "minecraft:overworld", currentTimeMs);
    }

    /**
     * 移動による活動更新判定（2ブロック以上の移動で活動状態を更新）
     *
     * @return 2ブロック以上の有効移動があった場合は true
     */
    public boolean updatePosition(UUID playerUuid, double x, double y, double z, String dimension, long currentTimeMs) {
        return updatePositionAndGetDistance(playerUuid, x, y, z, dimension, currentTimeMs) > 0.0D;
    }

    public double updatePositionAndGetDistance(UUID playerUuid, double x, double y, double z, long currentTimeMs) {
        return updatePositionAndGetDistance(playerUuid, x, y, z, "minecraft:overworld", currentTimeMs);
    }

    /**
     * 移動による活動更新判定および移動距離（ブロック数）の取得
     * ディメンション変更時や100m以上の急激なジャンプ（テレポート等）は移動距離に加算せず除外
     *
     * @return 通常移動距離（ブロック数）。テレポート・異世界移動・初回サンプリング時は 0.0
     */
    public double updatePositionAndGetDistance(UUID playerUuid, double x, double y, double z, String dimension, long currentTimeMs) {
        PlayerState state = playerStates.get(playerUuid);
        if (state == null) {
            PlayerState newState = new PlayerState(currentTimeMs, x, y, z, dimension);
            playerStates.put(playerUuid, newState);
            return 0.0D;
        }

        if (!state.hasPos()) {
            state.setPos(x, y, z, dimension);
            state.setLastActiveTimeMs(currentTimeMs);
            return 0.0D;
        }

        // ディメンションが切り替わった場合（Netherゲートやアリーナ入場等）
        if (state.getLastDimension() != null && !state.getLastDimension().equals(dimension)) {
            state.setPos(x, y, z, dimension);
            state.setLastActiveTimeMs(currentTimeMs);
            return 0.0D; // 異ディメンション移動は距離加算なし
        }

        double dx = x - state.getLastX();
        double dy = y - state.getLastY();
        double dz = z - state.getLastZ();
        double distSqr = dx * dx + dy * dy + dz * dz;

        // 閾値（2ブロック）以上移動した場合
        if (distSqr >= AnalyticsConfig.MOVEMENT_THRESHOLD_SQR) {
            double distance = Math.sqrt(distSqr);
            state.setPos(x, y, z, dimension);
            state.setLastActiveTimeMs(currentTimeMs);

            // 100m以上の急激な座標ジャンプ（TPAやテレポート、死亡リスポーン）は移動距離から除外
            if (distance > MAX_WALKING_DISTANCE_PER_SAMPLE) {
                return 0.0D;
            }
            return distance;
        }

        return 0.0D;
    }

    /**
     * 指定時刻においてプレイヤーがAFK（無活動状態）であるかを判定
     */
    public boolean isAfk(UUID playerUuid, long currentTimeMs) {
        PlayerState state = playerStates.get(playerUuid);
        if (state == null) {
            return true;
        }
        return (currentTimeMs - state.getLastActiveTimeMs()) >= AnalyticsConfig.AFK_THRESHOLD_MS;
    }

    /**
     * 最終活動時刻を取得
     */
    public long getLastActiveTimeMs(UUID playerUuid) {
        PlayerState state = playerStates.get(playerUuid);
        return state != null ? state.getLastActiveTimeMs() : 0L;
    }

    /**
     * 状態マップをクリア
     */
    public void clear() {
        playerStates.clear();
    }
}
