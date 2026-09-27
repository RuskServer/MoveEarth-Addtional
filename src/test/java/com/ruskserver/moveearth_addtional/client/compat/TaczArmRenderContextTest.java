package com.ruskserver.moveearth_addtional.client.compat;

import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TaczArmRenderContextTest {
    @Test
    void onlyActiveDuringGunArmRendering() {
        assertFalse(TaczArmRenderContext.isRenderingArm());
        TaczArmRenderContext.render(() -> assertTrue(TaczArmRenderContext.isRenderingArm()));
        assertFalse(TaczArmRenderContext.isRenderingArm());
    }

    @Test
    void nestedRenderingRestoresOuterContext() {
        TaczArmRenderContext.render(() -> {
            TaczArmRenderContext.render(() -> assertTrue(TaczArmRenderContext.isRenderingArm()));
            assertTrue(TaczArmRenderContext.isRenderingArm());
        });
        assertFalse(TaczArmRenderContext.isRenderingArm());
    }

    @Test
    void exceptionsDoNotLeakContextToOtherHands() {
        assertThrows(IllegalStateException.class, () -> TaczArmRenderContext.render(() -> {
            throw new IllegalStateException("arm rendering failed");
        }));
        assertFalse(TaczArmRenderContext.isRenderingArm());
    }

    @Test
    void contextDoesNotAffectOtherThreads() {
        TaczArmRenderContext.render(() -> assertFalse(
                CompletableFuture.supplyAsync(TaczArmRenderContext::isRenderingArm).join()));
        assertFalse(TaczArmRenderContext.isRenderingArm());
    }
}
