package com.ruskserver.moveearth_addtional.worldgen.postprocess;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PostProcessRuleTest {
    @Test
    void aFullChunkWithMarkedBlocksNeedsThePass() {
        assertTrue(PostProcessRule.needed("minecraft:full", new int[] {0, 0, 3, 0}));
    }

    @Test
    void finishedOrUnfinishedChunksAreLeftAlone() {
        assertFalse(PostProcessRule.needed("minecraft:full", new int[] {0, 0, 0}), "already processed");
        assertFalse(PostProcessRule.needed("minecraft:features", new int[] {5}), "not fully generated");
        assertFalse(PostProcessRule.needed("", new int[] {5}), "missing status");
        assertFalse(PostProcessRule.anyWaiting(null));
    }
}
