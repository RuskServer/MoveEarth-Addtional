package com.ruskserver.moveearth_addtional.s2.time;

import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import com.ruskserver.moveearth_addtional.ServerSchedule;
import net.minecraft.server.MinecraftServer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Shared source of truth for durations expressed in server-opening time. */
@EventBusSubscriber(modid = Moveearth_addtional.MODID, bus = EventBusSubscriber.Bus.GAME)
public final class OpenTimeService {
    private OpenTimeService() { }

    public static long now(MinecraftServer server) {
        return OpenTimeSavedData.get(server).openTicks();
    }

    public static boolean isOpen(MinecraftServer server) {
        return !server.isDedicatedServer() || ServerSchedule.isOpenNow();
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (server.overworld().getGameTime() % 20L != 3L || !isOpen(server)) return;
        OpenTimeSavedData.get(server).advance(20L);
    }
}
