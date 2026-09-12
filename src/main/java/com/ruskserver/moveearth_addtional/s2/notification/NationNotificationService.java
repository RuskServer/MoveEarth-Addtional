package com.ruskserver.moveearth_addtional.s2.notification;

import com.ruskserver.moveearth_addtional.s2.nation.NationSavedData;
import com.ruskserver.moveearth_addtional.ui.MoveEarthMessage;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;

/** Single event boundary for in-game nation alerts and the external Discord bridge outbox. */
public final class NationNotificationService {
    private NationNotificationService() { }

    public static void publish(MinecraftServer server, Collection<UUID> nationIds,
                               NationNotificationSavedData.EventType type,
                               ResourceLocation dimension, BlockPos pos,
                               Component inGameBody, List<String> externalArguments) {
        if (server == null || nationIds == null || nationIds.isEmpty()) return;
        NationSavedData nations = NationSavedData.get(server);
        NationNotificationSavedData notifications = NationNotificationSavedData.get(server);
        long now = System.currentTimeMillis();
        for (UUID nationId : new LinkedHashSet<>(nationIds)) {
            NationSavedData.Nation nation = nations.nation(nationId).orElse(null);
            if (nation == null) continue;
            NationNotificationSavedData.Settings settings = notifications.settings(nationId);
            if (settings.inGame() && inGameBody != null) {
                for (UUID memberId : nation.members().keySet()) {
                    ServerPlayer player = server.getPlayerList().getPlayer(memberId);
                    if (player != null) player.sendSystemMessage(MoveEarthMessage.warning(inGameBody));
                }
            }
            // The five-minute initial lock is intentionally an in-game warning only.
            if (type != NationNotificationSavedData.EventType.SIEGE_INITIAL_STARTED) {
                notifications.enqueue(nationId, type, dimension, pos, externalArguments, now);
            }
        }
    }
}
