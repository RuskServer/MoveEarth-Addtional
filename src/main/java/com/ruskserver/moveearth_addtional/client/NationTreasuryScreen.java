package com.ruskserver.moveearth_addtional.client;

import com.ruskserver.moveearth_addtional.client.ui.MoveEarthUi.Rect;
import com.ruskserver.moveearth_addtional.client.ui.SuppressesChatOverlay;
import com.ruskserver.moveearth_addtional.network.C2S_NationTreasuryPacket;
import com.ruskserver.moveearth_addtional.network.C2S_RequestS2HubPacket;
import com.ruskserver.moveearth_addtional.network.S2C_NationTreasuryPacket;
import com.ruskserver.moveearth_addtional.s2.S2HubTab;
import io.github.lightman314.lightmanscurrency.api.money.bank.reference.BankReference;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

import static com.ruskserver.moveearth_addtional.client.ui.MoveEarthUi.*;

public final class NationTreasuryScreen extends Screen implements SuppressesChatOverlay {
    private S2C_NationTreasuryPacket data;
    private int selected;

    public NationTreasuryScreen(S2C_NationTreasuryPacket data) {
        super(Component.translatable("screen.moveearth_addtional.treasury.title"));
        update(data);
    }

    public void update(S2C_NationTreasuryPacket packet) {
        data = packet;
        selected = packet.accounts().isEmpty() ? -1
                : Math.max(0, Math.min(packet.selectedAccount(), packet.accounts().size() - 1));
    }

