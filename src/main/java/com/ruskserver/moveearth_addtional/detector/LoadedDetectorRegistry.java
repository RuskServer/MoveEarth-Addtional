package com.ruskserver.moveearth_addtional.detector;

import com.ruskserver.moveearth_addtional.block.entity.PlayerDetectorBlockEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

/** Tracks only detector block entities whose chunks are currently loaded. */
public final class LoadedDetectorRegistry {
    private static final Map<MinecraftServer, Set<PlayerDetectorBlockEntity>> DETECTORS_BY_SERVER =
            new IdentityHashMap<>();

    private LoadedDetectorRegistry() {
    }

    public static void register(PlayerDetectorBlockEntity detector) {
        if (!(detector.getLevel() instanceof ServerLevel level)) return;
        DETECTORS_BY_SERVER.computeIfAbsent(
                level.getServer(), ignored -> Collections.newSetFromMap(new IdentityHashMap<>()))
                .add(detector);
    }

    public static void unregister(PlayerDetectorBlockEntity detector) {
        if (detector.getLevel() instanceof ServerLevel level) {
            Set<PlayerDetectorBlockEntity> detectors = DETECTORS_BY_SERVER.get(level.getServer());
            if (detectors != null) {
                detectors.remove(detector);
                if (detectors.isEmpty()) DETECTORS_BY_SERVER.remove(level.getServer());
            }
            return;
        }

        // Defensive fallback for a lifecycle path that cleared the level first.
        DETECTORS_BY_SERVER.values().forEach(detectors -> detectors.remove(detector));
        DETECTORS_BY_SERVER.values().removeIf(Set::isEmpty);
    }

    public static void maintainLoadedDetectors(MinecraftServer server) {
        Set<PlayerDetectorBlockEntity> detectors = DETECTORS_BY_SERVER.get(server);
        if (detectors == null) return;

        Iterator<PlayerDetectorBlockEntity> iterator = detectors.iterator();
        while (iterator.hasNext()) {
            PlayerDetectorBlockEntity detector = iterator.next();
            if (detector.isRemoved()
                    || !(detector.getLevel() instanceof ServerLevel level)
                    || level.getServer() != server) {
                iterator.remove();
                continue;
            }
            detector.maintainDummyEntity(level, detector.getBlockPos());
        }

        if (detectors.isEmpty()) DETECTORS_BY_SERVER.remove(server);
    }

    public static void clear(MinecraftServer server) {
        DETECTORS_BY_SERVER.remove(server);
    }
}
