package com.ruskserver.moveearth_addtional.s2.territory;

import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

@EventBusSubscriber(modid = Moveearth_addtional.MODID, bus = EventBusSubscriber.Bus.GAME)
public final class TerritoryClosureEvents {
    private TerritoryClosureEvents() {
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        TerritoryClosureRecheckManager.tick(event.getServer());
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        TerritoryClosureRecheckManager.clear();
    }

}
