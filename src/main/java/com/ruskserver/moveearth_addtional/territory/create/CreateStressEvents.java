package com.ruskserver.moveearth_addtional.territory.create;

import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

@EventBusSubscriber(modid = Moveearth_addtional.MODID)
public final class CreateStressEvents {
    private CreateStressEvents() {
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (!ModList.get().isLoaded("create")) {
            return;
        }
        long gameTime = event.getServer().overworld().getGameTime();
        if (gameTime % TerritoryCreateConfig.REFRESH_INTERVAL.get() == 0L) {
            CreateStressScanner.refresh(event.getServer());
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        CreateStressCache.clear(event.getServer());
    }
}
