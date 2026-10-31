package com.ruskserver.moveearth_addtional.s2.provenance;

import com.ruskserver.moveearth_addtional.analytics.provenance.MutationRejectionBuffer;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MutationRejectionBufferTest {
    @Test void preservesCountsAndFirstLocationUnderRepeatedAttempts() {
        var buffer = new MutationRejectionBuffer();
        for (int i = 0; i < 100_000; i++) buffer.add(120 + i % 60, "cbc.explosion", "minecraft:overworld", i, 64, 2);
        var batches = buffer.drain();
        assertEquals(1, batches.size());
        assertEquals(100_000L, batches.getFirst().count());
        assertEquals(120, batches.getFirst().occurredAt());
        assertEquals(0, batches.getFirst().x());
        assertTrue(buffer.drain().isEmpty());
    }

    @Test void separatesRoutesAndDimensionsWithoutLocationCardinality() {
        var buffer = new MutationRejectionBuffer();
        buffer.add(100, "cbc.explosion", "a", 1, 2, 3);
        buffer.add(100, "cbc.explosion", "b", 1, 2, 3);
        buffer.add(100, "level.replace", "a", 1, 2, 3);
        assertEquals(3, buffer.drain().size());
    }

    @Test void capsMemoryWithoutDroppingOverflowCounts() {
        var buffer = new MutationRejectionBuffer();
        for (int i = 0; i < 10_000; i++) buffer.add(120, "level.replace", "dim" + i, i, 64, 0);
        var batches = buffer.drain();
        assertEquals(MutationRejectionBuffer.MAX_KEYS + 1, batches.size());
        assertEquals(10_000L, batches.stream().mapToLong(MutationRejectionBuffer.Batch::count).sum());
        assertEquals(10_000L - MutationRejectionBuffer.MAX_KEYS,
                batches.stream().filter(b -> b.route().equals("overflow")).findFirst().orElseThrow().count());
        buffer.add(180, "cbc.explosion", "a", 0, 1, 2);
        assertEquals(1, buffer.drain().size());
    }
}
