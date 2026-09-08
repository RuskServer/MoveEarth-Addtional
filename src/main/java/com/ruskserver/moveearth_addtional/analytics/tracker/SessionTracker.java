package com.ruskserver.moveearth_addtional.analytics.tracker;

import com.ruskserver.moveearth_addtional.analytics.activity.PlayerActivityTracker;
import com.ruskserver.moveearth_addtional.analytics.model.SessionRecord;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * プレイヤーのログインセッションおよびオンライン・アクティブ・AFK時間の積算を管理するトラッカー
 */
public class SessionTracker {

    public static final SessionTracker INSTANCE = new SessionTracker();

    public static class ActiveSession {
        private final UUID sessionId;
        private final UUID playerUuid;
        private final String playerName;
        private final long loginTimeMs;
        private volatile long lastAccountingTimeMs;
        private volatile int activeSeconds = 0;
        private volatile int afkSeconds = 0;

        public ActiveSession(UUID sessionId, UUID playerUuid, String playerName, long loginTimeMs) {
            this.sessionId = sessionId;
            this.playerUuid = playerUuid;
            this.playerName = playerName;
            this.loginTimeMs = loginTimeMs;
            this.lastAccountingTimeMs = loginTimeMs;
        }

        public UUID getSessionId() {
            return sessionId;
        }

        public UUID getPlayerUuid() {
            return playerUuid;
        }

        public String getPlayerName() {
            return playerName;
        }

        public long getLoginTimeMs() {
            return loginTimeMs;
        }

        public int getActiveSeconds() {
            return activeSeconds;
        }

        public int getAfkSeconds() {
            return afkSeconds;
        }

        public int getOnlineSeconds(long currentTimeMs) {
            return (int) Math.max(0L, (currentTimeMs - loginTimeMs) / 1000L);
        }

        public synchronized void updateAccounting(boolean isAfk, long currentTimeMs) {
            long elapsedMs = Math.max(0L, currentTimeMs - lastAccountingTimeMs);
            int elapsedSec = (int) (elapsedMs / 1000L);
            if (elapsedSec > 0) {
                if (isAfk) {
                    afkSeconds += elapsedSec;
                } else {
                    activeSeconds += elapsedSec;
                }
                lastAccountingTimeMs += elapsedSec * 1000L;
            }
        }
    }

    private final Map<UUID, ActiveSession> sessions = new ConcurrentHashMap<>();

    public SessionTracker() {
    }

    /**
     * ログイン時のセッション開始
     */
    public ActiveSession onLogin(UUID playerUuid, String playerName, long currentTimeMs) {
        UUID sessionId = UUID.randomUUID();
        ActiveSession session = new ActiveSession(sessionId, playerUuid, playerName, currentTimeMs);
        sessions.put(playerUuid, session);
        return session;
    }

    /**
     * ログアウト時のセッション終了およびレコード生成
     */
    @Nullable
    public SessionRecord onLogout(UUID playerUuid, PlayerActivityTracker activityTracker, long currentTimeMs) {
        ActiveSession session = sessions.remove(playerUuid);
        if (session == null) {
            return null;
        }

        boolean isAfk = activityTracker.isAfk(playerUuid, currentTimeMs);
        session.updateAccounting(isAfk, currentTimeMs);

        int onlineSec = session.getOnlineSeconds(currentTimeMs);
        return new SessionRecord(
                session.getSessionId(),
                session.getPlayerUuid(),
                session.getPlayerName(),
                session.getLoginTimeMs() / 1000L,
                currentTimeMs / 1000L,
                onlineSec,
                session.getActiveSeconds(),
                session.getAfkSeconds()
        );
    }

    /**
     * 周期的なアクティブ/AFK時間の積算更新
     */
    public void updateAllAccounting(PlayerActivityTracker activityTracker, long currentTimeMs) {
        for (Map.Entry<UUID, ActiveSession> entry : sessions.entrySet()) {
            UUID playerUuid = entry.getKey();
            ActiveSession session = entry.getValue();
            boolean isAfk = activityTracker.isAfk(playerUuid, currentTimeMs);
            session.updateAccounting(isAfk, currentTimeMs);
        }
    }

    @Nullable
    public ActiveSession getSession(UUID playerUuid) {
        return sessions.get(playerUuid);
    }

    public Map<UUID, ActiveSession> getAllActiveSessions() {
        return java.util.Collections.unmodifiableMap(sessions);
    }

    public void clear() {
        sessions.clear();
    }
}
