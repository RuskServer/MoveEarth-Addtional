package com.ruskserver.moveearth_addtional.territory.domain;

import java.util.Objects;

public record InfluenceSource(TerritoryCore core, double industrialScore) {
    public InfluenceSource {
        Objects.requireNonNull(core, "core");
        if (!Double.isFinite(industrialScore) || industrialScore < 0.0D) {
            throw new IllegalArgumentException("industrialScore must be finite and non-negative");
        }
    }

    public double corePower(InfluenceSettings settings) {
        Objects.requireNonNull(settings, "settings");
        return settings.basePower() + industrialScore * settings.industrialWeight();
    }
}
