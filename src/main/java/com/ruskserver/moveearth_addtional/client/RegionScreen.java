package com.ruskserver.moveearth_addtional.client;

import com.ruskserver.moveearth_addtional.region.RegionMaterialNames;
import com.ruskserver.moveearth_addtional.region.RegionSnapshot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * What the player's region holds, and what the ones next to it hold.
 *
 * <p>The question this answers is "why is there no gold here", which without
 * an answer is indistinguishable from broken ore generation. It is deliberately
 * not a map: it shows the region underfoot and the ones bordering it, because
 * those are the ones worth walking to and the ones worth trading with.
 *
 * <p>A neighbour nobody has visited shows as unknown rather than as empty. The
 * two look different on purpose -- "nothing here" and "nobody has looked" lead
 * to opposite decisions.
 */
public final class RegionScreen extends Screen {

    private static final int PANEL_WIDTH = 340;
    private static final int ROW_HEIGHT = 46;
    private static final int HEADER = 56;

    private static final int BACKDROP = 0xC0101713;
    private static final int PANEL = 0xF016211A;
    private static final int BORDER = 0xFF4E5A48;
    private static final int CURRENT_BORDER = 0xFF75A84B;
    private static final int TEXT = 0xFFEEF5E7;
    private static final int MUTED = 0xFF8B9784;
    private static final int ACCENT = 0xFF9BC86A;

    private RegionSnapshot snapshot;

    public RegionScreen(RegionSnapshot snapshot) {
        super(Component.translatable("screen.moveearth_addtional.region.title"));
        this.snapshot = snapshot;
    }

    public void update(RegionSnapshot updated) {
        this.snapshot = updated;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        List<RegionSnapshot.Entry> entries = snapshot.regions();
        int panelHeight = Math.min(height - 40, HEADER + Math.max(1, entries.size()) * ROW_HEIGHT + 12);
        int x = (width - PANEL_WIDTH) / 2;
        int y = (height - panelHeight) / 2;

        graphics.fill(0, 0, width, height, BACKDROP);
        graphics.fill(x, y, x + PANEL_WIDTH, y + panelHeight, PANEL);
        graphics.renderOutline(x, y, PANEL_WIDTH, panelHeight, BORDER);

        graphics.drawString(font, title, x + 14, y + 14, TEXT, false);
        graphics.drawString(font, Component.translatable("screen.moveearth_addtional.region.subtitle"),
                x + 14, y + 28, MUTED, false);
        graphics.fill(x + 14, y + 44, x + PANEL_WIDTH - 14, y + 45, BORDER);

        if (entries.isEmpty()) {
            graphics.drawString(font, Component.translatable("screen.moveearth_addtional.region.none"),
                    x + 14, y + HEADER + 6, MUTED, false);
            return;
        }
        int row = y + HEADER;
        for (RegionSnapshot.Entry entry : entries) {
            if (row + ROW_HEIGHT > y + panelHeight) {
                break;
            }
            renderEntry(graphics, entry, x + 10, row, PANEL_WIDTH - 20);
            row += ROW_HEIGHT;
        }
    }

    private void renderEntry(GuiGraphics graphics, RegionSnapshot.Entry entry, int x, int y, int rowWidth) {
        graphics.renderOutline(x, y, rowWidth, ROW_HEIGHT - 6, entry.current() ? CURRENT_BORDER : BORDER);
        Component name = Component.translatable("message.moveearth_addtional.region.name", entry.id());
        graphics.drawString(font, entry.current()
                        ? Component.translatable("screen.moveearth_addtional.region.here", name)
                        : name,
                x + 8, y + 7, entry.current() ? ACCENT : TEXT, false);

        if (!entry.known()) {
            graphics.drawString(font, Component.translatable("screen.moveearth_addtional.region.unvisited"),
                    x + 8, y + 22, MUTED, false);
            return;
        }
        Component strategic = entry.exclusives().isEmpty()
                ? Component.translatable("screen.moveearth_addtional.region.no_exclusive")
                : Component.translatable("screen.moveearth_addtional.region.exclusive",
                        RegionMaterialNames.list(entry.exclusives()));
        graphics.drawString(font, strategic, x + 8, y + 21, TEXT, false);

        if (!entry.specialty().isBlank() || !entry.shortage().isBlank()) {
            graphics.drawString(font, Component.translatable("screen.moveearth_addtional.region.common",
                            RegionMaterialNames.of(entry.specialty()),
                            RegionMaterialNames.of(entry.shortage())),
                    x + 8, y + 31, MUTED, false);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