    @Override public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) { }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        drawBackground(graphics, width, height);
        Rect panel = panel();
        drawPanel(graphics, panel);
        graphics.drawString(font, title, panel.x() + 18, panel.y() + 15, TEXT, false);
        graphics.drawString(font, Component.translatable("screen.moveearth_addtional.treasury.detail"),
                panel.x() + 18, panel.y() + 31, MUTED, false);
        drawClose(graphics, font, close(panel), close(panel).contains(mouseX, mouseY));

        Rect upkeep = new Rect(panel.x() + 20, panel.y() + 60, panel.width() - 40, 52);
        drawCard(graphics, upkeep, data.penalty() == com.ruskserver.moveearth_addtional.s2.territory.UpkeepPenalty.DISABLED
                ? DANGER : data.failedPayments() > 0 ? GOLD : SUCCESS, false, false);
        graphics.drawString(font, Component.translatable("screen.moveearth_addtional.treasury.cost", data.upkeep()),
                upkeep.x() + 13, upkeep.y() + 10, TEXT, false);
        Component status = data.penalty() != com.ruskserver.moveearth_addtional.s2.territory.UpkeepPenalty.CURRENT
                ? Component.translatable("screen.moveearth_addtional.treasury.penalty."
                + data.penalty().name().toLowerCase(java.util.Locale.ROOT), overdue(data.overdueSince()))
                : !data.enabled()
                ? Component.translatable("screen.moveearth_addtional.treasury.disabled")
                : data.failedPayments() > 0
                ? Component.translatable("screen.moveearth_addtional.treasury.overdue", data.failedPayments())
                : Component.translatable("screen.moveearth_addtional.treasury.enabled", remaining(data.nextDueAt()));
        graphics.drawString(font, status, upkeep.x() + 13, upkeep.y() + 29,
                data.penalty() == com.ruskserver.moveearth_addtional.s2.territory.UpkeepPenalty.DISABLED
                        ? DANGER : data.failedPayments() > 0 ? GOLD : MUTED, false);

        Rect account = new Rect(panel.x() + 20, panel.y() + 126, panel.width() - 40, 46);
        drawCard(graphics, account, ACCENT, false, false);
        graphics.drawString(font, Component.translatable("screen.moveearth_addtional.treasury.account"),
                account.x() + 13, account.y() + 7, MUTED, false);
        String name = selected < 0 || selected >= data.accountNames().size()
                ? Component.translatable("screen.moveearth_addtional.treasury.no_account").getString()
                : data.accountNames().get(selected);
        graphics.drawCenteredString(font, name, account.x() + account.width() / 2, account.y() + 26, TEXT);
        drawButton(graphics, font, previous(account), Component.literal("<"), ACCENT,
                previous(account).contains(mouseX, mouseY), data.canManage() && data.accounts().size() > 1);
        drawButton(graphics, font, next(account), Component.literal(">"), ACCENT,
                next(account).contains(mouseX, mouseY), data.canManage() && data.accounts().size() > 1);

        boolean selectable = data.canManage() && selected >= 0;
        drawButton(graphics, font, disable(panel), Component.translatable("screen.moveearth_addtional.treasury.disable"),
                DANGER, disable(panel).contains(mouseX, mouseY), data.canManage() && data.enabled());
        drawButton(graphics, font, pay(panel), Component.translatable("screen.moveearth_addtional.treasury.pay_now"),
                GOLD, pay(panel).contains(mouseX, mouseY), data.canManage() && data.enabled());
        drawButton(graphics, font, save(panel), Component.translatable("screen.moveearth_addtional.treasury.save_enable"),
                SUCCESS, save(panel).contains(mouseX, mouseY), selectable);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return super.mouseClicked(mouseX, mouseY, button);
        Rect panel = panel();
        if (close(panel).contains(mouseX, mouseY)) { onClose(); return true; }
        if (data.canManage() && data.accounts().size() > 1) {
            if (previous(new Rect(panel.x() + 20, panel.y() + 126, panel.width() - 40, 46)).contains(mouseX, mouseY)) {
                selected = (selected - 1 + data.accounts().size()) % data.accounts().size(); return true;
            }
            if (next(new Rect(panel.x() + 20, panel.y() + 126, panel.width() - 40, 46)).contains(mouseX, mouseY)) {
                selected = (selected + 1) % data.accounts().size(); return true;
            }
        }
        BankReference reference = selected >= 0 && selected < data.accounts().size() ? data.accounts().get(selected) : null;
        if (data.canManage() && data.enabled() && disable(panel).contains(mouseX, mouseY)) {
            send(C2S_NationTreasuryPacket.Action.DISABLE, reference); return true;
        }
        if (data.canManage() && data.enabled() && pay(panel).contains(mouseX, mouseY)) {
            send(C2S_NationTreasuryPacket.Action.PAY_NOW, reference); return true;
        }
        if (data.canManage() && reference != null && save(panel).contains(mouseX, mouseY)) {
            send(C2S_NationTreasuryPacket.Action.SAVE_ENABLE, reference); return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private static void send(C2S_NationTreasuryPacket.Action action, BankReference reference) {
        PacketDistributor.sendToServer(new C2S_NationTreasuryPacket(action, reference));
    }

    private static String remaining(long dueAt) {
        long minutes = Math.max(0L, dueAt - System.currentTimeMillis()) / 60_000L;
        return (minutes / 60L) + "h " + (minutes % 60L) + "m";
    }

    private static String overdue(long overdueSince) {
        long minutes = Math.max(0L, System.currentTimeMillis() - overdueSince) / 60_000L;
        return (minutes / 60L) + "h " + (minutes % 60L) + "m";
    }

    @Override public void onClose() { PacketDistributor.sendToServer(new C2S_RequestS2HubPacket(S2HubTab.OVERVIEW)); }
    @Override public boolean isPauseScreen() { return false; }
    private Rect panel() { int w = Math.min(470, width - 20), h = Math.min(250, height - 20); return new Rect((width-w)/2,(height-h)/2,w,h); }
    private static Rect close(Rect p) { return new Rect(p.right()-28,p.y()+8,20,20); }
    private static Rect previous(Rect r) { return new Rect(r.x()+8,r.y()+18,25,22); }
    private static Rect next(Rect r) { return new Rect(r.right()-33,r.y()+18,25,22); }
    private static Rect disable(Rect p) { return new Rect(p.x()+20,p.bottom()-40,92,22); }
    private static Rect pay(Rect p) { return new Rect(p.x()+121,p.bottom()-40,92,22); }
    private static Rect save(Rect p) { return new Rect(p.right()-132,p.bottom()-40,112,22); }
}
