package com.ruskserver.moveearth_addtional.compat.jei;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.List;

/** Shared layout for the Nether gate and blaze rod JEI pages: slots on top, rules below. */
final class NetherCategoryStyle {
    static final int WIDTH = 162;
    static final int SLOT_ROW_Y = 6;
    static final int RULES_Y = 34;
    static final int LINE_HEIGHT = 10;
    static final int TEXT = 0xFFE8EDF3;
    static final int MUTED = 0xFF8F9AA8;

    private NetherCategoryStyle() { }

    static int rulesHeight(List<Component> rules) {
        return rulesHeight(rules, RULES_Y);
    }

    static int rulesHeight(List<Component> rules, int top) {
        Font font = Minecraft.getInstance().font;
        int lines = 0;
        for (Component rule : rules) lines += Math.max(1, font.split(rule, WIDTH - 8).size());
        return top + lines * LINE_HEIGHT + 4;
    }

    static void drawRules(GuiGraphics graphics, List<Component> rules) {
        drawRules(graphics, rules, RULES_Y);
    }

    static void drawRules(GuiGraphics graphics, List<Component> rules, int top) {
        Font font = Minecraft.getInstance().font;
        int y = top;
        for (Component rule : rules) {
            for (FormattedCharSequence line : font.split(rule, WIDTH - 8)) {
                graphics.drawString(font, line, 4, y, MUTED, false);
                y += LINE_HEIGHT;
            }
        }
    }

    static String minutes(double minutes) {
        return minutes == Math.rint(minutes) ? Long.toString(Math.round(minutes))
                : String.format(java.util.Locale.ROOT, "%.1f", minutes);
    }
}
