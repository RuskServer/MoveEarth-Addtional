package com.ruskserver.moveearth_addtional.client.compat.coldsweat;

import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import com.ruskserver.moveearth_addtional.client.compat.coldsweat.TemperatureHudPolicy.ThermalStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiEvent;

import java.util.UUID;

/** Adaptive top-left temperature panel backed by Cold Sweat's client-synced API. */
@EventBusSubscriber(modid = Moveearth_addtional.MODID, value = Dist.CLIENT)
public final class ColdSweatTemperatureHud {
    private static final long DOWNGRADE_DELAY_MS = 2_500L;
    private static final long SAMPLE_INTERVAL_MS = 250L;
    private static ThermalStatus displayed = ThermalStatus.UNAVAILABLE;
    private static ThermalStatus pending = ThermalStatus.UNAVAILABLE;
    private static long pendingSince;
    private static float opacity;
    private static boolean apiFailed;
    private static UUID sampledPlayer;
    private static ColdSweatClientBridge.TemperatureSample cachedSample;
    private static long nextSampleAt;

    private ColdSweatTemperatureHud() { }

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (apiFailed || !ModList.get().isLoaded("cold_sweat") || minecraft.player == null
                || minecraft.options.hideGui || minecraft.screen != null
                || minecraft.player.isCreative() || minecraft.player.isSpectator()
                || minecraft.getDebugOverlay().showDebugScreen()) return;

        long now = System.currentTimeMillis();
        UUID playerId = minecraft.player.getUUID();
        if (!playerId.equals(sampledPlayer)) {
            sampledPlayer = playerId;
            cachedSample = null;
            nextSampleAt = 0L;
            displayed = ThermalStatus.UNAVAILABLE;
            pending = ThermalStatus.UNAVAILABLE;
            opacity = 0.0F;
        }
        if (cachedSample == null || now >= nextSampleAt) {
            try {
                cachedSample = ColdSweatClientBridge.sample(minecraft.player);
                nextSampleAt = now + SAMPLE_INTERVAL_MS;
            } catch (LinkageError | RuntimeException exception) {
                apiFailed = true;
                Moveearth_addtional.LOGGER.warn("[MoveEarth] Cold Sweat HUD disabled: incompatible client API ({})",
                        exception.getClass().getSimpleName());
                return;
            }
        }

