package com.ruskserver.moveearth_addtional.s2.recovery;

import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class RecoveryWallBaselinesTest {
    @Test
    void repeatedHitsAndCompleteDestructionDoNotLowerTheOpeningTarget() {
        var data = new RecoveryWallBaselines();
        UUID siege = UUID.randomUUID();
        assertTrue(data.record(siege, 640, 2));
        assertFalse(data.record(siege, 0, 0));
        assertEquals(new RecoveryWallBaselines.Baseline(640, 2), data.get(siege));
        var restored = new RecoveryWallBaselines();
        data.snapshot().forEach((id, value) -> restored.record(id, value.health(), value.blocks()));
        assertFalse(restored.record(siege, 0, 0));
        assertEquals(data.get(siege), restored.get(siege));
    }

    @Test
    void pruningRetainsOtherOngoingBattlesAndAllowsANewBattleSnapshot() {
        var data = new RecoveryWallBaselines();
        UUID ended = UUID.randomUUID(), active = UUID.randomUUID();
        data.record(ended, 320, 1);
        data.record(active, 640, 2);
        assertTrue(data.prune(active::equals));
        assertNull(data.get(ended));
        assertEquals(640, data.get(active).health());
        assertTrue(data.record(UUID.randomUUID(), 960, 3));
    }
}
