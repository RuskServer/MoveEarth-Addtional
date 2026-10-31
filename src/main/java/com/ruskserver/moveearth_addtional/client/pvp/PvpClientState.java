package com.ruskserver.moveearth_addtional.client.pvp;

import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import com.ruskserver.moveearth_addtional.network.s2c.pvp.S2C_PvpHudPacket;
import com.ruskserver.moveearth_addtional.network.s2c.pvp.S2C_PvpKillcamPacket;
import com.ruskserver.moveearth_addtional.network.s2c.pvp.S2C_PvpResultPacket;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.RenderNameTagEvent;
import net.neoforged.neoforge.common.util.TriState;

import java.util.*;

@EventBusSubscriber(modid = Moveearth_addtional.MODID, bus = EventBusSubscriber.Bus.GAME, value = Dist.CLIENT)
public final class PvpClientState {
    private static S2C_PvpHudPacket hud = S2C_PvpHudPacket.inactive();
    private static final Set<UUID> allies = new HashSet<>();
    private static List<com.ruskserver.moveearth_addtional.pvp.loadout.PvpLoadoutDefinition> loadouts = com.ruskserver.moveearth_addtional.pvp.loadout.PvpLoadoutPreset.createDefaultDefinitions();
    private static S2C_PvpKillcamPacket killcam;
    private static int killcamTicks;
    private static int killcamTotalTicks;
    private static S2C_PvpResultPacket matchResult;
    private static int resultTicks;
    private static int resultTotalTicks;
    private static UUID highlightedKiller;
    private static final Map<UUID, Boolean> originalGlow = new HashMap<>();

    private PvpClientState() {}

    public static boolean isMatchActive() { return hud.active(); }
    public static S2C_PvpHudPacket hud() { return hud; }
    public static boolean isAlly(UUID id) { return allies.contains(id); }
    /** Whether a PvP event is being hosted, for the hub's PvP tab. */
    private static boolean hosting;
    public static boolean isHosting() { return hosting; }
    public static void setHosting(boolean value) { hosting = value; }

    public static void updateHud(S2C_PvpHudPacket packet) { hud = packet; }
    public static void updateLoadouts(List<com.ruskserver.moveearth_addtional.pvp.loadout.PvpLoadoutDefinition> list) {
        loadouts = new ArrayList<>(list);
    }
    public static List<com.ruskserver.moveearth_addtional.pvp.loadout.PvpLoadoutDefinition> getLoadouts() {
        return Collections.unmodifiableList(loadouts);
    }
    public static Optional<com.ruskserver.moveearth_addtional.pvp.loadout.PvpLoadoutDefinition> getLoadoutById(String id) {
        return loadouts.stream().filter(def -> def.id().equals(id)).findFirst();
    }
    public static void updateAllies(List<UUID> ids) {
        Set<UUID> next = new HashSet<>(ids);
        for (UUID id : new HashSet<>(allies)) {
            if (!next.contains(id) && !id.equals(highlightedKiller)) restoreGlow(id);
        }
        allies.clear();
        allies.addAll(next);
    }

