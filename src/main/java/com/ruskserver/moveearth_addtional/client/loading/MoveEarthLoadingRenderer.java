package com.ruskserver.moveearth_addtional.client.loading;

import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import com.ruskserver.moveearth_addtional.client.menu.StaticMeteorBackground;
import com.ruskserver.moveearth_addtional.client.ui.MoveEarthUi;
import com.ruskserver.moveearth_addtional.s2.tip.TipCatalog;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;

import java.util.List;

/** Shared loading view for connecting, receiving a level and generating chunks. */
public final class MoveEarthLoadingRenderer {
    private static final ResourceLocation LOGO = ResourceLocation.fromNamespaceAndPath(
            Moveearth_addtional.MODID, "menu/minecraft_title.png");
    private static final StaticMeteorBackground BACKGROUND = new StaticMeteorBackground();
    private static final long SESSION_GAP_MILLIS = 2_500L;
    private static final int PANEL = 0xC412161D;
    private static final int PANEL_BORDER = 0xE0354150;

    private static long sessionStartedAt = -1L;
    private static long sessionSeed;
    private static long lastRenderedAt = -1L;

    private MoveEarthLoadingRenderer() {
    }

    public static MoveEarthLoadingLayout.Layout render(GuiGraphics graphics, Font font,
                                                        int width, int height, Component status,
                                                        int progress, boolean hasCancel,
                                                        long nowMillis) {
        updateSession(nowMillis);
        MoveEarthLoadingLayout.Layout layout = MoveEarthLoadingLayout.calculate(width, height);
        BACKGROUND.render(graphics, width, height);
        graphics.fillGradient(0, 0, width, height, 0x24000000, 0x94000000);
        drawLogo(graphics, layout.logo());
        drawPanel(graphics, font, layout, status, progress, hasCancel, nowMillis);
        return layout;
    }

    private static void updateSession(long nowMillis) {
        if (sessionStartedAt < 0L || lastRenderedAt < 0L
                || nowMillis < lastRenderedAt || nowMillis - lastRenderedAt > SESSION_GAP_MILLIS) {
            sessionStartedAt = nowMillis;
            sessionSeed = nowMillis / 1_000L;
        }
        lastRenderedAt = nowMillis;
    }

    private static void drawLogo(GuiGraphics graphics, MoveEarthUi.Rect logo) {
        graphics.blit(LOGO, logo.x(), logo.y(), logo.width(), logo.height(),
                0.0F, 0.0F, 1024, 269, 1024, 269);
    }

    private static void drawPanel(GuiGraphics graphics, Font font,
                                  MoveEarthLoadingLayout.Layout layout, Component status,
                                  int progress, boolean hasCancel, long nowMillis) {
        MoveEarthUi.Rect panel = layout.panel();
        graphics.fill(panel.x(), panel.y(), panel.right(), panel.bottom(), PANEL);
        MoveEarthUi.drawBorder(graphics, panel, PANEL_BORDER);
        graphics.fill(panel.x(), panel.y(), panel.right(), panel.y() + 2, MoveEarthUi.SUCCESS);

        int textX = panel.x() + 12;
        int tipRight = hasCancel ? layout.cancel().x() - 10 : panel.right() - 12;
        TipCatalog.Tip tip = currentTip(nowMillis);
        Component tipLabel = Component.translatable("screen.moveearth_addtional.loading.tip")
                .append("  ").append(Component.translatable(tip.titleKey()));
        graphics.drawString(font, tipLabel, textX, panel.y() + 9, MoveEarthUi.SUCCESS, false);

        List<FormattedCharSequence> lines = font.split(Component.translatable(tip.bodyKey()),
                Math.max(32, tipRight - textX));
        int bodyY = panel.y() + 23;
        int statusY = panel.bottom() - 22;
        for (FormattedCharSequence line : lines) {
            if (bodyY + font.lineHeight > statusY - 3) break;
            graphics.drawString(font, line, textX, bodyY, MoveEarthUi.TEXT, false);
            bodyY += font.lineHeight + 1;
        }

        drawStatus(graphics, font, panel, status, progress, statusY);
        drawProgress(graphics, layout.progress(), progress, nowMillis);
    }

    private static TipCatalog.Tip currentTip(long nowMillis) {
        int index = LoadingTipRotation.index(sessionSeed, nowMillis - sessionStartedAt,
                TipCatalog.ALL.size());
        return TipCatalog.ALL.get(index);
    }

    private static void drawStatus(GuiGraphics graphics, Font font, MoveEarthUi.Rect panel,
                                   Component status, int progress, int y) {
        int x = panel.x() + 12;
        String brand = "[MoveEarth]";
        graphics.drawString(font, brand, x, y, MoveEarthUi.SUCCESS, false);
        x += font.width(brand);
        String chevrons = " >>> ";
        graphics.drawString(font, chevrons, x, y, MoveEarthUi.MUTED, false);
        x += font.width(chevrons);
        int percentWidth = progress >= 0 ? font.width("100%") + 12 : 0;
        graphics.enableScissor(x, y - 1, panel.right() - 12 - percentWidth, y + font.lineHeight + 1);
        graphics.drawString(font, status, x, y, MoveEarthUi.TEXT, false);
        graphics.disableScissor();
        if (progress >= 0) {
            String percentage = Math.max(0, Math.min(100, progress)) + "%";
            graphics.drawString(font, percentage, panel.right() - 12 - font.width(percentage),
                    y, MoveEarthUi.ACCENT, false);
        }
    }

    private static void drawProgress(GuiGraphics graphics, MoveEarthUi.Rect bar,
                                     int progress, long nowMillis) {
        graphics.fill(bar.x(), bar.y(), bar.right(), bar.bottom(), MoveEarthUi.BAR_BACKGROUND);
        if (progress >= 0) {
            int filled = Math.round(bar.width() * Math.max(0, Math.min(100, progress)) / 100.0F);
            if (filled > 0) {
                graphics.fillGradient(bar.x(), bar.y(), bar.x() + filled, bar.bottom(),
                        MoveEarthUi.SUCCESS, MoveEarthUi.ACCENT);
            }
            return;
        }

        int segment = Math.max(24, bar.width() / 4);
        double phase = Math.floorMod(nowMillis - sessionStartedAt, 2_400L) / 2_400.0D;
        int start = bar.x() - segment + (int) Math.round((bar.width() + segment) * phase);
        int clippedStart = Math.max(bar.x(), start);
        int clippedEnd = Math.min(bar.right(), start + segment);
        if (clippedEnd > clippedStart) {
            graphics.fillGradient(clippedStart, bar.y(), clippedEnd, bar.bottom(),
                    MoveEarthUi.SUCCESS, MoveEarthUi.ACCENT);
        }
    }
}
