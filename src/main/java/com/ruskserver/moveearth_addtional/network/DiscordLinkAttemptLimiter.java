package com.ruskserver.moveearth_addtional.network;

import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Server-thread limiter shared by both one-time Discord link-code consumers. */
@EventBusSubscriber(modid = Moveearth_addtional.MODID, bus = EventBusSubscriber.Bus.GAME)
final class DiscordLinkAttemptLimiter {
    private static final long MIN_INTERVAL_TICKS = 20L;
    private static final Map<AttemptKey, Long> LAST_ATTEMPTS = new HashMap<>();

    private DiscordLinkAttemptLimiter() { }

    static boolean allow(ServerPlayer player, String operation) {
        long now = player.server.overworld().getGameTime();
        AttemptKey key = new AttemptKey(player.getUUID(), operation);
        Long previous = LAST_ATTEMPTS.put(key, now);
        return previous == null || now < previous || now - previous >= MIN_INTERVAL_TICKS;
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        UUID playerId = event.getEntity().getUUID();
        LAST_ATTEMPTS.keySet().removeIf(key -> key.playerId.equals(playerId));
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        LAST_ATTEMPTS.clear();
    }

    private record AttemptKey(UUID playerId, String operation) { }
}
