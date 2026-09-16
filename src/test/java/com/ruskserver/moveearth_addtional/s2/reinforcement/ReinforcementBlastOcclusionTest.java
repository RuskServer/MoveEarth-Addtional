package com.ruskserver.moveearth_addtional.s2.reinforcement;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReinforcementBlastOcclusionTest {
    @Test
    void frontWallShieldsBlocksBehindItButNotItself() {
        Set<ReinforcementBlastOcclusion.Cell> wall = Set.of(
                new ReinforcementBlastOcclusion.Cell(1, 0, 0));
        assertFalse(ReinforcementBlastOcclusion.blocked(
                0.5D, 0.5D, 0.5D, 1, 0, 0, wall::contains));
        assertTrue(ReinforcementBlastOcclusion.blocked(
                0.5D, 0.5D, 0.5D, 2, 0, 0, wall::contains));
    }

    @Test
    void aReinforcedImpactBlockShieldsTheBlocksBehindIt() {
        Set<ReinforcementBlastOcclusion.Cell> wall = Set.of(
                new ReinforcementBlastOcclusion.Cell(1, 0, 0));
        assertTrue(ReinforcementBlastOcclusion.blocked(
                1.5D, 0.5D, 0.5D, 3, 0, 0, wall::contains));
    }

    @Test
    void unobstructedAndOffAxisWallsDoNotBlock() {
        Set<ReinforcementBlastOcclusion.Cell> wall = Set.of(
                new ReinforcementBlastOcclusion.Cell(1, 1, 0));
        assertFalse(ReinforcementBlastOcclusion.blocked(
                0.5D, 0.5D, 0.5D, 3, 0, 0, wall::contains));
    }
}
