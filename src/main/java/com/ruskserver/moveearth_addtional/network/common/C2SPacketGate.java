package com.ruskserver.moveearth_addtional.network.common;

import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import com.ruskserver.moveearth_addtional.ui.MoveEarthMessage;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.handling.IPayloadHandler;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Rate limit in front of every MoveEarth client-to-server packet.
 *
 * <p>C2S payloads are registered to run on the network thread with a handler from this class: the
 * token check runs there, and only admitted packets are queued for the server thread, so a flood never
 * reaches the (unbounded) main-thread task queue. Vanilla's {@code rate-limit} does not cover this:
 * it defaults to 0 (off) and counts all packets alike.
 *
 * <p>What happens to a packet over its budget depends on {@link Overflow}: read-only snapshot requests
 * keep only the latest one per player and packet type and run it once a token frees up, so the final
 * state a player asked for is always answered; button actions are refused with a throttled action-bar
 * notice; continuous input is dropped silently.
 */
@EventBusSubscriber(modid = Moveearth_addtional.MODID, bus = EventBusSubscriber.Bus.GAME)
public final class C2SPacketGate {
    public static final String RATE_LIMITED_KEY = "message.moveearth_addtional.network.rate_limited";
    private static final long NOTICE_INTERVAL_MILLIS = 3_000L;

    private static final C2SRateLimiter LIMITER = C2SRateLimiter.systemClock();
    /** Latest deferred request per player and payload type; guarded by itself. */
    private static final Map<UUID, Map<CustomPacketPayload.Type<?>, Deferred<?>>> DEFERRED = new HashMap<>();
    /** Last rate-limit notice per player, in monotonic millis; guarded by itself. */
    private static final Map<UUID, Long> LAST_NOTICE = new HashMap<>();

    public enum Overflow {
        /** Drop silently. */
        DROP,
        /** Drop and tell the player (at most once per few seconds). */
        NOTIFY,
        /** Keep only the latest request and run it once the budget allows. */
        COALESCE
    }

    private record Deferred<T extends CustomPacketPayload>(T payload, IPayloadContext context,
                                                           IPayloadHandler<T> handler,
                                                           C2SRateLimiter.Budget budget) {
        void run() {
            handler.handle(payload, context);
        }
    }

    private C2SPacketGate() {
    }

    /** Continuous cheap input (e.g. a scroll-wheel setting); only the latest value matters. */
    public static <T extends CustomPacketPayload> IPayloadHandler<T> input(IPayloadHandler<T> handler) {
        return guard(C2SRateLimiter.INPUT, Overflow.COALESCE, handler);
    }

    /** A UI button that changes state. */
    public static <T extends CustomPacketPayload> IPayloadHandler<T> action(IPayloadHandler<T> handler) {
        return guard(C2SRateLimiter.ACTION, Overflow.NOTIFY, handler);
    }

    /** An action whose reply rebuilds a large snapshot. */
    public static <T extends CustomPacketPayload> IPayloadHandler<T> heavy(IPayloadHandler<T> handler) {
        return guard(C2SRateLimiter.HEAVY, Overflow.NOTIFY, handler);
    }

    /** A read-only snapshot request; extra requests fold into the latest one. */
    public static <T extends CustomPacketPayload> IPayloadHandler<T> request(IPayloadHandler<T> handler) {
        return guard(C2SRateLimiter.REQUEST, Overflow.COALESCE, handler);
    }

    /** An action with server-wide side effects (founding a nation, Discord linking). */
    public static <T extends CustomPacketPayload> IPayloadHandler<T> sensitive(IPayloadHandler<T> handler) {
        return guard(C2SRateLimiter.SENSITIVE, Overflow.NOTIFY, handler);
    }

