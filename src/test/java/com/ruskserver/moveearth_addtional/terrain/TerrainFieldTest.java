package com.ruskserver.moveearth_addtional.terrain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TerrainFieldTest {
    private static final int SEA_Y = 63;
    private static final int MIN_Y = -56;
    private static final int MAX_Y = 272;

    /**
     * Vanilla's depth is {@code y_clamped_gradient(1.5 at -64 .. -1.5 at 320) + offset},
     * so the surface lands near {@code y = 128 + 128 * offset}. The offset field
     * has to be the exact inverse or the generated terrain sits at the wrong height.
     */
    private static double surfaceY(double offset) {
        return 128.0 + 128.0 * offset;
    }

    @Test
    void offsetIsTheInverseOfVanillaDepth() {
        for (int y : new int[] {MIN_Y, 0, SEA_Y, 128, 200, MAX_Y}) {
            double offset = TerrainField.OFFSET.convert(y, SEA_Y, MIN_Y, MAX_Y);
            assertEquals(y, surfaceY(offset), 1.0E-9,
                    "offset must map back to the same world height for y=" + y);
        }
    }

    @Test
    void offsetStaysInsideTheDeclaredBounds() {
        for (int y = MIN_Y; y <= MAX_Y; y++) {
            double offset = TerrainField.OFFSET.convert(y, SEA_Y, MIN_Y, MAX_Y);
            assertTrue(offset >= TerrainField.OFFSET.min() && offset <= TerrainField.OFFSET.max(),
                    "offset " + offset + " for y=" + y + " escaped the declared range");
        }
    }

    @Test
    void continentalnessDecodesTheStoredCoastDistanceParameter() {
        // No longer derived from height: the generator writes vanilla's own
        // continentalness, mapped from distance to the shore, as a signed byte.
        assertEquals(-1.0, TerrainField.CONTINENTS.convert(-127.0, SEA_Y, MIN_Y, MAX_Y), 1.0E-9);
        assertEquals(0.0, TerrainField.CONTINENTS.convert(0.0, SEA_Y, MIN_Y, MAX_Y), 1.0E-9);
        assertEquals(1.0, TerrainField.CONTINENTS.convert(127.0, SEA_Y, MIN_Y, MAX_Y), 1.0E-9);
        assertEquals("continentalness", TerrainField.CONTINENTS.layer());
    }

    /**
     * Beaches only generate inside vanilla's coast band, so the decoded value
     * has to be able to land there at all.
     */
    @Test
    void continentalnessCanReachVanillaCoastBand() {
        double coastal = TerrainField.CONTINENTS.convert(-19.0, SEA_Y, MIN_Y, MAX_Y);
        assertTrue(coastal > -0.19 && coastal < -0.11,
                "expected a value inside the coast band, got " + coastal);
    }

    @Test
    void continentalnessRisesWithHeight() {
        double previous = Double.NEGATIVE_INFINITY;
        for (int y = MIN_Y; y <= MAX_Y; y += 8) {
            double value = TerrainField.CONTINENTS.convert(y, SEA_Y, MIN_Y, MAX_Y);
            assertTrue(value >= previous, "continentalness dipped at y=" + y);
            previous = value;
        }
    }

    /**
     * The gate multiplies a weirdness noise, so it has to be zero on a channel --
     * that is where vanilla places river biomes -- and one away from water, so
     * the noise survives and the rest of the world keeps its biome variety.
     *
     * <p>The input is the distance to the channel centre in blocks, not the
     * channel width: the tile carries a distance field precisely so that river
     * width stops being limited by tile resolution.
     */
    @Test
    void riverGateClosesOnTheChannelAndOpensAwayFromIt() {
        assertEquals(0.0, TerrainField.RIVER_GATE.convert(0.0, SEA_Y, MIN_Y, MAX_Y), 1.0E-9);
        assertEquals(1.0, TerrainField.RIVER_GATE.convert(200.0, SEA_Y, MIN_Y, MAX_Y), 1.0E-9);

        double near = TerrainField.RIVER_GATE.convert(8.0, SEA_Y, MIN_Y, MAX_Y);
        double far = TerrainField.RIVER_GATE.convert(32.0, SEA_Y, MIN_Y, MAX_Y);
        assertTrue(near < far, "the gate must open as the channel gets further away");
        assertTrue(near >= 0.0 && far <= 1.0);
    }

    @Test
    void climateChannelsDecodeSignedByteLayersIntoTheClimateRange() {
        assertEquals(-1.0, TerrainField.TEMPERATURE.convert(-127.0, SEA_Y, MIN_Y, MAX_Y), 1.0E-9);
        assertEquals(0.0, TerrainField.TEMPERATURE.convert(0.0, SEA_Y, MIN_Y, MAX_Y), 1.0E-9);
        assertEquals(1.0, TerrainField.TEMPERATURE.convert(127.0, SEA_Y, MIN_Y, MAX_Y), 1.0E-9);

        // humidity is signed now: the generator maps it onto vanilla's own
        // vegetation band edges, and the dry half of those is below zero
        assertEquals(-1.0, TerrainField.VEGETATION.convert(-127.0, SEA_Y, MIN_Y, MAX_Y), 1.0E-9);
        assertEquals(0.0, TerrainField.VEGETATION.convert(0.0, SEA_Y, MIN_Y, MAX_Y), 1.0E-9);
        assertEquals(1.0, TerrainField.VEGETATION.convert(127.0, SEA_Y, MIN_Y, MAX_Y), 1.0E-9);

        assertEquals(-1.0, TerrainField.EROSION.convert(-127.0, SEA_Y, MIN_Y, MAX_Y), 1.0E-9);
        assertEquals(1.0, TerrainField.EROSION.convert(127.0, SEA_Y, MIN_Y, MAX_Y), 1.0E-9);
    }

    @Test
    void everyFieldDeclaresBoundsThatContainItsOutsideValue() {
        for (TerrainField field : TerrainField.values()) {
            assertTrue(field.outside() >= Math.min(field.min(), field.outside()));
            assertTrue(field.outside() <= Math.max(field.max(), field.outside()));
            assertEquals(field.name().toLowerCase(java.util.Locale.ROOT), field.serializedName());
        }
    }
}
