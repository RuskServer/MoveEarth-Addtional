package com.ruskserver.moveearth_addtional.s2.reinforcement;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Per-player, per-block cooldown for valid normal-mining damage attempts. */
public final class ReinforcementDamageLimiter {
    private final long cooldownTicks;
    private final Map<Key, Long> lastDamageTicks = new HashMap<>();

    public ReinforcementDamageLimiter(long cooldownTicks) {
        if (cooldownTicks < 0) throw new IllegalArgumentException("Cooldown cannot be negative");
        this.cooldownTicks = cooldownTicks;
    }

    public boolean tryDamage(UUID playerId, String dimension, long blockPos, long gameTime) {
        Key key = new Key(playerId, dimension, blockPos);
        Long previous = lastDamageTicks.get(key);
        if (previous != null && gameTime - previous < cooldownTicks) return false;
        lastDamageTicks.put(key, gameTime);
        if (lastDamageTicks.size() > 8192) {
            long oldestAllowed = gameTime - Math.max(200L, cooldownTicks * 4L);
            lastDamageTicks.entrySet().removeIf(entry -> entry.getValue() < oldestAllowed);
        }
        return true;
    }

    public void clear() {
        lastDamageTicks.clear();
    }

    private record Key(UUID playerId, String dimension, long blockPos) {
    }
}
