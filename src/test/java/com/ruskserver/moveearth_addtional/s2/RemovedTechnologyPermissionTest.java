package com.ruskserver.moveearth_addtional.s2;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RemovedTechnologyPermissionTest {
    @Test
    void removedTechnologyBitDoesNotGrantAnyPermissionOrShiftLaterBits() {
        assertEquals(Set.of(), S2Permission.fromMask(1L << 10));
        assertEquals(1L << 11, S2Permission.MANAGE_PRISONERS.mask());
        assertEquals(1L << 12, S2Permission.MANAGE_DISPATCH.mask());
        assertEquals(Set.of(S2Permission.MANAGE_PRISONERS),
                S2Permission.fromMask((1L << 10) | (1L << 11)));
    }
}
