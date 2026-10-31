package com.ruskserver.moveearth_addtional.s2.notification;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** A delivery stays in flight until its server-thread acknowledgement/retry mutation has completed. */
public final class DiscordDeliveryCompletion {
    private DiscordDeliveryCompletion() { }

    public static void schedule(BooleanSupplier currentServer, Consumer<Runnable> serverExecutor,
                                Runnable release, Runnable complete) {
        if (!currentServer.getAsBoolean()) return;
        serverExecutor.accept(() -> {
            // An old JDA callback must not touch a new server's lease for a persisted delivery UUID.
            if (!currentServer.getAsBoolean()) return;
            try { complete.run(); }
            finally { release.run(); }
        });
    }
}
