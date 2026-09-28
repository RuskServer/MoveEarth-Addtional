package com.ruskserver.moveearth_addtional.chat;

import com.ruskserver.moveearth_addtional.ui.MoveEarthMessage;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * Chat is proximity-only and new players spawn far from everyone, so a first message silently
 * reached nobody. Tell the sender, at most every few minutes, and point them to /msg.
 */
public final class LocalChatNotice {
    private static final long INTERVAL_MILLIS = 600_000L;
    private static final Map<UUID, Long> LAST = new HashMap<>();

    private LocalChatNotice() { }

    public static void nobodyHeard(ServerPlayer sender, int radius) {
        long now = System.currentTimeMillis();
        Long last = LAST.get(sender.getUUID());
        if (last != null && now - last < INTERVAL_MILLIS) return;
        LAST.put(sender.getUUID(), now);
        sender.sendSystemMessage(MoveEarthMessage.tip(
                Component.translatable("message.moveearth_addtional.chat.nobody_heard", radius)));
    }
}
