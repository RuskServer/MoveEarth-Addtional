package com.ruskserver.moveearth_addtional.s2.recovery;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;

/** First accepted attack fixes the recovery target for the lifetime of that Siege. */
public final class RecoveryWallBaselines {
    private final Map<UUID, Baseline> values = new LinkedHashMap<>();

    public record Baseline(int health, int blocks) {
        public Baseline {
            health = Math.max(0, health);
            blocks = Math.max(0, blocks);
        }
    }

    public boolean contains(UUID siege) { return values.containsKey(siege); }
    public Baseline get(UUID siege) { return values.get(siege); }
    public boolean record(UUID siege, int health, int blocks) {
        return values.putIfAbsent(siege, new Baseline(health, blocks)) == null;
    }
    public boolean prune(Predicate<UUID> ongoing) { return values.keySet().removeIf(id -> !ongoing.test(id)); }
    public Map<UUID, Baseline> snapshot() { return Map.copyOf(values); }
}
