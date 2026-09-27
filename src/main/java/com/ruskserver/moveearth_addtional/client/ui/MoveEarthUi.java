package com.ruskserver.moveearth_addtional.client.ui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * Shared visual language for MoveEarth's custom, render-driven screens.
 *
 * <p>The helpers deliberately do not create vanilla widgets. Screens retain
 * ownership of focus and input while drawing a consistent panel, card and
 * control vocabulary.</p>
 */
public final class MoveEarthUi {
    public static final int TEXT = 0xFFE8EDF3;
    public static final int MUTED = 0xFF8F9AA8;
    public static final int PANEL = 0xF012161D;
    public static final int CARD = 0xFF1B222C;
    public static final int CARD_HOVER = 0xFF242E3B;
    public static final int CARD_SELECTED = 0xFF263542;
    public static final int BORDER = 0xFF354150;
    public static final int BORDER_HOVER = 0xFF536173;
    public static final int DISABLED = 0xFF4A515B;
    public static final int TRACK = 0xFF202832;
    public static final int BAR_BACKGROUND = 0xFF0C1015;

    public static final int ACCENT = 0xFF5DCBFF;
    public static final int SUCCESS = 0xFF68E09B;
    public static final int DANGER = 0xFFFF6577;
    public static final int GOLD = 0xFFFFB454;

    private static final int BACKGROUND_TOP = 0xD0080B10;
    private static final int BACKGROUND_BOTTOM = 0xE010151D;

    private MoveEarthUi() {
    }

    public static void drawBackground(GuiGraphics graphics, int width, int height) {
        graphics.fillGradient(0, 0, width, height, BACKGROUND_TOP, BACKGROUND_BOTTOM);
    }

    public static void drawPanel(GuiGraphics graphics, Rect bounds) {
        graphics.fill(bounds.x(), bounds.y(), bounds.right(), bounds.bottom(), PANEL);
        drawBorder(graphics, bounds, BORDER);
    }

    public static void drawCard(GuiGraphics graphics, Rect bounds, int accent,
                                boolean selected, boolean hovered) {
        graphics.fill(bounds.x(), bounds.y(), bounds.right(), bounds.bottom(),
                selected ? CARD_SELECTED : hovered ? CARD_HOVER : CARD);
        graphics.fill(bounds.x(), bounds.y(), bounds.x() + (selected ? 4 : 2), bounds.bottom(),
                selected ? accent : BORDER);
        drawBorder(graphics, bounds, selected ? accent : hovered ? BORDER_HOVER : BORDER);
    }

    public static void drawButton(GuiGraphics graphics, Font font, Rect bounds, Component label,
                                  int accent, boolean hovered, boolean enabled) {
        graphics.fill(bounds.x(), bounds.y(), bounds.right(), bounds.bottom(),
                hovered && enabled ? CARD_HOVER : CARD);
        graphics.fill(bounds.x(), bounds.y(), bounds.x() + 3, bounds.bottom(),
                enabled ? accent : DISABLED);
        drawBorder(graphics, bounds, hovered && enabled ? accent : BORDER);
        int textY = bounds.y() + Math.max(1, (bounds.height() - 8) / 2);
        graphics.drawCenteredString(font, label, bounds.x() + bounds.width() / 2, textY,
                enabled ? accent : MUTED);
    }

    /**
     * One option of a segmented choice. The selected option uses the same selected-card treatment as
     * /pvp loadout cards; a disabled but selected option stays visible so the current value is still readable.
     */
    public static void drawChoice(GuiGraphics graphics, Font font, Rect bounds, Component label,
                                  int accent, boolean selected, boolean hovered, boolean enabled) {
        boolean active = hovered && enabled && !selected;
        graphics.fill(bounds.x(), bounds.y(), bounds.right(), bounds.bottom(),
                selected ? CARD_SELECTED : active ? CARD_HOVER : CARD);
        drawBorder(graphics, bounds, selected ? (enabled ? accent : BORDER_HOVER) : active ? BORDER_HOVER : BORDER);
        int textY = bounds.y() + Math.max(1, (bounds.height() - 8) / 2);
        int color = !enabled ? (selected ? MUTED : DISABLED) : selected ? accent : TEXT;
        graphics.drawCenteredString(font, label, bounds.x() + bounds.width() / 2, textY, color);
    }

    /** Equal-width slot {@code index} of {@code count} across {@code row}, separated by {@code gap} pixels. */
    public static Rect slot(Rect row, int index, int count, int gap) {
        int safeCount = Math.max(1, count);
        int width = Math.max(1, (row.width() - gap * (safeCount - 1)) / safeCount);
        return new Rect(row.x() + index * (width + gap), row.y(), width, row.height());
    }

