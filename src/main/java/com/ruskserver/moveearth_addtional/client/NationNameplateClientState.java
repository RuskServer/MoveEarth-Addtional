package com.ruskserver.moveearth_addtional.client;

import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import com.ruskserver.moveearth_addtional.network.S2C_NationNameplatesPacket;
import com.ruskserver.moveearth_addtional.s2.nation.NationNameplateRelation;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RenderNameTagEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@EventBusSubscriber(modid = Moveearth_addtional.MODID, bus = EventBusSubscriber.Bus.GAME, value = Dist.CLIENT)
public final class NationNameplateClientState {
    private static final Map<UUID, S2C_NationNameplatesPacket.Entry> ENTRIES = new HashMap<>();

    private NationNameplateClientState() {
    }

    public static void update(S2C_NationNameplatesPacket packet) {
        ENTRIES.clear();
        for (S2C_NationNameplatesPacket.Entry entry : packet.entries()) {
            ENTRIES.put(entry.playerId(), entry);
        }
    }

    @SubscribeEvent
    public static void onRenderNameTag(RenderNameTagEvent event) {
        if (PvpClientState.isMatchActive() || !(event.getEntity() instanceof Player player)) return;
        S2C_NationNameplatesPacket.Entry entry = ENTRIES.get(player.getUUID());
        if (entry == null || entry.prefix().isBlank()) return;
        ChatFormatting color = color(entry.relation());
        event.setContent(Component.literal(entry.prefix()).withStyle(color, ChatFormatting.BOLD)
                .append(Component.literal(event.getOriginalContent().getString()).withStyle(color)));
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        ENTRIES.clear();
    }

    private static ChatFormatting color(NationNameplateRelation relation) {
        return switch (relation) {
            case OWN_NATION -> ChatFormatting.AQUA;
            case ALLY -> ChatFormatting.GREEN;
            case HOSTILE -> ChatFormatting.RED;
            case NEUTRAL -> ChatFormatting.GRAY;
        };
    }
}
