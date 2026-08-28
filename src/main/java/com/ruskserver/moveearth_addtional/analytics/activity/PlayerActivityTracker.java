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

    public static class PlayerState {
        private final long sessionStartTimeMs;
        private long lastActiveTimeMs;
        private double lastX;
        private double lastY;
        private double lastZ;
        private boolean hasPos;

        public PlayerState(long sessionStartTimeMs, double x, double y, double z) {
            this.sessionStartTimeMs = sessionStartTimeMs;
            this.lastActiveTimeMs = sessionStartTimeMs;
            this.lastX = x;
            this.lastY = y;
            this.lastZ = z;
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

        public void setPos(double x, double y, double z) {
            this.lastX = x;
            this.lastY = y;
            this.lastZ = z;
            this.hasPos = true;
        }
    }

    private final Map<UUID, PlayerState> playerStates = new ConcurrentHashMap<>();

    public PlayerActivityTracker() {
    }

    /**
     * プレイヤーがログインしたときの初期化 (Vec3)
     */
    public void onPlayerLogin(UUID playerUuid, Vec3 initialPos, long currentTimeMs) {
        if (initialPos != null) {
            onPlayerLogin(playerUuid, initialPos.x, initialPos.y, initialPos.z, currentTimeMs);
        } else {
            playerStates.put(playerUuid, new PlayerState(currentTimeMs));
        }
    }

    /**
     * プレイヤーがログインしたときの初期化 (double座標)
     */
    public void onPlayerLogin(UUID playerUuid, double x, double y, double z, long currentTimeMs) {
        playerStates.put(playerUuid, new PlayerState(currentTimeMs, x, y, z));
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
    public boolean updatePosition(UUID playerUuid, Vec3 currentPos, long currentTimeMs) {
        if (currentPos == null) {
            return false;
        }
        return updatePosition(playerUuid, currentPos.x, currentPos.y, currentPos.z, currentTimeMs);
    }

    /**
     * 移動による活動更新判定（2ブロック以上の移動で活動状態を更新）
     *
     * @return 2ブロック以上の有効移動があった場合は true
     */
    public boolean updatePosition(UUID playerUuid, double x, double y, double z, long currentTimeMs) {
        PlayerState state = playerStates.get(playerUuid);
        if (state == null) {
            PlayerState newState = new PlayerState(currentTimeMs, x, y, z);
            playerStates.put(playerUuid, newState);
            return true;
        }

        if (!state.hasPos()) {
            state.setPos(x, y, z);
            state.setLastActiveTimeMs(currentTimeMs);
            return true;
        }

        double dx = x - state.getLastX();
        double dy = y - state.getLastY();
        double dz = z - state.getLastZ();
        double distSqr = dx * dx + dy * dy + dz * dz;

        if (distSqr >= AnalyticsConfig.MOVEMENT_THRESHOLD_SQR) {
            state.setPos(x, y, z);
            state.setLastActiveTimeMs(currentTimeMs);
            return true;
        }

        return false;
    }

    /**
     * 移動による活動更新判定および移動距離（ブロック数）の取得
     *
     * @return 移動距離（ブロック数）。初回や未移動時は 0.0
     */
    public double updatePositionAndGetDistance(UUID playerUuid, double x, double y, double z, long currentTimeMs) {
        PlayerState state = playerStates.get(playerUuid);
        if (state == null) {
            PlayerState newState = new PlayerState(currentTimeMs, x, y, z);
            playerStates.put(playerUuid, newState);
            return 0.0D;
        }

        if (!state.hasPos()) {
            state.setPos(x, y, z);
            state.setLastActiveTimeMs(currentTimeMs);
            return 0.0D;
        }

        double dx = x - state.getLastX();
        double dy = y - state.getLastY();
        double dz = z - state.getLastZ();
        double distSqr = dx * dx + dy * dy + dz * dz;

        if (distSqr >= AnalyticsConfig.MOVEMENT_THRESHOLD_SQR) {
            double distance = Math.sqrt(distSqr);
            state.setPos(x, y, z);
            state.setLastActiveTimeMs(currentTimeMs);
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
