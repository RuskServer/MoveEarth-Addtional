package com.ruskserver.moveearth_addtional.network;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class NetworkDecodeLimitsTest {
    @Test
    void acceptsBoundaryCounts() {
        assertEquals(0, NetworkDecodeLimits.checkedCount(0, 4, "entry"));
        assertEquals(4, NetworkDecodeLimits.checkedCount(4, 4, "entry"));
    }

    @Test
    void rejectsNegativeAndOversizedCounts() {
        assertThrows(IllegalArgumentException.class,
                () -> NetworkDecodeLimits.checkedCount(-1, 4, "entry"));
        assertThrows(IllegalArgumentException.class,
                () -> NetworkDecodeLimits.checkedCount(5, 4, "entry"));
    }

    @Test
    void listReaderRejectsOversizedCountBeforeReadingEntries() {
        AtomicInteger reads = new AtomicInteger();
        assertThrows(IllegalArgumentException.class,
                () -> NetworkDecodeLimits.readList(129, 128, "loadout", reads::incrementAndGet));
        assertEquals(0, reads.get());
    }

    @Test
    void listReaderPropagatesTruncatedPayloadFailure() {
        AtomicInteger reads = new AtomicInteger();
        assertThrows(IllegalStateException.class, () -> NetworkDecodeLimits.readList(2, 4, "loadout", () -> {
            if (reads.getAndIncrement() == 1) throw new IllegalStateException("truncated");
            return "first";
        }));
    }

    @Test
    void listReaderAcceptsMaximumCount() {
        AtomicInteger next = new AtomicInteger();
        assertEquals(List.of(0, 1, 2, 3),
                NetworkDecodeLimits.readList(4, 4, "entry", next::getAndIncrement));
    }
}
