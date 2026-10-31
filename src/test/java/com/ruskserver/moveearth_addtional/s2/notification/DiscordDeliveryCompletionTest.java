package com.ruskserver.moveearth_addtional.s2.notification;

import org.junit.jupiter.api.Test;
import java.util.ArrayDeque;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.jupiter.api.Assertions.*;

class DiscordDeliveryCompletionTest {
    @Test void tickCannotResendWhileAcknowledgementIsQueued() {
        var tasks = new ArrayDeque<Runnable>();
        var leased = new AtomicBoolean(true);
        var pending = new AtomicBoolean(true);
        DiscordDeliveryCompletion.schedule(() -> true, tasks::add, () -> leased.set(false), () -> {
            assertTrue(leased.get());
            pending.set(false);
        });
        assertTrue(leased.get(), "a delivery tick before the queued task must still see it as in flight");
        assertTrue(pending.get());
        tasks.remove().run();
        assertFalse(pending.get());
        assertFalse(leased.get());
    }

    @Test void staleCallbackCannotReleaseANewServersLease() {
        var tasks = new ArrayDeque<Runnable>();
        var current = new AtomicBoolean(true);
        DiscordDeliveryCompletion.schedule(current::get, tasks::add,
                () -> fail("cannot release replacement server's lease"), () -> fail("cannot acknowledge on replacement server"));
        current.set(false);
        tasks.remove().run();
        DiscordDeliveryCompletion.schedule(current::get, tasks::add, () -> fail(), () -> fail());
        assertTrue(tasks.isEmpty());
    }

    @Test void failedServerMutationStillReleasesTheLeaseForRetry() {
        var tasks = new ArrayDeque<Runnable>();
        var leased = new AtomicBoolean(true);
        DiscordDeliveryCompletion.schedule(() -> true, tasks::add, () -> leased.set(false), () -> {
            throw new IllegalStateException("test mutation failure");
        });
        assertThrows(IllegalStateException.class, () -> tasks.remove().run());
        assertFalse(leased.get());
    }
}
