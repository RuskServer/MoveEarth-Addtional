package com.ruskserver.moveearth_addtional.handler;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class RandomSpawnMappingPolicyTest {
    @Test
    void candidatesStayInsideConfiguredRingAndUseChunkCenters() {
        for (int index = 0; index < 1_000; index++) {
            var column = RandomSpawnMappingPolicy.column(index, 100, -200, 750, 4_000);
            double distance = Math.hypot(column.x() - 100, column.z() + 200);
            assertTrue(distance >= 730.0D && distance <= 4_020.0D);
            assertEquals(8, column.x() & 15);
            assertEquals(8, column.z() & 15);
        }
    }

    @Test
    void earlyCandidatesDoNotCollapseIntoOneChunk() {
        Set<String> chunks = new HashSet<>();
        for (int index = 0; index < 128; index++) {
            var column = RandomSpawnMappingPolicy.column(index, 0, 0, 750, 4_000);
            chunks.add((column.x() >> 4) + ":" + (column.z() >> 4));
        }
        assertTrue(chunks.size() >= 120);
    }

    @Test
    void scanBudgetIsBounded() {
        assertFalse(RandomSpawnMappingPolicy.exhausted(255, 8));
        assertTrue(RandomSpawnMappingPolicy.exhausted(256, 8));
        assertTrue(RandomSpawnMappingPolicy.exhausted(1_600, 100));
    }
}
