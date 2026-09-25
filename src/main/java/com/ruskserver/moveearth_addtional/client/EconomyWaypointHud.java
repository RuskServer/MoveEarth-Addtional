package com.ruskserver.moveearth_addtional.client;

import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import com.ruskserver.moveearth_addtional.network.S2C_WaypointPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;

import java.util.Locale;

/** Economy text and navigation card drawn after vanilla chat/HUD, never over a full screen. */
@EventBusSubscriber(modid = Moveearth_addtional.MODID, bus = EventBusSubscriber.Bus.GAME, value = Dist.CLIENT)
public final class EconomyWaypointHud {
    private static Long balance;
    private static String balanceText = "";
    private static S2C_WaypointPacket waypoint = S2C_WaypointPacket.clear();
    private EconomyWaypointHud() { }

    public static void setBalance(long value) {
        balance = Math.max(0L, value);
        balanceText = String.format(Locale.US, "%,d TC", balance);
    }
    public static void setWaypoint(S2C_WaypointPacket value) {
        waypoint = value == null ? S2C_WaypointPacket.clear() : value;
    }
    static S2C_WaypointPacket waypoint() { return waypoint; }

    @SubscribeEvent public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        balance = null;
        balanceText = "";
        waypoint = S2C_WaypointPacket.clear();
    }

    @SubscribeEvent public static void render(RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.options.hideGui || mc.screen != null
                || mc.getDebugOverlay().showDebugScreen()) return;
        GuiGraphics g = event.getGuiGraphics();
        int right = g.guiWidth() - 8;
        int y = mc.player.getActiveEffects().isEmpty() ? 8 : 58;
        if (balance != null) {
            g.drawString(mc.font, balanceText, right - mc.font.width(balanceText), y, 0xFFFFCF72, true);
            y += 16;
        }
        if (!waypoint.active()) return;
        if (mc.level.dimension().location().equals(waypoint.dimension())) {
            // The scalable in-world marker is the primary navigation display.
            return;
        }
        String detail = "別ディメンション: " + waypoint.dimension();
        String title = mc.font.plainSubstrByWidth(waypoint.name(), 183);
        detail = mc.font.plainSubstrByWidth(detail, 183);
        int width = Math.max(115, Math.max(mc.font.width(title), mc.font.width(detail)) + 22);
        int x = right - width;
        card(g, x, y, width, 35, 0xFF70AADA);
        g.drawString(mc.font, title, x + 10, y + 6, 0xFFE8EDF3, false);
        g.drawString(mc.font, detail, x + 10, y + 20, 0xFF9CCAD5, false);
    }

    private static void card(GuiGraphics g, int x, int y, int width, int height, int accent) {
        g.fill(x, y, x + width, y + height, 0xD517252B);
        g.fill(x, y, x + 3, y + height, accent);
        g.fill(x + 3, y, x + width, y + 1, 0x77587780);
    }
}
