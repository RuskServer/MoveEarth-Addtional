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
        final PayloadRegistrar registrar = event.registrar(
                "3.0-detector-admin1-oxygen1-s2ui39-advancements1-prisoners1-recovery1-notifications2-market5-waypoint2-balance1-eventhud1-eventscreen1-weldtarget1-notifyux1-hubhome1-mekashield1");
        NationPayloads.register(registrar);
        SiegePayloads.register(registrar);
        MarketPayloads.register(registrar);
        VehiclePayloads.register(registrar);
        OtherPayloads.register(registrar);
    }
}
