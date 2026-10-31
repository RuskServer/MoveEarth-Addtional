package com.ruskserver.moveearth_addtional.client.pvp;

import com.ruskserver.moveearth_addtional.network.c2s.pvp.C2S_PvpExchangePacket;
import com.ruskserver.moveearth_addtional.pvp.reward.PvpExchange;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

import static com.ruskserver.moveearth_addtional.client.ui.MoveEarthUi.*;

/** The PvP point exchange: a card for each of {@link PvpExchange}'s rewards, opened over the PvP screen. */
public final class PvpExchangeScreen extends Screen implements com.ruskserver.moveearth_addtional.client.ui.SuppressesChatOverlay {
    private static final int WIDTH = 320;
    private static final int HEIGHT = 92;
    private static final int CARD_HEIGHT = 44;

    private final Screen parent;
    private final int points;
    private final ItemStack[] icons;

    public PvpExchangeScreen(Screen parent, int points) {
        super(Component.literal("ポイント交換"));
        this.parent = parent;
        this.points = points;
        PvpExchange[] values = PvpExchange.values();
        this.icons = new ItemStack[values.length];
        for (int i = 0; i < values.length; i++) this.icons[i] = values[i].stack();
    }

    @Override
    protected void init() {
        // The PvP screen it opens over is drawn behind it, so it takes that screen's scale and lays it out again.
        com.ruskserver.moveearth_addtional.client.HubTabs.fit(this, PvpScreen.PANEL_WIDTH, PvpScreen.PANEL_HEIGHT);
        if (parent != null) parent.init(minecraft, width, height);
        super.init();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
    }

    private int left() {
        return (width - WIDTH) / 2;
    }

    private int top() {
        return (height - HEIGHT) / 2;
    }

    private Rect card(int index) {
        return new Rect(left() + 12, top() + 34 + index * (CARD_HEIGHT + 6), WIDTH - 24, CARD_HEIGHT);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (parent != null) parent.render(graphics, -1, -1, partialTick);
        drawModalBackdrop(graphics, width, height);
        drawPanel(graphics, new Rect(left(), top(), WIDTH, HEIGHT));
        graphics.drawString(font, title, left() + 12, top() + 12, TEXT, false);
        String balance = points + " PvP PT";
        graphics.drawString(font, balance, left() + WIDTH - 12 - font.width(balance), top() + 12, GOLD, false);

        PvpExchange[] values = PvpExchange.values();
        for (int i = 0; i < values.length; i++) {
            PvpExchange exchange = values[i];
            Rect card = card(i);
            boolean affordable = points >= exchange.cost();
            drawCard(graphics, card, affordable ? GOLD : DISABLED, false, affordable && card.contains(mouseX, mouseY));
            graphics.renderItem(icons[i], card.x() + 10, card.y() + 14);
            graphics.drawString(font, exchange.displayName(), card.x() + 34, card.y() + 10, affordable ? TEXT : MUTED, false);
            String cost = exchange.cost() + "pt";
            graphics.drawString(font, cost, card.right() - 12 - font.width(cost), card.y() + 10, affordable ? GOLD : MUTED, false);
            graphics.drawString(font, "着火済み。Createの加熱にすぐ使える", card.x() + 34, card.y() + 25, MUTED, false);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return super.mouseClicked(mouseX, mouseY, button);
        PvpExchange[] values = PvpExchange.values();
        for (int i = 0; i < values.length; i++) {
            if (card(i).contains(mouseX, mouseY)) {
                if (points >= values[i].cost()) {
                    PacketDistributor.sendToServer(new C2S_PvpExchangePacket(i));
                    minecraft.setScreen(null);
                }
                return true;
            }
        }
        if (!new Rect(left(), top(), WIDTH, HEIGHT).contains(mouseX, mouseY)) {
            onClose();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }
}
