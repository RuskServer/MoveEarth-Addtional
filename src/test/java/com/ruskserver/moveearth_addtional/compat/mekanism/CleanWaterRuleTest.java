package com.ruskserver.moveearth_addtional.compat.mekanism;

import com.ruskserver.moveearth_addtional.compat.mekanism.CleanWaterRule.Water;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CleanWaterRuleTest {
    private final Map<String, Water> world = new HashMap<>();
    private final CleanWaterRule.Lookup lookup =
            (x, y, z) -> world.getOrDefault(x + "," + y + "," + z, Water.NONE);

    private void put(int x, int y, int z, Water water) {
        world.put(x + "," + y + "," + z, water);
    }

    @Test
    void worldWaterNeverWashes() {
        for (int x = -3; x <= 3; x++) for (int z = -3; z <= 3; z++) put(x, 0, z, Water.OTHER_SOURCE);
        assertFalse(CleanWaterRule.clean(lookup, 0, 0, 0));
        put(5, 0, 0, Water.FLOWING);
        put(4, 0, 0, Water.FLOWING);
        assertFalse(CleanWaterRule.clean(lookup, 5, 0, 0), "a river's current is no cleaner");
    }

    @Test
    void aPlacedSourceAndTheWaterItFeedsWash() {
        put(0, 0, 0, Water.PLACED_SOURCE);
        put(1, 0, 0, Water.FLOWING);
        put(0, 0, 1, Water.OTHER_SOURCE); // formed between two placed sources
        put(0, 0, 2, Water.PLACED_SOURCE);
        assertTrue(CleanWaterRule.clean(lookup, 0, 0, 0));
        assertTrue(CleanWaterRule.clean(lookup, 1, 0, 0));
        assertTrue(CleanWaterRule.clean(lookup, 0, 0, 1));
    }

    @Test
    void aBucketPouredBesideTheSeaDoesNotCleanTheSea() {
        put(0, 0, 0, Water.PLACED_SOURCE);
        put(1, 0, 0, Water.OTHER_SOURCE);
        put(2, 0, 0, Water.OTHER_SOURCE);
        assertFalse(CleanWaterRule.clean(lookup, 1, 0, 0));
    }

    @Test
    void aShowerWashesFromUpToFourBlocksBelow() {
        put(0, 4, 0, Water.PLACED_SOURCE);
        for (int y = 0; y < 4; y++) put(0, y, 0, Water.FLOWING);
        assertTrue(CleanWaterRule.clean(lookup, 0, 0, 0));
        put(0, 8, 0, Water.PLACED_SOURCE);
        for (int y = 5; y < 8; y++) put(0, y, 0, Water.FLOWING);
        put(0, 4, 0, Water.FLOWING);
        assertFalse(CleanWaterRule.clean(lookup, 0, 0, 0), "too far below the source");
    }
}
