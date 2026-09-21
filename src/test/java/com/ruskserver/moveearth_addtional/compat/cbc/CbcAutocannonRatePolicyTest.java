package com.ruskserver.moveearth_addtional.compat.cbc;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CbcAutocannonRatePolicyTest {
    @Test
    void keepsStoppedAndActiveSelectorRange() {
        assertEquals(0, CbcAutocannonRatePolicy.normalizeSelector(-1));
        assertEquals(0, CbcAutocannonRatePolicy.normalizeSelector(0));
        assertEquals(11, CbcAutocannonRatePolicy.normalizeSelector(1));
        assertEquals(11, CbcAutocannonRatePolicy.normalizeSelector(7));
        assertEquals(11, CbcAutocannonRatePolicy.normalizeSelector(10));
        assertEquals(11, CbcAutocannonRatePolicy.normalizeSelector(11));
        assertEquals(15, CbcAutocannonRatePolicy.normalizeSelector(15));
        assertEquals(15, CbcAutocannonRatePolicy.normalizeSelector(100));
    }
}
