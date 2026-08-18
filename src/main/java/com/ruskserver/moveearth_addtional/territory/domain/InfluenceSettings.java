package com.ruskserver.moveearth_addtional.territory.domain;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

public record InfluenceSettings(
        double basePower,
        double industrialWeight,
        double distanceFalloff,
        double contestMargin,
        double maxRaidSuppression,
        DistanceModel distanceModel,
        Map<ProtectionAction, Double> protectionThresholds
) {
    public InfluenceSettings {
        requireNonNegativeFinite(basePower, "basePower");
        requireNonNegativeFinite(industrialWeight, "industrialWeight");
        requirePositiveFinite(distanceFalloff, "distanceFalloff");
        requireNonNegativeFinite(contestMargin, "contestMargin");
        requireNonNegativeFinite(maxRaidSuppression, "maxRaidSuppression");
        Objects.requireNonNull(distanceModel, "distanceModel");
        Objects.requireNonNull(protectionThresholds, "protectionThresholds");

        EnumMap<ProtectionAction, Double> copiedThresholds = new EnumMap<>(ProtectionAction.class);
        protectionThresholds.forEach((action, threshold) -> {
            Objects.requireNonNull(action, "protection action");
            Objects.requireNonNull(threshold, "protection threshold");
            requireNonNegativeFinite(threshold, "protection threshold");
            copiedThresholds.put(action, threshold);
        });
        protectionThresholds = Map.copyOf(copiedThresholds);
    }

    public double thresholdFor(ProtectionAction action) {
        return protectionThresholds.getOrDefault(action, Double.POSITIVE_INFINITY);
    }

    private static void requireNonNegativeFinite(double value, String name) {
        if (!Double.isFinite(value) || value < 0.0D) {
            throw new IllegalArgumentException(name + " must be finite and non-negative");
        }
    }

    private static void requirePositiveFinite(double value, String name) {
        if (!Double.isFinite(value) || value <= 0.0D) {
            throw new IllegalArgumentException(name + " must be finite and positive");
        }
    }
}
