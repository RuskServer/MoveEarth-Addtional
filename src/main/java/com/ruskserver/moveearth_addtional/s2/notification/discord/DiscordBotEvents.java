package com.ruskserver.moveearth_addtional.s2.notification.discord;

import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Dedicated-server lifecycle boundary for the embedded bot. */
@EventBusSubscriber(modid = Moveearth_addtional.MODID, value = Dist.DEDICATED_SERVER)
public final class DiscordBotEvents {
    private DiscordBotEvents() { }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        DiscordBotService.instance().start(event.getServer());
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        DiscordBotService.instance().tick(event.getServer());
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        DiscordBotService.instance().stop();
    }
}
