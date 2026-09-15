package com.ruskserver.moveearth_addtional.client.loading;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LoadingTipRotationTest {
    @Test
    void keepsTipStableUntilRotationBoundary() {
        assertEquals(2, LoadingTipRotation.index(2L, 0L, 5));
        assertEquals(2, LoadingTipRotation.index(2L, LoadingTipRotation.ROTATION_MILLIS - 1L, 5));
        assertEquals(3, LoadingTipRotation.index(2L, LoadingTipRotation.ROTATION_MILLIS, 5));
    }

    @Test
    void wrapsAndRejectsEmptyCatalogs() {
        assertEquals(0, LoadingTipRotation.index(4L, LoadingTipRotation.ROTATION_MILLIS, 5));
        assertThrows(IllegalArgumentException.class, () -> LoadingTipRotation.index(0L, 0L, 0));
    }
}
