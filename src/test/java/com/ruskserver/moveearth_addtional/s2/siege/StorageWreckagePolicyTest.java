package com.ruskserver.moveearth_addtional.s2.siege;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class StorageWreckagePolicyTest {

    @Test
    @DisplayName("an unowned wreck can be emptied by anyone")
    void unownedIsSalvage() {
        // The case that locked a block into the world: no owner to match, so
        // every other route failed, and a wreck holding items cannot be broken.
        assertTrue(StorageWreckagePolicy.mayRecover(false, false, false, false, false));
    }

    @Test
    @DisplayName("an owned wreck needs a reason")
    void ownedNeedsAClaim() {
        assertFalse(StorageWreckagePolicy.mayRecover(false, true, false, false, false));
        assertTrue(StorageWreckagePolicy.mayRecover(false, true, true, false, false));
        assertTrue(StorageWreckagePolicy.mayRecover(false, true, false, true, false));
        assertTrue(StorageWreckagePolicy.mayRecover(false, true, false, false, true));
    }

    @Test
    @DisplayName("staff are not blocked by any of it")
    void staffAlways() {
        assertTrue(StorageWreckagePolicy.mayRecover(true, true, false, false, false));
    }
}
