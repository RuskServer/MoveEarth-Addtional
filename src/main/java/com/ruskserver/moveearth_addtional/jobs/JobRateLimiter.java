package com.ruskserver.moveearth_addtional.jobs;

import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Per-player soft cap: XP beyond the one-minute budget is reduced to ten percent. */
final class JobRateLimiter {
    static final int WINDOW_TICKS = 20 * 60;
    static final int FULL_RATE_XP_PER_WINDOW = 500;
    private static final double OVERFLOW_MULTIPLIER = 0.10D;
    private final Map<Key, Window> windows = new HashMap<>();

    int apply(UUID playerId, ResourceLocation jobId, int requested, long gameTime) {
        if (requested <= 0) {
            return 0;
        }
        Key key = new Key(playerId, jobId);
        Window window = windows.computeIfAbsent(key, ignored -> new Window(gameTime));
        if (gameTime < window.startedAt || gameTime - window.startedAt >= WINDOW_TICKS) {
            window.startedAt = gameTime;
            window.fullRateXp = 0;
        }

        int fullRate = Math.min(requested, Math.max(0, FULL_RATE_XP_PER_WINDOW - window.fullRateXp));
        int overflow = requested - fullRate;
        window.fullRateXp += fullRate;
        return fullRate + (int) Math.ceil(overflow * OVERFLOW_MULTIPLIER);
    }

    void clear() {
        windows.clear();
    }

    private record Key(UUID playerId, ResourceLocation jobId) {
    }

    private static final class Window {
        private long startedAt;
        private int fullRateXp;

        private Window(long startedAt) {
            this.startedAt = startedAt;
        }
    }
}