    /** Draws word-wrapped text and returns the height it used. */
    public static int drawWrapped(GuiGraphics graphics, Font font, Component text, int x, int y,
                                  int width, int color) {
        int lineY = y;
        for (var line : font.split(text, Math.max(1, width))) {
            graphics.drawString(font, line, x, lineY, color, false);
            lineY += LINE_HEIGHT;
        }
        return lineY - y;
    }

    public static int wrappedHeight(Font font, Component text, int width) {
        return font.split(text, Math.max(1, width)).size() * LINE_HEIGHT;
    }

    public static final int LINE_HEIGHT = 10;

    public static void drawTab(GuiGraphics graphics, Font font, Rect bounds, Component label,
                               boolean selected, boolean hovered) {
        graphics.fill(bounds.x(), bounds.y(), bounds.right(), bounds.bottom(),
                selected ? 0xFF26394A : hovered ? CARD_HOVER : CARD);
        graphics.fill(bounds.x(), bounds.bottom() - 3, bounds.right(), bounds.bottom(),
                selected ? ACCENT : BORDER);
        int textY = bounds.y() + Math.max(1, (bounds.height() - 8) / 2);
        graphics.drawCenteredString(font, label, bounds.x() + bounds.width() / 2, textY,
                selected ? ACCENT : TEXT);
    }

    public static void drawClose(GuiGraphics graphics, Font font, Rect bounds, boolean hovered) {
        graphics.drawCenteredString(font, "×", bounds.x() + bounds.width() / 2,
                bounds.y() + Math.max(1, (bounds.height() - 8) / 2), hovered ? DANGER : MUTED);
    }

    public static void drawModalBackdrop(GuiGraphics graphics, int width, int height) {
        graphics.fill(0, 0, width, height, 0xA0000000);
    }

    public static void drawToast(GuiGraphics graphics, Font font, int screenWidth, int screenHeight,
                                 Component message, int accent) {
        int toastWidth = Math.min(screenWidth - 24, Math.max(150, font.width(message) + 28));
        Rect bounds = new Rect((screenWidth - toastWidth) / 2, screenHeight - 42, toastWidth, 26);
        graphics.fill(bounds.x(), bounds.y(), bounds.right(), bounds.bottom(), PANEL);
        graphics.fill(bounds.x(), bounds.y(), bounds.x() + 3, bounds.bottom(), accent);
        drawBorder(graphics, bounds, accent);
        graphics.drawCenteredString(font, message, bounds.x() + bounds.width() / 2, bounds.y() + 9, TEXT);
    }

    public static void drawScrollbar(GuiGraphics graphics, Rect track, int viewportHeight,
                                     int contentHeight, int scrollOffset) {
        if (contentHeight <= viewportHeight || viewportHeight <= 0) return;

        int maxScroll = contentHeight - viewportHeight;
        int thumbHeight = Math.max(20, (int) ((float) viewportHeight / contentHeight * track.height()));
        int clampedOffset = MoveEarthUiMath.clamp(scrollOffset, 0, maxScroll);
        int thumbY = track.y() + (int) ((float) clampedOffset / maxScroll * (track.height() - thumbHeight));
        graphics.fill(track.x(), track.y(), track.right(), track.bottom(), TRACK);
        graphics.fill(track.x(), thumbY, track.right(), thumbY + thumbHeight, BORDER_HOVER);
    }

    public static void drawBorder(GuiGraphics graphics, Rect bounds, int color) {
        graphics.fill(bounds.x(), bounds.y(), bounds.right(), bounds.y() + 1, color);
        graphics.fill(bounds.x(), bounds.bottom() - 1, bounds.right(), bounds.bottom(), color);
        graphics.fill(bounds.x(), bounds.y() + 1, bounds.x() + 1, bounds.bottom() - 1, color);
        graphics.fill(bounds.right() - 1, bounds.y() + 1, bounds.right(), bounds.bottom() - 1, color);
    }

    public static int scroll(int current, double wheelDelta, int step, int contentHeight, int viewportHeight) {
        return MoveEarthUiMath.scroll(current, wheelDelta, step, contentHeight, viewportHeight);
    }

    public record Rect(int x, int y, int width, int height) {
        public Rect {
            if (width < 0 || height < 0) {
                throw new IllegalArgumentException("UI rectangle dimensions must be non-negative");
            }
        }

        public int right() {
            return x + width;
        }

        public int bottom() {
            return y + height;
        }

        public boolean contains(double mouseX, double mouseY) {
            return mouseX >= x && mouseX < right() && mouseY >= y && mouseY < bottom();
        }
    }
}