        ThermalStatus measured = TemperatureHudPolicy.classify(cachedSample.world(), cachedSample.body(),
                cachedSample.freezingPoint(), cachedSample.burningPoint());
        if (measured == ThermalStatus.UNAVAILABLE) return;
        updateDisplayed(measured, now);
        opacity += (displayed.opacity() - opacity) * 0.16F;
        render(event.getGuiGraphics(), minecraft.font, displayed, cachedSample.celsius(), opacity, now);
    }

    private static void updateDisplayed(ThermalStatus measured, long now) {
        if (displayed == ThermalStatus.UNAVAILABLE) {
            displayed = measured;
            pending = measured;
            pendingSince = now;
            return;
        }
        if (measured == displayed) {
            pending = measured;
            pendingSince = now;
            return;
        }
        if (measured != pending) {
            pending = measured;
            pendingSince = now;
        }
        boolean moreDangerous = measured.danger() > displayed.danger();
        if (moreDangerous || now - pendingSince >= DOWNGRADE_DELAY_MS) displayed = measured;
    }

    private static void render(GuiGraphics graphics, Font font, ThermalStatus status,
                               double celsius, float alpha, long now) {
        Component title = Component.translatable(statusKey(status));
        Component temperature = Component.translatable("hud.moveearth_addtional.temperature.current",
                Math.round(celsius));
        Component advice = status.direction() == TemperatureHudPolicy.Direction.COLD
                ? Component.translatable("hud.moveearth_addtional.temperature.advice.cold")
                : Component.translatable("hud.moveearth_addtional.temperature.advice.hot");

        int width = status.expanded()
                ? Math.max(184, Math.max(font.width(title), Math.max(font.width(advice), font.width(temperature))) + 20)
                : Math.max(150, font.width(Component.empty().append(title).append("  ").append(temperature)) + 20);
        int height = status.expanded() ? 48 : 23;
        int x = 10;
        int y = 10;
        int accent = accent(status, now);
        graphics.fill(x, y, x + width, y + height, color(0x10151D, Math.round(190 * alpha)));
        graphics.fill(x, y, x + 3, y + height, color(accent, Math.round(255 * alpha)));
        drawBorder(graphics, x, y, width, height, color(accent, Math.round(110 * alpha)));

        if (!status.expanded()) {
            Component line = Component.empty().append(title).append("  ").append(temperature);
            graphics.drawString(font, line, x + 10, y + 8, color(textColor(status), Math.round(255 * alpha)), false);
            return;
        }
        graphics.drawString(font, title, x + 10, y + 6,
                color(textColor(status), Math.round(255 * alpha)), true);
        graphics.drawString(font, advice, x + 10, y + 20,
                color(0xD7E2EB, Math.round(235 * alpha)), false);
        graphics.drawString(font, temperature, x + 10, y + 34,
                color(0xAEBBC7, Math.round(225 * alpha)), false);
    }

    private static String statusKey(ThermalStatus status) {
        return switch (status) {
            case COMFORTABLE -> "hud.moveearth_addtional.temperature.comfortable";
            case COLD -> "hud.moveearth_addtional.temperature.cold";
            case SEVERE_COLD -> "hud.moveearth_addtional.temperature.severe_cold";
            case EXTREME_COLD -> "hud.moveearth_addtional.temperature.extreme_cold";
            case WARM -> "hud.moveearth_addtional.temperature.warm";
            case HOT -> "hud.moveearth_addtional.temperature.hot";
            case EXTREME_HEAT -> "hud.moveearth_addtional.temperature.extreme_heat";
            case UNAVAILABLE -> "hud.moveearth_addtional.temperature.comfortable";
        };
    }

    private static int accent(ThermalStatus status, long now) {
        if (status == ThermalStatus.EXTREME_COLD || status == ThermalStatus.EXTREME_HEAT) {
            double pulse = (Math.sin(now / 230.0D) + 1.0D) * 0.5D;
            return blend(status.direction() == TemperatureHudPolicy.Direction.COLD ? 0x55BFFF : 0xFF654F,
                    0xFFFFFF, pulse * 0.28D);
        }
        return status.direction() == TemperatureHudPolicy.Direction.COLD ? 0x5DCBFF
                : status.direction() == TemperatureHudPolicy.Direction.HOT ? 0xFF9B54 : 0x76D7A0;
    }

    private static int textColor(ThermalStatus status) {
        return status.direction() == TemperatureHudPolicy.Direction.COLD ? 0xA8E1FF
                : status.direction() == TemperatureHudPolicy.Direction.HOT ? 0xFFC095 : 0xB9EBCF;
    }

    private static int color(int rgb, int alpha) {
        return (Math.max(0, Math.min(255, alpha)) << 24) | rgb;
    }

    private static int blend(int from, int to, double amount) {
        double t = Math.max(0.0D, Math.min(1.0D, amount));
        int r = (int) (((from >> 16) & 255) * (1.0D - t) + ((to >> 16) & 255) * t);
        int g = (int) (((from >> 8) & 255) * (1.0D - t) + ((to >> 8) & 255) * t);
        int b = (int) ((from & 255) * (1.0D - t) + (to & 255) * t);
        return (r << 16) | (g << 8) | b;
    }

    private static void drawBorder(GuiGraphics graphics, int x, int y, int width, int height, int color) {
        graphics.fill(x, y, x + width, y + 1, color);
        graphics.fill(x, y + height - 1, x + width, y + height, color);
        graphics.fill(x, y + 1, x + 1, y + height - 1, color);
        graphics.fill(x + width - 1, y + 1, x + width, y + height - 1, color);
    }
}
