package com.ruskserver.moveearth_addtional.territory.data;

import com.ruskserver.moveearth_addtional.territory.domain.TerritoryOwnerId;

import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

public final class IndustrialScoreRegistry {
    private final Map<TerritoryOwnerId, Double> scores = new TreeMap<>();

    public double get(TerritoryOwnerId ownerId) {
        return scores.getOrDefault(Objects.requireNonNull(ownerId, "ownerId"), 0.0D);
    }

    public boolean set(TerritoryOwnerId ownerId, double score) {
        Objects.requireNonNull(ownerId, "ownerId");
        if (!Double.isFinite(score) || score < 0.0D) {
            throw new IllegalArgumentException("score must be finite and non-negative");
        }
        Double previous = scores.put(ownerId, score);
        return previous == null || Double.compare(previous, score) != 0;
    }

    public Map<TerritoryOwnerId, Double> entries() {
        return Map.copyOf(scores);
    }
}
