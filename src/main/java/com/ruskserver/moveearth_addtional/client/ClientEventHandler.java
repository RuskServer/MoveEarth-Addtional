package com.ruskserver.moveearth_addtional.client;

import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import com.ruskserver.moveearth_addtional.client.gunpack.RequiredGunPackPrompt;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;

@EventBusSubscriber(modid = Moveearth_addtional.MODID, bus = EventBusSubscriber.Bus.GAME, value = Dist.CLIENT)
public class ClientEventHandler {

    private static int tickCounter = 0;

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        tickCounter++;
        if (tickCounter >= 100) { // 5秒ごとに更新 (100 ticks)
            tickCounter = 0;
            DiscordRPCManager.update();
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onScreenOpening(ScreenEvent.Opening event) {
        RequiredGunPackPrompt.onScreenOpening(event);
    }

    @EventBusSubscriber(modid = Moveearth_addtional.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static class ModBusEvents {
        @SubscribeEvent
        public static void onClientSetup(FMLClientSetupEvent event) {
            event.enqueueWork(DiscordRPCManager::init);
        }
    }
}
