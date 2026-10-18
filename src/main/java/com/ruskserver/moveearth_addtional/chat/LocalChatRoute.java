package com.ruskserver.moveearth_addtional.chat;

import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import net.minecraft.server.level.ServerPlayer;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Logs, once per route and sender, player chat that reached a player some other way than the player list,
 * so the server log shows which way chat travels when a line arrives without local chat.
 */
public final class LocalChatRoute {
    private static final Set<String> SEEN = ConcurrentHashMap.newKeySet();

    private LocalChatRoute() { }

    public static void noticed(String route, ServerPlayer sender, ServerPlayer recipient) {
        String name = sender.getGameProfile().getName();
        if (SEEN.add(route + '/' + name)) {
            Moveearth_addtional.LOGGER.info("[LocalChat] chat from {} reached {} by the {} route; local chat applied there",
                    name, recipient.getGameProfile().getName(), route);
        }
    }
}
