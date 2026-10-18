package com.ruskserver.moveearth_addtional.client;

import com.ruskserver.moveearth_addtional.client.ui.MoveEarthUi;
import com.ruskserver.moveearth_addtional.client.ui.MoveEarthUi.Rect;
import com.ruskserver.moveearth_addtional.client.ui.UiSounds;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * The strip of tabs across the top of the hub screens, like a browser's: the nation hub, the market,
 * the wallet, jobs, stats and events, one click apart.
 *
 * <p>Each tab asks the server for its screen the same way its command or key always has, so the server
 * needs nothing new; the screen that answers replaces the current one and draws the strip again with
 * its own tab selected. Every screen still opens by its command or key as before.
 */
public final class HubTabs {
    /** How long a clicked tab waits for its screen before it can be clicked again. */
    private static final long PENDING_MS = 3_000L;

    private static Tab pending;
    private static long pendingSince;

    private HubTabs() { }

    public enum Tab {
        NATION("nation"), MARKET("market"), BALANCE("balance"), JOBS("jobs"), STATS("stats"), EVENT("event"), WIKI("wiki"),
        /** Only while a PvP event is being hosted, or on the PvP screen itself. */
        PVP("pvp");

        private final String key;

        Tab(String key) {
            this.key = key;
        }

        Component label() {
            return Component.translatable("screen.moveearth_addtional.hub_tabs." + key);
        }
    }

    /** Draws the strip with {@code current} selected. Call it after the screen's own panel. */
    public static void render(GuiGraphics graphics, Font font, int screenWidth, Tab current,
                              int mouseX, int mouseY) {
        if (pending == current || pending != null && Util.getMillis() - pendingSince > PENDING_MS) pending = null;
        Tab[] tabs = visible(current);
        List<Rect> bounds = layout(font, screenWidth, tabs);
        for (int i = 0; i < tabs.length; i++) {
            Tab tab = tabs[i];
            Rect rect = bounds.get(i);
            boolean selected = tab == current;
            boolean hovered = !selected && rect.contains(mouseX, mouseY);
            if (hovered) UiSounds.hoveredControl(rect);
            graphics.fill(rect.x(), rect.y(), rect.right(), rect.bottom(),
                    selected ? 0xF21A2430 : hovered ? MoveEarthUi.CARD_HOVER : 0xE0121820);
            int edge = selected ? MoveEarthUi.ACCENT : hovered ? MoveEarthUi.BORDER_HOVER : MoveEarthUi.BORDER;
            graphics.fill(rect.x(), rect.y(), rect.right(), rect.y() + (selected ? 2 : 1), edge);
            graphics.fill(rect.x(), rect.y(), rect.x() + 1, rect.bottom(), MoveEarthUi.BORDER);
            graphics.fill(rect.right() - 1, rect.y(), rect.right(), rect.bottom(), MoveEarthUi.BORDER);
            Component label = tab == pending ? Component.empty().append(tab.label()).append("…") : tab.label();
            int color = selected ? MoveEarthUi.ACCENT : tab == pending ? MoveEarthUi.MUTED : MoveEarthUi.TEXT;
            graphics.drawCenteredString(font, label, rect.x() + rect.width() / 2,
                    rect.y() + (rect.height() - 8) / 2 + 1, color);
        }
    }

    /**
     * Opens the clicked tab's screen. Call it first in the screen's {@code mouseClicked}; a click on
     * the strip is consumed even when it opens nothing.
     */
    public static boolean mouseClicked(Font font, int screenWidth, Tab current,
                                       double mouseX, double mouseY, int button) {
        if (button != 0 || mouseY >= HubTabLayout.BOTTOM) return false;
        Tab[] tabs = visible(current);
        List<Rect> bounds = layout(font, screenWidth, tabs);
        for (int i = 0; i < tabs.length; i++) {
            if (!bounds.get(i).contains((int) mouseX, (int) mouseY)) continue;
            Tab tab = tabs[i];
            if (tab == current) return true;
            if (pending != null && Util.getMillis() - pendingSince <= PENDING_MS) return true;
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.player == null) return true;
            UiSounds.click();
            pending = tab;
            pendingSince = Util.getMillis();
            open(minecraft, tab);
            return true;
        }
        return false;
    }

    /** Where a centred panel of {@code panelHeight} starts below the strip. */
    public static int panelTop(int screenHeight, int panelHeight) {
        return HubTabLayout.panelTop(screenHeight, panelHeight);
    }

    /**
     * Call first in {@code init()}: a screen whose {@code panelWidth} by {@code panelHeight} panel does not
     * fit the player's GUI scale is shown at a smaller one while it is open. The strip may cover the
     * panel's empty top margin, above its title, so only that little more height is asked for: asking for
     * the strip's full height would drop a 1080p screen from GUI scale 4 straight to 2 for most panels.
     */
    public static void fit(net.minecraft.client.gui.screens.Screen screen, int panelWidth, int panelHeight) {
        com.ruskserver.moveearth_addtional.client.ui.ScreenFit.fit(screen, panelWidth + 8, panelHeight + 12);
    }

    /** Tallest panel that fits below the strip, for panels that grow with the screen. */
    public static int maxPanelHeight(int screenHeight) {
        return HubTabLayout.maxPanelHeight(screenHeight);
    }

    private static void open(Minecraft minecraft, Tab tab) {
        switch (tab) {
            case NATION -> S2HubScreen.requestFromTab();
            case MARKET -> minecraft.player.connection.sendCommand("market");
            case BALANCE -> minecraft.player.connection.sendCommand("balance");
            case JOBS -> minecraft.player.connection.sendCommand("jobs");
            case STATS -> minecraft.player.connection.sendCommand("stats");
            case EVENT -> minecraft.player.connection.sendCommand("event");
            case WIKI -> com.ruskserver.moveearth_addtional.client.wiki.WikiScreen.openFromTabs();
            case PVP -> minecraft.player.connection.sendCommand("pvp");
        }
    }

    /** The tabs shown: PvP only while an event is hosted, or when it is the screen being shown. */
    private static Tab[] visible(Tab current) {
        boolean pvp = current == Tab.PVP || com.ruskserver.moveearth_addtional.client.pvp.PvpClientState.isHosting();
        return java.util.Arrays.stream(Tab.values()).filter(tab -> tab != Tab.PVP || pvp).toArray(Tab[]::new);
    }

    private static List<Rect> layout(Font font, int screenWidth, Tab[] tabs) {
        int[] widths = new int[tabs.length];
        for (int i = 0; i < tabs.length; i++) widths[i] = font.width(tabs[i].label()) + font.width("…");
        return HubTabLayout.tabs(screenWidth, widths);
    }
}
