package com.ruskserver.moveearth_addtional.s2.reinforcement;

import org.junit.jupiter.api.Test;

import java.util.HashSet;

import static org.junit.jupiter.api.Assertions.*;

class ReinforcementBrushPatternTest {
    @Test
    void fiveByFiveBrushContainsTwentyFiveUniqueOffsets() {
        var offsets = ReinforcementBrushPattern.offsets(ReinforcementBrushPattern.Axis.Z, 2);
        assertEquals(25, offsets.size());
        assertEquals(25, new HashSet<>(offsets).size());
        assertTrue(offsets.stream().allMatch(offset -> offset.z() == 0));
    }

    @Test
    void brushPlaneFollowsClickedFaceAxis() {
        assertTrue(ReinforcementBrushPattern.offsets(ReinforcementBrushPattern.Axis.X, 1)
                .stream().allMatch(offset -> offset.x() == 0));
        assertTrue(ReinforcementBrushPattern.offsets(ReinforcementBrushPattern.Axis.Y, 1)
                .stream().allMatch(offset -> offset.y() == 0));
    }

    @Test
    void radiusAndSizeAreServerSafe() {
        assertEquals(0, ReinforcementBrushPattern.clamp(-100));
        assertEquals(2, ReinforcementBrushPattern.clamp(100));
        assertEquals(1, ReinforcementBrushPattern.size(0));
        assertEquals(5, ReinforcementBrushPattern.size(2));
    }
}
