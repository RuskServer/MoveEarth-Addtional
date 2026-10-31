package com.ruskserver.moveearth_addtional.analytics.provenance;

import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import com.ruskserver.moveearth_addtional.analytics.event.*;
import com.ruskserver.moveearth_addtional.analytics.queue.AnalyticsEventQueue;
import com.ruskserver.moveearth_addtional.analytics.storage.AnalyticsStorageService;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

/** Diagnostics only. Never changes the source decision; database writes use the existing asynchronous queue. */
public final class MutationRejectionAudit {
    private static final MutationRejectionBuffer BUFFER = new MutationRejectionBuffer();
    private static long minute = -1;
    private MutationRejectionAudit() { }

    public static synchronized void rejected(String route, Level level, BlockPos pos) {
        if (level == null || level.isClientSide || pos == null) return;
        try {
            long now = System.currentTimeMillis() / 1000L;
            rotate(now);
            BUFFER.add(now, route, level.dimension().location().toString(), pos.getX(), pos.getY(), pos.getZ());
        } catch (RuntimeException ignored) { /* Diagnostics must never prevent the refusal. */ }
    }

    public static synchronized void tick() { rotate(System.currentTimeMillis() / 1000L); }
    private static void rotate(long now) {
        long next = now / 60;
        if (minute != next) { flush(); minute = next; }
    }

    public static synchronized void flush() {
        for (var batch : BUFFER.drain()) {
            Moveearth_addtional.LOGGER.warn("Unattributed action blocked: route={} dimension={} refusals={} sample=({}, {}, {}) firstEpochSec={}",
                    batch.route(), batch.dimension(), batch.count(), batch.x(), batch.y(), batch.z(), batch.occurredAt());
            if (AnalyticsStorageService.INSTANCE.isRunning()) {
                AnalyticsEventQueue.INSTANCE.enqueue(new AnalyticsEventQueue.GameEventLogEvent(new GameEventRecord(
                        batch.occurredAt(), GameEventType.MUTATION_REJECTED.id(), null, null, batch.dimension(),
                        batch.x(), batch.y(), batch.z(), batch.count(), batch.route())));
            }
        }
    }
}
