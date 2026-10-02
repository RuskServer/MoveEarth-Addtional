package com.ruskserver.moveearth_addtional.client;

import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import com.ruskserver.moveearth_addtional.network.s2c.other.S2C_LiveStreamersPacket;
import com.ruskserver.moveearth_addtional.s2.live.LiveStreamService;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RenderNameTagEvent;

import java.util.Set;
import java.util.UUID;

/**
 * Puts {@code [LIVE]} in front of a streaming player's name above their head. Runs
 * last so it decorates whatever the nation and PvP name tags made of the name.
 */
@EventBusSubscriber(modid = Moveearth_addtional.MODID, value = Dist.CLIENT)
public final class LiveNameTagClient {
    private static Set<UUID> live = Set.of();

    private LiveNameTagClient() { }

    public static void update(S2C_LiveStreamersPacket packet) {
        live = Set.copyOf(packet.players());
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onRenderNameTag(RenderNameTagEvent event) {
        if (event.getEntity() instanceof Player player && live.contains(player.getUUID())) {
            event.setContent(LiveStreamService.prefix().append(event.getContent()));
        }
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        live = Set.of();
    }
}
