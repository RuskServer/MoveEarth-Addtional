package com.ruskserver.moveearth_addtional.network;

import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import com.ruskserver.moveearth_addtional.network.common.registration.NationPayloads;
import com.ruskserver.moveearth_addtional.network.common.registration.SiegePayloads;
import com.ruskserver.moveearth_addtional.network.common.registration.MarketPayloads;
import com.ruskserver.moveearth_addtional.network.common.registration.VehiclePayloads;
import com.ruskserver.moveearth_addtional.network.common.registration.OtherPayloads;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

@EventBusSubscriber(modid = Moveearth_addtional.MODID, bus = EventBusSubscriber.Bus.MOD)
public class ModMessages {
    @SubscribeEvent
    public static void register(final RegisterPayloadHandlersEvent event) {
        // One protocol per release: clients from another MoveEarth version are refused at login.
        final PayloadRegistrar registrar = event.registrar("3.3");
        NationPayloads.register(registrar);
        SiegePayloads.register(registrar);
        MarketPayloads.register(registrar);
        VehiclePayloads.register(registrar);
        OtherPayloads.register(registrar);
    }
}
