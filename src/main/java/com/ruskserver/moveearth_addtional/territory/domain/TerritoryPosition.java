package com.ruskserver.moveearth_addtional.territory.domain;

import java.util.Objects;

public record TerritoryPosition(String dimensionId, double x, double y, double z) {
    public TerritoryPosition {
        Objects.requireNonNull(dimensionId, "dimensionId");
        if (dimensionId.isBlank()) {
            throw new IllegalArgumentException("dimensionId must not be blank");
        }
        requireFinite(x, "x");
        requireFinite(y, "y");
        requireFinite(z, "z");
    }

    public double distanceTo(TerritoryPosition other, DistanceModel model) {
        Objects.requireNonNull(other, "other");
        Objects.requireNonNull(model, "model");
        if (!dimensionId.equals(other.dimensionId)) {
            return Double.POSITIVE_INFINITY;
        }

        double dx = x - other.x;
        double dz = z - other.z;
        if (model == DistanceModel.CYLINDER_2D) {
            return Math.sqrt(dx * dx + dz * dz);
        }
        double dy = y - other.y;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    private static void requireFinite(double value, String name) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(name + " must be finite");
        }
    }
}
