package com.ruskserver.moveearth_addtional.s2.territory;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReinforcedDoorLockPolicyTest {
    @Test
    void onlyDoorLikeBlocksAreChecked() {
        assertTrue(ReinforcedDoorLockPolicy.opensBlock(true, false, false));
        assertFalse(ReinforcedDoorLockPolicy.opensBlock(false, false, false));
    }

    @Test
    void sneakingWithAnItemUsesTheItemSoC4CanStillBePlaced() {
        assertFalse(ReinforcedDoorLockPolicy.opensBlock(true, true, true));
        assertTrue(ReinforcedDoorLockPolicy.opensBlock(true, true, false));
        assertTrue(ReinforcedDoorLockPolicy.opensBlock(true, false, true));
    }

    @Test
    void eitherReinforcedHalfLocksTheDoor() {
        assertTrue(ReinforcedDoorLockPolicy.locked(true, false));
        assertTrue(ReinforcedDoorLockPolicy.locked(false, true));
        assertFalse(ReinforcedDoorLockPolicy.locked(false, false));
    }
}
