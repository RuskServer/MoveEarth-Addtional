package com.ruskserver.moveearth_addtional.s2.provenance;

import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class ActionSourceTest {
    @Test void unidentifiedArtificialActionsNeverGetAnImplicitPass() {
        assertFalse(ActionSource.UNKNOWN.allowed());
        assertFalse(new ActionSource(null, null, ActionSource.Kind.ATTRIBUTED).allowed());
        assertTrue(new ActionSource(UUID.randomUUID(), null, ActionSource.Kind.ATTRIBUTED).allowed());
        assertTrue(new ActionSource(null, UUID.randomUUID(), ActionSource.Kind.ATTRIBUTED).allowed());
    }
    @Test void naturalChangesAndMobOriginsAreExplicitExceptions() {
        assertTrue(ActionSource.NATURAL.allowed());
        assertTrue(ActionSource.MOB.allowed());
        assertFalse(ActionSource.NATURAL.attributable());
        assertFalse(ActionSource.MOB.attributable());
    }
    @Test void nestedFailuresCannotLeakOrBorrowAnotherAttackersOrigin() {
        assertNull(ActionContext.current());
        Object first = new Object(), second = new Object();
        try (var outer = ActionContext.push(first)) {
            assertSame(first, ActionContext.current());
            assertThrows(IllegalStateException.class, () -> {
                try (var inner = ActionContext.push(second)) {
                    assertSame(second, ActionContext.current());
                    throw new IllegalStateException();
                }
            });
            assertSame(first, ActionContext.current());
            try (var denied = ActionContext.push(ActionSource.UNKNOWN)) {
                assertSame(ActionSource.UNKNOWN, ActionContext.current());
            }
            assertSame(first, ActionContext.current());
        }
        assertNull(ActionContext.current());
    }
    @Test void independentThreadsCannotBorrowTheServerThreadsAttribution() throws Exception {
        var childSaw = new java.util.concurrent.atomic.AtomicReference<Object>();
        var childError = new java.util.concurrent.atomic.AtomicReference<Throwable>();
        Object serverOrigin = new Object();
        try (var outer = ActionContext.push(serverOrigin)) {
            Thread thread = new Thread(() -> {
                try {
                    childSaw.set(ActionContext.current());
                    try (var child = ActionContext.push(ActionSource.UNKNOWN)) {
                        assertSame(ActionSource.UNKNOWN, ActionContext.current());
                    }
                    assertNull(ActionContext.current());
                } catch (Throwable error) { childError.set(error); }
            });
            thread.start();
            thread.join(5_000);
            assertFalse(thread.isAlive());
            assertNull(childError.get());
            assertNull(childSaw.get());
            assertSame(serverOrigin, ActionContext.current());
        }
        assertNull(ActionContext.current());
    }
}
