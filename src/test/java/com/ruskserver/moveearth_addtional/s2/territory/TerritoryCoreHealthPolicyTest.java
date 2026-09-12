package com.ruskserver.moveearth_addtional.s2.territory;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TerritoryCoreHealthPolicyTest {
    @Test
    void damageClampsAtZero() {
        assertEquals(70, TerritoryCoreHealthPolicy.damage(100, 30));
        assertEquals(0, TerritoryCoreHealthPolicy.damage(20, 30));
    }

    @Test
    void regenerationConsumesDelayThenHealsByConfiguredIntervals() {
        var waiting = TerritoryCoreHealthPolicy.advance(500, 1000, 100, 0,
                80, 20, 1.0D);
        assertEquals(500, waiting.health());
        assertEquals(20, waiting.delayTicks());

        var healing = TerritoryCoreHealthPolicy.advance(waiting.health(), 1000,
                waiting.delayTicks(), waiting.progressTicks(), 60, 20, 1.0D);
        assertEquals(520, healing.health());
        assertEquals(0, healing.delayTicks());
        assertEquals(0, healing.progressTicks());
    }

    @Test
    void depletedCoreDoesNotRegenerateBeforeSiegeResolution() {
        var result = TerritoryCoreHealthPolicy.advance(0, 1000, 0, 0,
                10_000, 20, 1.0D);
        assertEquals(0, result.health());
    }
}
