package com.ruskserver.moveearth_addtional.detector;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DetectorNamePolicyTest {
    @Test
    void acceptsAndTrimsJapaneseName() {
        DetectorNamePolicy.Validation result = DetectorNamePolicy.validate("  北門監視塔  ");

        assertTrue(result.valid());
        assertEquals("北門監視塔", result.normalized());
    }

    @Test
    void emptyNameResetsToUnnamed() {
        DetectorNamePolicy.Validation result = DetectorNamePolicy.validate("   ");

        assertTrue(result.valid());
        assertEquals("", result.normalized());
    }

    @Test
    void rejectsTooLongOrUnsafeNames() {
        assertFalse(DetectorNamePolicy.validate("a".repeat(DetectorNamePolicy.MAX_LENGTH + 1)).valid());
        assertFalse(DetectorNamePolicy.validate("北門\n偽ログ").valid());
        assertFalse(DetectorNamePolicy.validate("§c偽装警告").valid());
    }
}
