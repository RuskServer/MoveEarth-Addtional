package com.ruskserver.moveearth_addtional.compat;

import com.kreezcraft.localizedchat.CommonClass;
import com.kreezcraft.localizedchat.ConfigCache;
import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.ChatVisiblity;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.ServerChatEvent;

import java.util.List;

@EventBusSubscriber(modid = Moveearth_addtional.MODID, bus = EventBusSubscriber.Bus.GAME)
public final class LocalizedChatLogHandler {
    private static final String LOCALIZED_CHAT_MOD_ID = "localizedchat";

    private LocalizedChatLogHandler() {
    }

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public static void onServerChat(ServerChatEvent event) {
        if (!ModList.get().isLoaded(LOCALIZED_CHAT_MOD_ID) || !event.isCanceled()) return;

        ServerPlayer sender = event.getPlayer();
        boolean globalOperatorChat = !ConfigCache.opAsPlayer
                && sender.server.getPlayerList().getOps().get(sender.getGameProfile()) != null;
        List<String> recipients = sender.server.getPlayerList().getPlayers().stream()
                .filter(player -> receivesMessage(sender, player, globalOperatorChat))
                .filter(player -> player.clientInformation().chatVisibility() != ChatVisiblity.HIDDEN)
                .map(player -> player.getGameProfile().getName())
                .toList();

        Moveearth_addtional.LOGGER.info("[LocalizedChat] recipients for <{}>: [{}]",
                event.getUsername(), String.join(", ", recipients));
    }

    private static boolean receivesMessage(ServerPlayer sender, ServerPlayer recipient,
                                           boolean globalOperatorChat) {
        if (sender.getUUID().equals(recipient.getUUID())) return true;
        if (globalOperatorChat) return true;
        return CommonClass.compareCoordinatesDistance(sender.blockPosition(), recipient.blockPosition())
                <= ConfigCache.talkRange;
    }
}
