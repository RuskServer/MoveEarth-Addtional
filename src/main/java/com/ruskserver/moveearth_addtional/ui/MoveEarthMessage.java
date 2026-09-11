package com.ruskserver.moveearth_addtional.ui;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;

/** Shared rich prefix and severity palette for player-facing MoveEarth messages. */
public final class MoveEarthMessage {
    private static final String BRAND = "[MoveEarth]";
    private static final String CHEVRONS = ">>>";
    private static final int BRAND_START = 0x278B57;
    private static final int BRAND_END = 0x78F0AA;
    private static final int CHEVRON_START = 0x626B75;
    private static final int CHEVRON_END = 0xAAB2BC;

    private MoveEarthMessage() {
    }

    public static MutableComponent info(String message) {
        return info(Component.literal(message));
    }

    public static MutableComponent info(Component message) {
        return compose(Severity.INFO, message);
    }

    public static MutableComponent success(String message) {
        return success(Component.literal(message));
    }

    public static MutableComponent success(Component message) {
        return compose(Severity.SUCCESS, message);
    }

    public static MutableComponent warning(String message) {
        return warning(Component.literal(message));
    }

    public static MutableComponent warning(Component message) {
        return compose(Severity.WARNING, message);
    }

    public static MutableComponent error(String message) {
        return error(Component.literal(message));
    }

    public static MutableComponent error(Component message) {
        return compose(Severity.ERROR, message);
    }

    public static MutableComponent compose(Severity severity, Component message) {
        MutableComponent result = Component.empty();
        appendGradient(result, BRAND, BRAND_START, BRAND_END, true);
        result.append(Component.literal(" "));
        appendGradient(result, CHEVRONS, CHEVRON_START, CHEVRON_END, true);
        result.append(Component.literal(" " + severity.symbol() + " ")
                .withStyle(color(severity.color(), true)));
        result.append(message.copy().withStyle(style ->
                style.getColor() == null ? style.withColor(severity.color()) : style));
        return result;
    }

    private static void appendGradient(MutableComponent target, String text,
                                       int startColor, int endColor, boolean bold) {
        int denominator = Math.max(1, text.length() - 1);
        for (int index = 0; index < text.length(); index++) {
            float progress = index / (float) denominator;
            int rgb = MoveEarthMessageStyle.interpolate(startColor, endColor, progress);
            target.append(Component.literal(String.valueOf(text.charAt(index)))
                    .withStyle(color(rgb, bold)));
        }
    }

    private static Style color(int rgb, boolean bold) {
        return Style.EMPTY.withColor(TextColor.fromRgb(rgb)).withBold(bold);
    }

    public enum Severity {
        INFO("•", 0xDCE5EA),
        SUCCESS("✓", 0x68E09B),
        WARNING("!", 0xFFB454),
        ERROR("×", 0xFF6577);

        private final String symbol;
        private final int color;

        Severity(String symbol, int color) {
            this.symbol = symbol;
            this.color = color;
        }

        public String symbol() {
            return symbol;
        }

        public int color() {
            return color;
        }
    }
}
