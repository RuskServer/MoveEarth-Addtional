package com.ruskserver.moveearth_addtional.s2.combat;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** One transient CombatLogX-style combat timer bar per tagged player. */
public final class CombatTagBossBar {
    private static final Map<UUID, ActiveBar> ACTIVE = new HashMap<>();

    private CombatTagBossBar() { }

    public static void update(ServerPlayer player, long remainingTicks) {
        if (remainingTicks <= 0L) {
            remove(player.getUUID());
            return;
        }
        ActiveBar active = ACTIVE.computeIfAbsent(player.getUUID(), ignored -> new ActiveBar());
        active.maximumTicks = CombatTagBossBarDisplay.updatedMaximum(
                active.maximumTicks, active.lastRemainingTicks, remainingTicks);
        active.lastRemainingTicks = remainingTicks;
        CombatTagBossBarDisplay.Display display = CombatTagBossBarDisplay.create(
                remainingTicks, active.maximumTicks);
        active.event.setName(Component.translatable(
                "message.moveearth_addtional.combat.bossbar", display.seconds()));
        active.event.setProgress(display.progress());
        active.event.setColor(display.urgent()
                ? BossEvent.BossBarColor.RED : BossEvent.BossBarColor.YELLOW);
        active.event.addPlayer(player);
    }

    public static void retain(Set<UUID> visiblePlayers) {
        Iterator<Map.Entry<UUID, ActiveBar>> iterator = ACTIVE.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, ActiveBar> entry = iterator.next();
            if (visiblePlayers.contains(entry.getKey())) continue;
            entry.getValue().event.removeAllPlayers();
            iterator.remove();
        }
    }

    public static void remove(UUID playerId) {
        ActiveBar active = ACTIVE.remove(playerId);
        if (active != null) active.event.removeAllPlayers();
    }

    public static void clear() {
        ACTIVE.values().forEach(active -> active.event.removeAllPlayers());
        ACTIVE.clear();
    }

    private static final class ActiveBar {
        private final ServerBossEvent event = new ServerBossEvent(Component.empty(),
                BossEvent.BossBarColor.YELLOW, BossEvent.BossBarOverlay.PROGRESS);
        private long maximumTicks;
        private long lastRemainingTicks;
    }
}
