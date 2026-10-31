package com.ruskserver.moveearth_addtional.client;

import com.ruskserver.moveearth_addtional.client.ui.SuppressesChatOverlay;
import com.ruskserver.moveearth_addtional.network.c2s.nation.C2S_RequestS2HubPacket;
import com.ruskserver.moveearth_addtional.s2.S2HubTab;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.List;
import java.util.UUID;
import static com.ruskserver.moveearth_addtional.client.ui.MoveEarthUi.*;

/** The shared selection/confirmation interaction for the two owner-facing management actions. */
abstract class NationGovernanceScreen extends Screen implements SuppressesChatOverlay {
    protected record Choice(UUID id, Component title, Component detail, boolean enabled) { }
    protected UUID selected;
    protected boolean pending;
    protected Component status = Component.empty();
    protected int statusColor = MUTED;
    private boolean started, confirming;
    private int scroll, pendingTicks;

    NationGovernanceScreen(Component title) { super(title); }
    protected abstract List<Choice> choices();
    protected abstract Component detail();
    protected abstract Component heading();
    protected abstract Component confirmation();
    protected abstract void submit();
    protected abstract void refresh();
    protected void initialLoad() { }
    protected boolean allowed() { return true; }

    @Override protected void init() { if (!started) { started = true; initialLoad(); } }
    protected void beginRequest() {
        pending = true; pendingTicks = 0; confirming = false;
        status = Component.translatable("screen.moveearth_addtional.governance.loading"); statusColor = MUTED;
    }
    protected void finished(Component message, int color) { pending = false; status = message; statusColor = color; }
    private Choice selection() { return choices().stream().filter(c -> c.id().equals(selected)).findFirst().orElse(null); }
    private boolean canSubmit() { var c = selection(); return !pending && allowed() && c != null && c.enabled(); }
    private Rect row(Rect list, int index) { return new Rect(list.x(), list.y() + index * 46 - scroll, Math.max(0, list.width() - 7), 42); }
    private Rect modal() { int w = Math.min(420, width - 20), h = Math.min(170, height - 20); return new Rect((width - w) / 2, (height - h) / 2, w, h); }
    private Rect cancel(Rect modal) { return new Rect(modal.x() + 12, modal.bottom() - 34, (modal.width() - 32) / 2, 22); }
    private Rect accept(Rect modal) { Rect left = cancel(modal); return new Rect(left.right() + 8, left.y(), left.width(), left.height()); }

