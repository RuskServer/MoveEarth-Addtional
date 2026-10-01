package com.ruskserver.moveearth_addtional.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ReinforcementRenderBudgetTest {
    @Test
    void compatibilityPathIsCappedRegardlessOfMode() {
        assertEquals(4096, ReinforcementRenderBudget.faceLimit(false, false));
        assertEquals(4096, ReinforcementRenderBudget.faceLimit(false, true));
    }

    @Test
    void instancedPathKeepsItsPassiveAndDetailedLimits() {
        assertEquals(4096 * 6, ReinforcementRenderBudget.faceLimit(true, false));
        assertEquals(8192 * 6, ReinforcementRenderBudget.faceLimit(true, true));
        assertEquals(ReinforcementRenderBudget.INSTANCED_FACE_CAPACITY,
                ReinforcementRenderBudget.faceLimit(true, true));
    }

    @Test
    void keysSortNearestFirstAndKeepTheirIndex() {
        double[] distances = {900.0D, 0.0D, 16.5D, 4624.0D, 16.5D};
        long[] keys = new long[distances.length + 3];
        for (int index = 0; index < distances.length; index++) {
            keys[index] = ReinforcementRenderBudget.key(distances[index], index);
        }

        ReinforcementRenderBudget.sortNearestFirst(keys, distances.length);

        int[] order = new int[distances.length];
        for (int index = 0; index < order.length; index++) order[index] = ReinforcementRenderBudget.index(keys[index]);
        assertArrayEquals(new int[]{1, 2, 4, 0, 3}, order);
    }

    @Test
    void negativeAndNanDistancesStillSort() {
        long[] keys = {
                ReinforcementRenderBudget.key(Double.NaN, 0),
                ReinforcementRenderBudget.key(-3.0D, 1),
                ReinforcementRenderBudget.key(10.0D, 2)
        };

        ReinforcementRenderBudget.sortNearestFirst(keys, keys.length);

        assertEquals(1, ReinforcementRenderBudget.index(keys[0]));
        assertEquals(2, ReinforcementRenderBudget.index(keys[1]));
        assertEquals(0, ReinforcementRenderBudget.index(keys[2]));
    }
}
