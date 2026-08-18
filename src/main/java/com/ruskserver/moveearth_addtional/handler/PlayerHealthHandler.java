package com.ruskserver.moveearth_addtional.handler;

import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

@EventBusSubscriber(modid = Moveearth_addtional.MODID, bus = EventBusSubscriber.Bus.GAME)
public class PlayerHealthHandler {
    private static final double MAX_HEALTH = 40.0D;

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        applyMaxHealth(event.getEntity());
    }

    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        applyMaxHealth(event.getEntity());
    }

    private static void applyMaxHealth(net.minecraft.world.entity.player.Player player) {
        if (!(player instanceof ServerPlayer)) {
            return;
        }

        AttributeInstance maxHealth = player.getAttribute(Attributes.MAX_HEALTH);
        if (maxHealth != null && maxHealth.getBaseValue() != MAX_HEALTH) {
            maxHealth.setBaseValue(MAX_HEALTH);
            player.setHealth(Math.min(player.getHealth(), player.getMaxHealth()));
        }
    }
}
