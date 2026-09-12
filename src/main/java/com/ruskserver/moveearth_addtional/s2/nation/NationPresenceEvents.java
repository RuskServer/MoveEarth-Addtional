package com.ruskserver.moveearth_addtional.s2.nation;

import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

@EventBusSubscriber(modid = Moveearth_addtional.MODID, bus = EventBusSubscriber.Bus.GAME)
public final class NationPresenceEvents {
    private NationPresenceEvents() {
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        update(event.getEntity());
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        update(event.getEntity());
    }

    private static void update(net.minecraft.world.entity.player.Player player) {
        if (!(player instanceof ServerPlayer serverPlayer)) return;
        NationSavedData.get(serverPlayer.server).updatePresence(serverPlayer.getUUID(),
                serverPlayer.getGameProfile().getName(), System.currentTimeMillis());
    }
}
