package com.ruskserver.moveearth_addtional.compat.vehicle;

import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import com.ruskserver.moveearth_addtional.config.AeronauticsSwivelConfig;
import com.ruskserver.moveearth_addtional.ui.MoveEarthMessage;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

/** Removes only excess, warned, continuously eligible loaded debris. Never forces bodies to load. */
@EventBusSubscriber(modid = Moveearth_addtional.MODID)
public final class SableDebrisCleanup {
    private static final Map<ServerLevel, Map<UUID, SableDebrisWarningWindow<ServerPlayer>>> PENDING = new WeakHashMap<>();
    private static final Map<ServerLevel, Long> RETRY_AFTER_FAILURE = new WeakHashMap<>();

    private SableDebrisCleanup() { }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (!AeronauticsSwivelConfig.bodyDensityEnabled() || !AeronauticsSwivelConfig.debrisCleanupEnabled()) {
            PENDING.clear();
            RETRY_AFTER_FAILURE.clear();
            return;
        }
        if (event.getServer().getTickCount() % 100 != 0) return;
        for (ServerLevel level : event.getServer().getAllLevels()) {
            if (level.getGameTime() < RETRY_AFTER_FAILURE.getOrDefault(level, Long.MIN_VALUE)) continue;
            try {
                sweep(level);
                RETRY_AFTER_FAILURE.remove(level);
            } catch (RuntimeException | LinkageError failure) {
                // Failed eligibility checks must not delete a body or cost the server tick.
                PENDING.remove(level);
                RETRY_AFTER_FAILURE.put(level, level.getGameTime() + 1200);
                Moveearth_addtional.LOGGER.error("Sable debris cleanup stopped in {}", level.dimension().location(), failure);
            }
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        PENDING.clear();
        RETRY_AFTER_FAILURE.clear();
    }

    private static void sweep(ServerLevel level) {
        var container = SubLevelContainer.getContainer(level);
        if (container == null) { PENDING.remove(level); return; }
        List<ServerSubLevel> bodies = new ArrayList<>(container.getAllSubLevels());
        bodies.removeIf(ServerSubLevel::isRemoved);
        var pending = PENDING.computeIfAbsent(level, ignored -> new HashMap<>());
        var loaded = new java.util.HashSet<UUID>();
        int radius = AeronauticsSwivelConfig.bodyDensityRadius();
        int max = AeronauticsSwivelConfig.bodyDensityMax();
        int graceSeconds = AeronauticsSwivelConfig.debrisCleanupGraceSeconds();
        var index = new SableBodyDensityIndex<UUID>(radius);
        for (var body : bodies) {
            loaded.add(body.getUniqueId());
            index.put(body.getUniqueId(), position(body));
        }
        var hotspots = new SableBodyDensityIndex<UUID>(radius);
        for (var body : bodies) {
            var point = position(body);
            if (index.count(point, max + 1) > max) hotspots.put(body.getUniqueId(), point);
        }
        pending.keySet().retainAll(loaded);
        bodies.sort(Comparator.comparingLong(body -> {
            var window = pending.get(body.getUniqueId());
            return window == null ? Long.MAX_VALUE : window.since();
        }));
        Map<UUID, Boolean> protection = new HashMap<>();
        Map<ServerPlayer, Integer> warnings = new HashMap<>();
        Map<ServerPlayer, SableBodyDensityPolicy.Point> observers = new HashMap<>();
        for (var player : level.players()) {
            var point = SableBodyProtection.worldPosition(player);
            observers.put(player, new SableBodyDensityPolicy.Point(point.x, point.z));
        }
        long now = level.getGameTime();
        int removed = 0;
        for (var body : bodies) {
            UUID id = body.getUniqueId();
            if (body.isRemoved()) { index.remove(id); hotspots.remove(id); pending.remove(id); continue; }
            var point = position(body);
            int nearby = index.overcrowdedCountNear(point, hotspots, max);
            if (nearby <= max) { pending.remove(id); continue; }
            if (!protection.containsKey(id)) {
                var chain = SableBodyProtection.inspect(level, body);
                for (UUID member : chain.ids()) protection.put(member, chain.protectedBody());
            }
            boolean protect = protection.getOrDefault(id, true);
            if (protect) { pending.remove(id); continue; }
            var window = pending.get(id);
            if (window == null || !window.matches(body) || now < window.since()) {
                window = new SableDebrisWarningWindow<>(body, now, graceSeconds * 20L);
                pending.put(id, window);
            }
            window.extendGrace(graceSeconds * 20L);
            // Warn before considering removal. A late arrival/reconnect receives a full grace period.
            for (var entry : observers.entrySet()) {
                ServerPlayer player = entry.getKey();
                if (point.within(entry.getValue(), radius) && window.warn(player.getUUID(), player, now)) {
                    warnings.merge(player, 1, Integer::sum);
                }
            }
            var decision = SableDebrisCleanupPolicy.review(false, nearby, max, now, window.since(),
                    window.graceTicks());
            switch (decision.action()) {
                case KEEP -> pending.remove(id);
                case WARN -> throw new IllegalStateException("A pending cleanup must have a warning window");
                case WAIT -> { }
                case REMOVE -> {
                    if (removed >= AeronauticsSwivelConfig.debrisCleanupMaxPerSweep()) continue;
                    // Recheck the live chain and players immediately before each irreversible removal.
                    if (body.isRemoved() || SableBodyProtection.inspect(level, body).protectedBody()) {
                        pending.remove(id);
                        continue;
                    }
                    // Removal observers may have removed other bodies since the snapshot was built.
                    for (var observed : bodies) {
                        if (observed.isRemoved()) {
                            index.remove(observed.getUniqueId());
                            hotspots.remove(observed.getUniqueId());
                        }
                    }
                    if (index.overcrowdedCountNear(point, hotspots, max) <= max) { pending.remove(id); continue; }
                    container.removeSubLevel(body, SubLevelRemovalReason.REMOVED);
                    index.remove(id); // later candidates see the freed slot; stop at the cap
                    hotspots.remove(id);
                    pending.remove(id);
                    removed++;
                    Moveearth_addtional.LOGGER.info("Removed excess coreless Sable body {} at {} in {}",
                            id, body.logicalPose().position(), level.dimension().location());
                }
            }
        }
        for (var entry : warnings.entrySet()) {
            ServerPlayer player = entry.getKey();
            player.sendSystemMessage(MoveEarthMessage.warning(SableSafetyMessages.component(
                    SableSafetyMessageCatalog.Message.DEBRIS_WARNING, player, entry.getValue(),
                    graceSeconds)));
        }
    }

    private static SableBodyDensityPolicy.Point position(ServerSubLevel body) {
        var point = body.logicalPose().position();
        return new SableBodyDensityPolicy.Point(point.x(), point.z());
    }
}
