package com.ruskserver.moveearth_addtional.client;

import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import com.ruskserver.moveearth_addtional.network.S2C_EventHudPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.scores.DisplaySlot;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;

import java.util.ArrayList;
import java.util.List;

@EventBusSubscriber(modid = Moveearth_addtional.MODID, bus = EventBusSubscriber.Bus.GAME, value = Dist.CLIENT)
public final class EventHud {
    private static S2C_EventHudPacket snapshot = S2C_EventHudPacket.inactive(0);

    private EventHud() { }

    public static void set(S2C_EventHudPacket value) {
        snapshot = value == null ? S2C_EventHudPacket.inactive(0) : value;
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        snapshot = S2C_EventHudPacket.inactive(0);
    }

    @SubscribeEvent
    public static void render(RenderGuiEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null || minecraft.options.hideGui
                || minecraft.screen != null || minecraft.getDebugOverlay().showDebugScreen()
                || minecraft.level.getScoreboard().getDisplayObjective(DisplaySlot.SIDEBAR) != null
                || !snapshot.active() && snapshot.pendingClaims() == 0) return;

        List<Line> lines = new ArrayList<>();
        if (snapshot.active()) {
            lines.add(new Line(snapshot.title(), 0xFFFFC76A));
            lines.add(new Line(snapshot.target() + "  残り" + snapshot.minutes() + "分", 0xFFE4E6E7));
            String rank = snapshot.rank() == 0 ? "未参加" : snapshot.rank() + "位";
            lines.add(new Line("自分 " + snapshot.score() + "点 · " + rank, 0xFF9AD5D1));
            for (int index = 0; index < snapshot.leaders().size(); index++) {
                S2C_EventHudPacket.Leader leader = snapshot.leaders().get(index);
                lines.add(new Line((index + 1) + ". " + leader.name() + "  " + leader.score(), 0xFFE0E0E0));
            }
        }
        if (snapshot.pendingClaims() > 0)
            lines.add(new Line("報酬あり  /event claim", 0xFFFFDC85));

        GuiGraphics graphics = event.getGuiGraphics();
        Font font = minecraft.font;
        int right = graphics.guiWidth() - 8;
        int top = graphics.guiHeight() / 2 - lines.size() * 11 / 2;
        for (int index = 0; index < lines.size(); index++) {
            Line line = lines.get(index);
            String label = font.plainSubstrByWidth(line.text(), 154);
            graphics.drawString(font, label, right - font.width(label), top + index * 11,
                    line.color(), true);
        }
    }

    private record Line(String text, int color) { }
}
