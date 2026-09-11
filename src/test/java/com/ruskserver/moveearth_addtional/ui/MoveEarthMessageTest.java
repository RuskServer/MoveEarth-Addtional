package com.ruskserver.moveearth_addtional.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MoveEarthMessageTest {
    @Test
    void createsRecognizablePlainTextStructure() {
        assertEquals("[MoveEarth] >>> ✓ 保存しました",
                MoveEarthMessageStyle.plainText("✓", "保存しました"));
    }

    @Test
    void interpolationCreatesActualGradient() {
        assertEquals(0x278B57, MoveEarthMessageStyle.interpolate(0x278B57, 0x78F0AA, 0.0F));
        assertEquals(0x78F0AA, MoveEarthMessageStyle.interpolate(0x278B57, 0x78F0AA, 1.0F));
        assertNotEquals(0x278B57, MoveEarthMessageStyle.interpolate(0x278B57, 0x78F0AA, 0.5F));
    }

    @Test
    void interpolationClampsOutOfRangeProgress() {
        assertEquals(0x123456, MoveEarthMessageStyle.interpolate(0x123456, 0xABCDEF, -1.0F));
        assertEquals(0xABCDEF, MoveEarthMessageStyle.interpolate(0x123456, 0xABCDEF, 2.0F));
    }
}
