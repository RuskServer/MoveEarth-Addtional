package com.ruskserver.moveearth_addtional.beginner;

import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

@EventBusSubscriber(modid = Moveearth_addtional.MODID, bus = EventBusSubscriber.Bus.GAME)
public final class BeginnerKitEvents {
    private BeginnerKitEvents() {
    }

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
                || !BeginnerKitService.isFirstLogin(player)) {
            return;
        }

        BeginnerKitService.GrantResult result = BeginnerKitService.grant(player, false, "first-login");
        if (result == BeginnerKitService.GrantResult.GRANTED) {
            player.sendSystemMessage(Component.translatable(
                    "message.moveearth_addtional.starterkit.first_login"));
        } else if (result == BeginnerKitService.GrantResult.CONTENT_UNAVAILABLE) {
            player.sendSystemMessage(Component.translatable(
                    "message.moveearth_addtional.starterkit.content_unavailable"));
        }
    }
}
