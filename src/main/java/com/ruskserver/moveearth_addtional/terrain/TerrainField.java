package com.ruskserver.moveearth_addtional.terrain;

import java.util.Locale;
import java.util.Optional;

/**
 * The fields a {@code map_field} density function can read.
 *
 * <p>Each one states how the stored layer becomes the value the noise router
 * expects, and what to return where no tile covers the position.
 *
 * <p>Deliberately free of Minecraft types so the mapping can be unit tested; the
 * codec that turns these names into a registry entry lives in
 * {@link MapFieldDensityFunction}.
 */
public enum TerrainField {
    /**
     * Base height as a vanilla {@code overworld/offset} value.
     *
     * <p>Vanilla's depth is {@code y_clamped_gradient(1.5 at -64 .. -1.5 at 320) + offset},
     * so the surface sits near {@code y = 128 + 128 * offset}. Overriding offset
     * therefore steers the whole vanilla shaping pipeline -- depth, sloped
     * cheese, final density -- without touching caves, aquifers or surface rules.
     */
    OFFSET("height", -1.6, 1.4, -1.35),
    /**
     * Continentalness, taken from distance to the shore rather than height.
     *
     * <p>Vanilla's parameter is effectively how far inland you are, and its band
     * edges are cut that way. Deriving it from elevation instead skipped the
     * coastal bands entirely: shallow water read as mid inland and no beach ever
     * generated.
     */
    CONTINENTS("continentalness", -1.0, 1.0, -1.0),
    /** Local roughness, mapped into the range vanilla's erosion parameter uses. */
    EROSION("erosion", -1.0, 1.0, 0.7),
    /**
     * River gate in 0..1: zero on a channel, one away from it.
     *
     * <p>Multiplied into a weirdness noise in the datapack rather than used as
     * weirdness directly. Vanilla picks river biomes where weirdness is near
     * zero, but it also uses weirdness to choose between biome variants, so
     * pinning it to a constant away from rivers would collapse the biome
     * variety of the whole world into one variant class.
     */
    RIVER_GATE("river_dist", 0.0, 1.0, 1.0),
    TEMPERATURE("temperature", -1.0, 1.0, 0.0),
    VEGETATION("humidity", -1.0, 1.0, 0.55),
    /** Raw world Y of the surface. For the region system and for diagnostics. */
    SURFACE_Y("height", -64.0, 320.0, -40.0),
    /** Vanilla's shaping factor, raised inside channels. Computed from the tile. */
    FACTOR("height", 1.0, 64.0, 3.5),
    /** How much of vanilla's 3D noise survives: zero inside channels. */
    NOISE_GATE("height", 0.0, 1.0, 1.0),
    /** Vanilla's jaggedness, driven by the tile's roughness. Sharpens ridges. */
    JAGGEDNESS("erosion", 0.0, 1.0, 0.0);

    private final String layer;
    private final double min;
    private final double max;
    private final double outside;

    TerrainField(String layer, double min, double max, double outside) {
        this.layer = layer;
        this.min = min;
        this.max = max;
        this.outside = outside;
    }

    /** Name used in the density function JSON. */
    public String serializedName() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static Optional<TerrainField> byName(String name) {
        for (TerrainField field : values()) {
            if (field.serializedName().equals(name)) {
                return Optional.of(field);
            }
        }
        return Optional.empty();
    }

    public String layer() {
        return layer;
    }

    public double min() {
        return min;
    }

    public double max() {
        return max;
    }

    public double outside() {
        return outside;
    }

    /** Half-width of the river biome corridor, in blocks. */
    static final double RIVER_BIOME_RADIUS = 14.0;

    private static double clamp(double value, double low, double high) {
        return value < low ? low : Math.min(value, high);
    }

    /** Converts a raw layer value into the router-facing value. */
    public double convert(double raw, TerrainTile tile) {
        return convert(raw, tile.seaY(), tile.minY(), tile.maxY());
    }

    /** Tile-free form, so the mapping can be tested without loading a tile. */
    public double convert(double raw, int seaY, int minY, int maxY) {
        return switch (this) {
            case OFFSET -> (raw - 128.0) / 128.0;
            case SURFACE_Y, FACTOR, NOISE_GATE, JAGGEDNESS -> raw;
            case CONTINENTS -> clamp(raw / 127.0, -1.0, 1.0);
            // int8 layers arrive as -127..127 and 0..127
            case TEMPERATURE -> clamp(raw / 127.0, -1.0, 1.0);
            // stored signed: the generator maps humidity onto vanilla's own
            // vegetation band edges, and the dry half of those is negative
            case VEGETATION -> clamp(raw / 127.0, -1.0, 1.0);
            case EROSION -> clamp(raw / 127.0, -1.0, 1.0);
            // distance to the channel centre, in blocks. Vanilla places river
            // biomes where weirdness is near zero, so the corridor is this wide.
            case RIVER_GATE -> {
                double t = clamp(raw / RIVER_BIOME_RADIUS, 0.0, 1.0);
                yield t * t * (3.0 - 2.0 * t);
            }
        };
    }
}
