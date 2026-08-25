package com.ruskserver.moveearth_addtional.jobs;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/** Maintains one short-lived vanilla boss bar per player for the latest Jobs XP award. */
public final class JobProgressBossBar {
    private static final int DISPLAY_TICKS = 4 * 20;
    private static final Map<UUID, ActiveBar> ACTIVE = new HashMap<>();

    private JobProgressBossBar() {
    }

    public static void show(ServerPlayer player, JobDefinition definition,
                            JobProgressSavedData.ProgressSnapshot progress, double gainedXp) {
        double nextLevelXp = progress.level() >= definition.maxLevel()
                ? 0.0D : definition.xpNeededForNextLevel(progress.level());
        JobBossBarDisplay.Display display = JobBossBarDisplay.create(
                definition.displayName(), progress.level(), progress.xpInLevel(), nextLevelXp, gainedXp);

        ActiveBar active = ACTIVE.computeIfAbsent(player.getUUID(), ignored -> new ActiveBar());
        active.event.setName(Component.literal(display.title()));
        active.event.setProgress(display.progress());
        active.event.addPlayer(player);
        active.ticksRemaining = DISPLAY_TICKS;
    }

    public static void tick() {
        Iterator<Map.Entry<UUID, ActiveBar>> iterator = ACTIVE.entrySet().iterator();
        while (iterator.hasNext()) {
            ActiveBar active = iterator.next().getValue();
            if (--active.ticksRemaining <= 0) {
                active.event.removeAllPlayers();
                iterator.remove();
            }
        }
    }

    public static void remove(UUID playerId) {
        ActiveBar active = ACTIVE.remove(playerId);
        if (active != null) {
            active.event.removeAllPlayers();
        }
    }

    public static void clear() {
        ACTIVE.values().forEach(active -> active.event.removeAllPlayers());
        ACTIVE.clear();
    }

    private static final class ActiveBar {
        private final ServerBossEvent event = new ServerBossEvent(Component.empty(),
                BossEvent.BossBarColor.GREEN, BossEvent.BossBarOverlay.PROGRESS);
        private int ticksRemaining;
    }
}
