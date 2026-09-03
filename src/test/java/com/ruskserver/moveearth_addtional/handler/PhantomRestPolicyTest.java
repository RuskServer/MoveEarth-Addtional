package com.ruskserver.moveearth_addtional.handler;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PhantomRestPolicyTest {
    @Test
    void protectsPlayerImmediatelyAfterRest() {
        assertTrue(PhantomRestPolicy.isProtectedFromPhantoms(0));
    }

    @Test
    void protectsPlayerAtVanillaThreshold() {
        assertTrue(PhantomRestPolicy.isProtectedFromPhantoms(
                PhantomRestPolicy.MAX_PROTECTED_REST_TICKS));
    }

    @Test
    void expiresProtectionWhenVanillaSpawningBecomesPossible() {
        assertFalse(PhantomRestPolicy.isProtectedFromPhantoms(
                PhantomRestPolicy.MAX_PROTECTED_REST_TICKS + 1));
    }
}
