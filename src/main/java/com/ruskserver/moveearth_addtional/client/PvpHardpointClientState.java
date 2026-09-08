package com.ruskserver.moveearth_addtional.client;

import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import com.ruskserver.moveearth_addtional.network.S2C_PvpZonePacket;
import com.ruskserver.moveearth_addtional.pvp.PvpZoneState;
import net.minecraft.Util;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;

@EventBusSubscriber(modid = Moveearth_addtional.MODID, bus = EventBusSubscriber.Bus.GAME, value = Dist.CLIENT)
public final class PvpHardpointClientState {
    private static final long COLOR_TRANSITION_MILLIS = 280L;
    private static S2C_PvpZonePacket zone = S2C_PvpZonePacket.inactive();
    private static int previousColor = baseColor(PvpZoneState.NEUTRAL);
    private static int targetColor = previousColor;
    private static long transitionStartedAt;

    private PvpHardpointClientState() {
    }

    public static void update(S2C_PvpZonePacket packet) {
        long now = Util.getMillis();
        previousColor = color(now);
        targetColor = baseColor(packet.state());
        transitionStartedAt = now;
        zone = packet;
    }

    static S2C_PvpZonePacket zone() {
        return zone;
    }

    static int color(long now) {
        if (transitionStartedAt == 0L) return targetColor;
        float progress = Math.min(1.0F, (now - transitionStartedAt) / (float) COLOR_TRANSITION_MILLIS);
        progress = progress * progress * (3.0F - 2.0F * progress);
        return lerpColor(previousColor, targetColor, progress);
    }

    public static void reset() {
        zone = S2C_PvpZonePacket.inactive();
        previousColor = targetColor = baseColor(PvpZoneState.NEUTRAL);
        transitionStartedAt = 0L;
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        reset();
    }

    private static int baseColor(PvpZoneState state) {
        return switch (state) {
            case RED -> 0xFF4052;
            case BLUE -> 0x438CFF;
            case CONTESTED -> 0xFFC247;
            case NEUTRAL -> 0xE8F4FF;
        };
    }

    private static int lerpColor(int from, int to, float progress) {
        int red = Math.round(((from >> 16) & 0xFF) + (((to >> 16) & 0xFF) - ((from >> 16) & 0xFF)) * progress);
        int green = Math.round(((from >> 8) & 0xFF) + (((to >> 8) & 0xFF) - ((from >> 8) & 0xFF)) * progress);
        int blue = Math.round((from & 0xFF) + ((to & 0xFF) - (from & 0xFF)) * progress);
        return red << 16 | green << 8 | blue;
    }
}
