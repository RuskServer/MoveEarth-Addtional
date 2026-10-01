package com.ruskserver.moveearth_addtional.nether;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NetherGateWavesTest {
    @Test
    void threeWavesGrowingToEightEnemies() {
        assertEquals(3, NetherGateWaves.count());
        assertEquals(5, NetherGateWaves.spawnOrder(0, 1.0D).size());
        assertEquals(5, NetherGateWaves.spawnOrder(1, 1.0D).size());
        assertEquals(8, NetherGateWaves.spawnOrder(2, 1.0D).size());
    }

    @Test
    void multiplierScalesEachKindButNeverRemovesOne() {
        assertEquals(6, NetherGateWaves.wave(0, 2.0D).get(NetherGateWaves.Kind.WITHER_SKELETON));
        assertTrue(NetherGateWaves.wave(2, 0.1D).values().stream().allMatch(count -> count == 1));
    }
}