    @Override public void renderBackground(GuiGraphics graphics, int mx, int my, float tick) { }
    @Override public void render(GuiGraphics graphics, int mx, int my, float tick) {
        drawBackground(graphics, width, height);
        var l = NationGovernanceLayout.calculate(width, height);
        var p = l.panel(); drawPanel(graphics, p);
        graphics.drawString(font, title, p.x() + 14, p.y() + 12, GOLD, false);
        if (!l.compact()) drawWrapped(graphics, font, detail(), p.x() + 14, p.y() + 31, p.width() - 28, MUTED);
        graphics.drawString(font, font.plainSubstrByWidth(heading().getString(), p.width() - 28),
                p.x() + 14, p.y() + (l.compact() ? 34 : 66), TEXT, false);
        graphics.drawString(font, font.plainSubstrByWidth(status.getString(), p.width() - 28),
                p.x() + 14, p.y() + (l.compact() ? 51 : 83), statusColor, false);
        List<Choice> choices = choices();
        Component hover = null;
        scroll = Math.min(scroll, Math.max(0, choices.size() * 46 - l.list().height()));
        graphics.enableScissor(l.list().x(), l.list().y(), l.list().right(), l.list().bottom());
        if (choices.isEmpty() && !pending) drawWrapped(graphics, font,
                Component.translatable("screen.moveearth_addtional.governance.empty"), l.list().x() + 8,
                l.list().y() + 8, l.list().width() - 16, MUTED);
        for (int i = 0; i < choices.size(); i++) {
            Choice c = choices.get(i); Rect row = row(l.list(), i);
            if (row.bottom() <= l.list().y() || row.y() >= l.list().bottom()) continue;
            if (l.list().contains(mx, my) && row.contains(mx, my)) hover = c.detail();
            drawCard(graphics, row, c.enabled() ? ACCENT : MUTED, c.id().equals(selected), row.contains(mx, my));
            graphics.drawString(font, font.plainSubstrByWidth(c.title().getString(), row.width() - 18), row.x() + 8, row.y() + 7,
                    c.enabled() ? TEXT : MUTED, false);
            graphics.drawString(font, font.plainSubstrByWidth(c.detail().getString(), row.width() - 18), row.x() + 8, row.y() + 25, MUTED, false);
        }
        graphics.disableScissor();
        drawScrollbar(graphics, new Rect(l.list().right() - 4, l.list().y(), 4, l.list().height()),
                l.list().height(), choices.size() * 46, scroll);
        drawButton(graphics, font, l.back(), Component.translatable("screen.moveearth_addtional.nation.cancel"), MUTED, l.back().contains(mx, my), !pending);
        drawButton(graphics, font, l.refresh(), Component.translatable("screen.moveearth_addtional.governance.refresh"), ACCENT, l.refresh().contains(mx, my), !pending);
        drawButton(graphics, font, l.confirm(), Component.translatable("screen.moveearth_addtional.governance.review"),
                GOLD, l.confirm().contains(mx, my), canSubmit());
        super.render(graphics, mx, my, tick);
        if (hover != null && !confirming) graphics.renderTooltip(font, hover, mx, my);
        if (confirming) {
            drawModalBackdrop(graphics, width, height); Rect modal = modal(); drawPanel(graphics, modal);
            graphics.drawString(font, title, modal.x() + 14, modal.y() + 14, GOLD, false);
            graphics.enableScissor(modal.x() + 12, modal.y() + 31, modal.right() - 12, modal.bottom() - 41);
            drawWrapped(graphics, font, confirmation(), modal.x() + 14, modal.y() + 35, modal.width() - 28, TEXT);
            graphics.disableScissor();
            drawButton(graphics, font, cancel(modal), Component.translatable("screen.moveearth_addtional.nation.cancel"), MUTED, cancel(modal).contains(mx, my), true);
            drawButton(graphics, font, accept(modal), Component.translatable("screen.moveearth_addtional.nation.settings.confirm"), DANGER, accept(modal).contains(mx, my), canSubmit());
        }
    }

    @Override public boolean mouseClicked(double x, double y, int button) {
        if (button != 0) return false;
        if (confirming) {
            if (cancel(modal()).contains(x, y)) confirming = false;
            else if (accept(modal()).contains(x, y) && canSubmit()) submit();
            return true;
        }
        if (pending) return true;
        var l = NationGovernanceLayout.calculate(width, height);
        if (l.back().contains(x, y)) { onClose(); return true; }
        if (l.refresh().contains(x, y)) { refresh(); return true; }
        if (l.confirm().contains(x, y) && canSubmit()) { confirming = true; return true; }
        if (l.list().contains(x, y)) {
            var rows = choices();
            for (int i = 0; i < rows.size(); i++) if (row(l.list(), i).contains(x, y)) {
                selected = rows.get(i).enabled() ? rows.get(i).id() : null;
                if (!rows.get(i).enabled()) { status = rows.get(i).detail(); statusColor = DANGER; }
                return true;
            }
        }
        return super.mouseClicked(x, y, button);
    }
    @Override public boolean mouseScrolled(double x, double y, double sx, double sy) {
        var l = NationGovernanceLayout.calculate(width, height);
        if (!confirming && l.list().contains(x, y)) { scroll = scroll(scroll, sy, 23, choices().size() * 46, l.list().height()); return true; }
        return false;
    }
    @Override public boolean keyPressed(int key, int scan, int modifiers) {
        if (key == 256 && confirming) { confirming = false; return true; }
        return super.keyPressed(key, scan, modifiers);
    }
    @Override public void tick() {
        super.tick();
        if (pending && ++pendingTicks >= 200) finished(Component.translatable("screen.moveearth_addtional.governance.timeout"), DANGER);
    }
    @Override public void onClose() { PacketDistributor.sendToServer(new C2S_RequestS2HubPacket(S2HubTab.OVERVIEW)); }
    @Override public boolean isPauseScreen() { return false; }
}
