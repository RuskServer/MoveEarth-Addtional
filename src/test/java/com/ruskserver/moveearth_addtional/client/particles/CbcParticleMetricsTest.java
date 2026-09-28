package com.ruskserver.moveearth_addtional.client.particles;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CbcParticleMetricsTest {
    @Test
    void sortsByCumulativeUpdateCostAndSeparatesCreationAndDiscard() {
        CbcParticleMetrics metrics = new CbcParticleMetrics();
        metrics.created("cbc.Smoke", false);
        metrics.created("cbc.Smoke", true);
        metrics.updated("cbc.Smoke", 1_000_000);
        metrics.updated("cbc.Emitter", 2_000_000);
        var lines = metrics.summary();
        assertEquals(2, lines.size());
        assertEquals("Emitter", lines.getFirst().type());
        assertEquals(new CbcParticleMetrics.Summary("Smoke", 2, 1, 1, 1_000_000, 0), lines.get(1));
    }

    @Test
    void emptyAndFreshMetricsHaveNoHistory() {
        assertTrue(new CbcParticleMetrics().summary().isEmpty());
    }

    @Test
    void reportsAtMostEightTypes() {
        CbcParticleMetrics metrics = new CbcParticleMetrics();
        for (int index = 0; index < 12; index++) metrics.updated("cbc.Particle" + index, index);
        assertEquals(8, metrics.summary().size());
        assertEquals("Particle11", metrics.summary().getFirst().type());
    }

    @Test
    void recordsCollisionLodUpdatesWithoutChangingUpdateCount() {
        CbcParticleMetrics metrics = new CbcParticleMetrics();
        metrics.updated("cbc.Smoke", 100, true);
        metrics.updated("cbc.Smoke", 200, false);
        assertEquals(new CbcParticleMetrics.Summary("Smoke", 0, 0, 2, 300, 1), metrics.summary().getFirst());
    }
}