    /**
     * Wraps a C2S handler. The returned handler must be registered on the network thread
     * ({@code HandlerThread.NETWORK}); it hands admitted packets to the server thread itself.
     */
    public static <T extends CustomPacketPayload> IPayloadHandler<T> guard(C2SRateLimiter.Budget budget,
                                                                           Overflow overflow,
                                                                           IPayloadHandler<T> handler) {
        return (payload, context) -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            UUID playerId = player.getUUID();
            if (LIMITER.tryAcquire(playerId, payload.type(), budget)) {
                // A newer request supersedes one still waiting, so they cannot run out of order.
                if (overflow == Overflow.COALESCE) discardDeferred(playerId, payload.type());
                context.enqueueWork(() -> handler.handle(payload, context));
                return;
            }
            switch (overflow) {
                case DROP -> { }
                case NOTIFY -> {
                    // Checked here so a flood queues at most one notice per interval on the server thread.
                    if (noticeDue(playerId)) context.enqueueWork(() -> showRateLimited(player));
                }
                case COALESCE -> defer(playerId, new Deferred<>(payload, context, handler, budget));
            }
        };
    }

    /**
     * Separate, tighter limit for one sub-action of a packet that already passed the gate (server
     * thread), e.g. an audit-logged unlink inside a general notification packet.
     */
    public static boolean allowSubAction(ServerPlayer player, String key, C2SRateLimiter.Budget budget) {
        return LIMITER.tryAcquire(player.getUUID(), key, budget, false);
    }

    /** Tells the player they are sending too fast, at most once per {@link #NOTICE_INTERVAL_MILLIS}. */
    public static void notifyRateLimited(ServerPlayer player) {
        if (noticeDue(player.getUUID())) showRateLimited(player);
    }

    private static boolean noticeDue(UUID playerId) {
        long now = System.nanoTime() / 1_000_000L;
        synchronized (LAST_NOTICE) {
            Long last = LAST_NOTICE.get(playerId);
            if (last != null && now - last < NOTICE_INTERVAL_MILLIS) return false;
            LAST_NOTICE.put(playerId, now);
            return true;
        }
    }

    private static void showRateLimited(ServerPlayer player) {
        if (player.hasDisconnected()) return;
        player.displayClientMessage(MoveEarthMessage.warning(Component.translatable(RATE_LIMITED_KEY)), true);
    }

    private static void defer(UUID playerId, Deferred<?> deferred) {
        synchronized (DEFERRED) {
            DEFERRED.computeIfAbsent(playerId, ignored -> new HashMap<>())
                    .put(deferred.payload().type(), deferred);
        }
    }

    private static void discardDeferred(UUID playerId, CustomPacketPayload.Type<?> type) {
        synchronized (DEFERRED) {
            Map<CustomPacketPayload.Type<?>, Deferred<?>> waiting = DEFERRED.get(playerId);
            if (waiting == null) return;
            waiting.remove(type);
            if (waiting.isEmpty()) DEFERRED.remove(playerId);
        }
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        List<Deferred<?>> ready;
        synchronized (DEFERRED) {
            if (DEFERRED.isEmpty()) return;
            ready = new ArrayList<>();
            var players = DEFERRED.entrySet().iterator();
            while (players.hasNext()) {
                var player = players.next();
                var requests = player.getValue().values().iterator();
                while (requests.hasNext()) {
                    Deferred<?> deferred = requests.next();
                    if (!LIMITER.tryAcquire(player.getKey(), deferred.payload().type(), deferred.budget())) continue;
                    ready.add(deferred);
                    requests.remove();
                }
                if (player.getValue().isEmpty()) players.remove();
            }
        }
        for (Deferred<?> deferred : ready) {
            if (!(deferred.context().player() instanceof ServerPlayer player) || player.hasDisconnected()
                    || !deferred.context().connection().isConnected()) continue;
            deferred.run();
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        UUID playerId = event.getEntity().getUUID();
        LIMITER.forget(playerId);
        synchronized (DEFERRED) {
            DEFERRED.remove(playerId);
        }
        synchronized (LAST_NOTICE) {
            LAST_NOTICE.remove(playerId);
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        LIMITER.clear();
        synchronized (DEFERRED) {
            DEFERRED.clear();
        }
        synchronized (LAST_NOTICE) {
            LAST_NOTICE.clear();
        }
    }
}