    public static void startKillcam(S2C_PvpKillcamPacket packet) {
        clearKillerHighlight();
        killcam = packet;
        killcamTicks = packet.ticks();
        killcamTotalTicks = packet.ticks();
        highlightedKiller = packet.hasKiller() ? packet.killerId() : null;
        PvpDeathCam.start();
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) mc.player.playSound(SoundEvents.PLAYER_DEATH, 1.0F, 1.0F);
    }

    /** The death camera in progress, or null. */
    public static S2C_PvpKillcamPacket killcam() {
        return killcamTicks > 0 ? killcam : null;
    }

    /** How far through the death camera, from 0 to 1. */
    public static float killcamProgress(float partialTick) {
        if (killcamTotalTicks <= 0) return 1.0F;
        return Mth.clamp((killcamTotalTicks - killcamTicks + partialTick) / killcamTotalTicks, 0.0F, 1.0F);
    }

    public static void showResult(S2C_PvpResultPacket packet) {
        if (packet.ticks() <= 0) {
            matchResult = null;
            resultTicks = 0;
            resultTotalTicks = 0;
            return;
        }
        clearKillerHighlight();
        killcam = null;
        killcamTicks = 0;
        killcamTotalTicks = 0;
        matchResult = packet;
        resultTicks = packet.ticks();
        resultTotalTicks = packet.ticks();
    }

    public static void reset() {
        for (UUID id : new HashSet<>(originalGlow.keySet())) restoreGlow(id);
        allies.clear();
        highlightedKiller = null;
        hosting = false;
        hud = S2C_PvpHudPacket.inactive();
        killcam = null;
        killcamTicks = 0;
        killcamTotalTicks = 0;
        matchResult = null;
        resultTicks = 0;
        resultTotalTicks = 0;
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        reset();
    }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null) {
            for (UUID id : allies) {
                applyGlow(id);
            }
            if (highlightedKiller != null) {
                applyGlow(highlightedKiller);
            }
        }

        if (killcamTicks <= 0) {
            clearKillerHighlight();
            killcam = null;
        } else if (killcam != null) {
            killcamTicks--;
        }

        if (matchResult != null && --resultTicks <= 0) {
            matchResult = null;
            resultTicks = 0;
            resultTotalTicks = 0;
        }
    }

    @SubscribeEvent
    public static void renderTeamNameTags(RenderNameTagEvent event) {
        if (!hud.active() || !(event.getEntity() instanceof Player player)) return;
        if (!allies.contains(player.getUUID())) {
            event.setCanRender(TriState.FALSE);
            return;
        }

        event.setCanRender(TriState.TRUE);
        event.setContent(Component.literal("◆ ALLY ").withStyle(ChatFormatting.GREEN)
                .append(event.getOriginalContent().copy().withStyle(ChatFormatting.WHITE)));
    }

    @SubscribeEvent
    public static void render(RenderGuiEvent.Post event) {
        GuiGraphics g = event.getGuiGraphics();
        Minecraft mc = Minecraft.getInstance();

        if (hud.active()) {
            int center = g.guiWidth() / 2;
            g.fill(center - 118, 7, center + 118, 42, 0xB0101218);
            g.drawString(mc.font, "RED  " + hud.red() + " / " + hud.target(), center - 108, 14, 0xFFFF5555, true);
            String blue = hud.blue() + " / " + hud.target() + "  BLUE";
            g.drawString(mc.font, blue, center + 108 - mc.font.width(blue), 14, 0xFF6699FF, true);
            int seconds = Math.max(0, hud.ticksLeft() / 20);
            String bottom = hud.hill() + "   " + String.format("%02d:%02d", seconds / 60, seconds % 60);
            g.drawCenteredString(mc.font, bottom, center, 28, 0xFFFFFFFF);
        }

        if (killcam != null && killcamTicks > 0) renderKillcam(g, mc, event.getPartialTick().getGameTimeDeltaPartialTick(false));

        if (matchResult != null) renderMatchResult(g, mc);
    }

    /**
     * The death camera's overlay: a red flash and letterbox bars, and a card naming the killer with the weapon,
     * their health when the shot landed and the distance, under a bar running out until the deploy screen.
     */
    private static void renderKillcam(GuiGraphics g, Minecraft mc, float partialTick) {
        int w = g.guiWidth();
        int h = g.guiHeight();
        int center = w / 2;
        int elapsed = killcamTotalTicks - Math.max(0, killcamTicks);
        if (elapsed < 20) {
            int alpha = Math.max(0, 150 - elapsed * 7);
            g.fill(0, 0, w, h, (alpha << 24) | 0x550000);
        }
        int bar = Math.min(28, elapsed * 3);
        g.fill(0, 0, w, bar, 0xCC090A0D);
        g.fill(0, h - bar, w, h, 0xCC090A0D);
        if (bar >= 28) {
            g.drawString(mc.font, "§c§lKILLCAM", 16, 10, 0xFFFF6577, false);
        }

        int cardW = 300;
        int cardH = killcam.hasKiller() ? 58 : 36;
        int cardX = center - cardW / 2;
        int cardY = h - 46 - cardH;
        g.fill(cardX, cardY, cardX + cardW, cardY + cardH, 0xEE12161D);
        drawBorder(g, cardX, cardY, cardW, cardH, 0xFFFF5555);
        g.drawString(mc.font, "KILLED BY", cardX + 12, cardY + 8, 0xFF999999, false);
        if (!killcam.hasKiller()) {
            g.drawString(mc.font, "戦闘エリア・落下など", cardX + 12, cardY + 20, 0xFFFF6464, false);
        } else {
            String title = killcam.killer();
            if (killcam.streak() > 1) title += "  §6" + killcam.streak() + " 連続キル";
            g.drawString(mc.font, title, cardX + 70, cardY + 8, 0xFFFF6464, false);

            int barX = cardX + 12;
            int barY = cardY + 24;
            int barW = 140;
            g.fill(barX, barY, barX + barW, barY + 6, 0xFF0C1015);
            float hp = Mth.clamp(killcam.killerHealth() / Math.max(1.0F, killcam.killerMaxHealth()), 0.0F, 1.0F);
            int hpColor = hp > 0.5F ? 0xFF55E6C1 : hp > 0.25F ? 0xFFFFB454 : 0xFFFF5364;
            g.fill(barX, barY, barX + (int) (barW * hp), barY + 6, hpColor);
            g.drawString(mc.font, String.format(Locale.ROOT, "相手の残り %.1f / %.0f HP", killcam.killerHealth(),
                    killcam.killerMaxHealth()), barX, barY + 10, 0xFFA0AAB8, false);

            String weapon = (killcam.weapon().isEmpty() ? "" : "§e" + killcam.weapon() + " ")
                    + String.format(Locale.ROOT, "§7%.1fm", killcam.distance());
            g.drawString(mc.font, weapon, cardX + cardW - 12 - mc.font.width(weapon), cardY + 24, 0xFFFFFFFF, false);
        }
        float progress = killcamProgress(partialTick);
        g.fill(cardX, cardY + cardH - 3, cardX + cardW, cardY + cardH, 0xFF0C1015);
        g.fill(cardX, cardY + cardH - 3, cardX + (int) (cardW * progress), cardY + cardH, 0xFF5DCBFF);
    }

    private static void renderMatchResult(GuiGraphics graphics, Minecraft minecraft) {
        int elapsed = resultTotalTicks - Math.max(0, resultTicks);
        float fadeIn = Mth.clamp(elapsed / 8.0F, 0.0F, 1.0F);
        float fadeOut = Mth.clamp(resultTicks / 15.0F, 0.0F, 1.0F);
        float visibility = Math.min(fadeIn, fadeOut);
        if (visibility <= 0.0F) return;

        int accent = switch (matchResult.outcome()) {
            case S2C_PvpResultPacket.WIN -> 0xFF55E6C1;
            case S2C_PvpResultPacket.LOSS -> 0xFFFF5364;
            default -> 0xFFFFC857;
        };
        int tint = switch (matchResult.outcome()) {
            case S2C_PvpResultPacket.WIN -> 0x00123538;
            case S2C_PvpResultPacket.LOSS -> 0x00380C14;
            default -> 0x00312712;
        };
        int width = graphics.guiWidth();
        int height = graphics.guiHeight();
        int centerX = width / 2;
        int centerY = height / 2;
        int backgroundAlpha = (int) (185.0F * visibility);
        graphics.fill(0, 0, width, height, (backgroundAlpha << 24) | tint);

        int panelWidth = Math.min(360, width - 40);
        int panelHeight = 110;
        int left = centerX - panelWidth / 2;
        int top = centerY - panelHeight / 2;
        int panelAlpha = (int) (230.0F * visibility);
        graphics.fill(left, top, left + panelWidth, top + panelHeight, (panelAlpha << 24) | 0x0D1117);

        int borderColor = ((int) (255.0F * visibility) << 24) | (accent & 0x00FFFFFF);
        drawBorder(graphics, left, top, panelWidth, panelHeight, borderColor);

        String mainTitle = switch (matchResult.outcome()) {
            case S2C_PvpResultPacket.WIN -> "VICTORY";
            case S2C_PvpResultPacket.LOSS -> "DEFEAT";
            default -> "DRAW";
        };
        int titleColor = ((int) (255.0F * visibility) << 24) | (accent & 0x00FFFFFF);
        graphics.drawCenteredString(minecraft.font, "§l" + mainTitle, centerX, top + 18, titleColor);

        String scoreText = matchResult.redScore() + "  -  " + matchResult.blueScore();
        int scoreColor = ((int) (240.0F * visibility) << 24) | 0x00E6EDF3;
        graphics.drawCenteredString(minecraft.font, scoreText, centerX, top + 42, scoreColor);

        int subtitleColor = ((int) (180.0F * visibility) << 24) | 0x008B949E;
        graphics.drawCenteredString(minecraft.font, "RED (左) vs BLUE (右)", centerX, top + 64, subtitleColor);
    }

    private static void applyGlow(UUID id) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        Entity e = mc.level.getPlayerByUUID(id);
        if (e != null && !e.hasGlowingTag()) {
            originalGlow.putIfAbsent(id, e.hasGlowingTag());
            e.setGlowingTag(true);
        }
    }

    private static void restoreGlow(UUID id) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            originalGlow.remove(id);
            return;
        }
        Entity e = mc.level.getPlayerByUUID(id);
        if (e != null) {
            Boolean original = originalGlow.remove(id);
            e.setGlowingTag(original != null && original);
        } else {
            originalGlow.remove(id);
        }
    }

    private static void clearKillerHighlight() {
        if (highlightedKiller != null) {
            if (!allies.contains(highlightedKiller)) restoreGlow(highlightedKiller);
            highlightedKiller = null;
        }
    }

    private static void drawBorder(GuiGraphics graphics, int x, int y, int width, int height, int color) {
        graphics.fill(x, y, x + width, y + 1, color);
        graphics.fill(x, y + height - 1, x + width, y + height, color);
        graphics.fill(x, y + 1, x + 1, y + height - 1, color);
        graphics.fill(x + width - 1, y + 1, x + width, y + height - 1, color);
    }
}
