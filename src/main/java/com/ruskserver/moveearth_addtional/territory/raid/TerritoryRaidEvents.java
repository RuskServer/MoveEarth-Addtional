package com.ruskserver.moveearth_addtional.territory.raid;

import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

@EventBusSubscriber(modid = Moveearth_addtional.MODID)
public final class TerritoryRaidEvents {
    private TerritoryRaidEvents() {
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        TerritoryRaidRegistry.clear(event.getServer());
    }
}
