package com.ruskserver.moveearth_addtional.client;

import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import com.ruskserver.moveearth_addtional.network.S2C_KillcamReplayPacket;
import com.ruskserver.moveearth_addtional.network.S2C_PvpHudPacket;
import com.ruskserver.moveearth_addtional.network.S2C_PvpKillcamPacket;
import com.ruskserver.moveearth_addtional.network.S2C_PvpResultPacket;
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
    private static List<com.ruskserver.moveearth_addtional.pvp.PvpLoadoutDefinition> loadouts = com.ruskserver.moveearth_addtional.pvp.PvpLoadoutPreset.createDefaultDefinitions();
    private static S2C_PvpKillcamPacket killcam;
    private static S2C_KillcamReplayPacket activeReplay;
    private static int killcamTicks;
    private static int killcamTotalTicks;
    private static S2C_PvpResultPacket matchResult;
    private static int resultTicks;
    private static int resultTotalTicks;
    private static UUID highlightedKiller;
    private static final Map<UUID, Boolean> originalGlow = new HashMap<>();

    private PvpClientState() {}

    public static void updateHud(S2C_PvpHudPacket packet) { hud = packet; }
    public static void updateLoadouts(List<com.ruskserver.moveearth_addtional.pvp.PvpLoadoutDefinition> list) {
        loadouts = new ArrayList<>(list);
    }
    public static List<com.ruskserver.moveearth_addtional.pvp.PvpLoadoutDefinition> getLoadouts() {
        return Collections.unmodifiableList(loadouts);
    }
    public static Optional<com.ruskserver.moveearth_addtional.pvp.PvpLoadoutDefinition> getLoadoutById(String id) {
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
        highlightedKiller = packet.killerId().getMostSignificantBits() == 0L && packet.killerId().getLeastSignificantBits() == 0L
                ? null : packet.killerId();
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) mc.player.playSound(SoundEvents.PLAYER_DEATH, 1.0F, 1.0F);
    }

    public static void startReplay(S2C_KillcamReplayPacket packet) {
        activeReplay = packet;
        PvpReplayManager.INSTANCE.startReplay(packet);
        highlightedKiller = packet.killerId();
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) mc.player.playSound(SoundEvents.PLAYER_DEATH, 1.0F, 1.0F);
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
        activeReplay = null;
        PvpReplayManager.INSTANCE.stopReplay();
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
        hud = S2C_PvpHudPacket.inactive();
        killcam = null;
        activeReplay = null;
        PvpReplayManager.INSTANCE.stopReplay();
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

        if (PvpReplayManager.INSTANCE.isActive()) {
            PvpReplayManager.INSTANCE.tick();
        } else if (killcam != null && killcamTicks-- > 0 && mc.player != null) {
            double dx = killcam.x() - mc.player.getX();
            double dy = killcam.y() - mc.player.getEyeY();
            double dz = killcam.z() - mc.player.getZ();
            double horizontal = Math.sqrt(dx * dx + dz * dz);
            float targetYaw = (float)(Mth.atan2(dz, dx) * 180.0 / Math.PI) - 90.0F;
            float targetPitch = (float)-(Mth.atan2(dy, horizontal) * 180.0 / Math.PI);
            mc.player.setYRot(Mth.rotLerp(0.18F, mc.player.getYRot(), targetYaw));
            mc.player.setXRot(Mth.lerp(0.18F, mc.player.getXRot(), targetPitch));
        } else if (killcamTicks <= 0) {
            clearKillerHighlight();
            killcam = null;
            activeReplay = null;
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

        boolean replaying = PvpReplayManager.INSTANCE.isActive();
        if (replaying && activeReplay != null) {
            renderKillcamReplay(g, mc, activeReplay);
        } else if (killcam != null) {
            renderSimpleKillcam(g, mc);
        }

        if (matchResult != null) renderMatchResult(g, mc);
    }

    private static void renderKillcamReplay(GuiGraphics g, Minecraft mc, S2C_KillcamReplayPacket replay) {
        int w = g.guiWidth();
        int h = g.guiHeight();
        int center = w / 2;

        // 上部シネマティックレターボックス
        g.fill(0, 0, w, 28, 0xCC090A0D);
        boolean blink = (mc.gui.getGuiTicks() / 10) % 2 == 0;
        String recSymbol = blink ? "§c● " : "§4○ ";
        g.drawString(mc.font, recSymbol + "§lKILLCAM REPLAY", 16, 10, 0xFFFF6577, false);
        g.drawString(mc.font, "§7ELIMINATED BY " + replay.killerName(), center - mc.font.width("ELIMINATED BY " + replay.killerName()) / 2, 10, 0xFFE0E0E0, false);

        // 下部シネマティックレターボックス
        g.fill(0, h - 30, w, h, 0xCC090A0D);
        g.drawCenteredString(mc.font, "§7[SPACE] スキップ", center, h - 18, 0xFFA0AAB8);

        // 詳細キルカード
        int cardW = 340;
        int cardH = 58;
        int cardX = (w - cardW) / 2;
        int cardY = h - 94;

        g.fill(cardX, cardY, cardX + cardW, cardY + cardH, 0xEE12161D);
        drawBorder(g, cardX, cardY, cardW, cardH, 0xFFFF5555);

        // キラー名 & キルストリーク
        String killerTitle = replay.killerName();
        if (replay.killerStreak() > 1) {
            killerTitle += "  §6🔥 " + replay.killerStreak() + " STREAK";
        }
        g.drawString(mc.font, killerTitle, cardX + 12, cardY + 8, 0xFFFF6464, false);

        // 体力バー
        int barX = cardX + 12;
        int barY = cardY + 22;
        int barW = 140;
        g.fill(barX, barY, barX + barW, barY + 6, 0xFF0C1015);
        float hpRatio = Mth.clamp(replay.killerHealth() / Math.max(1.0F, replay.killerMaxHealth()), 0.0F, 1.0F);
        int hpColor = hpRatio > 0.5F ? 0xFF55E6C1 : hpRatio > 0.25F ? 0xFFFFB454 : 0xFFFF5364;
        g.fill(barX, barY, barX + (int) (barW * hpRatio), barY + 6, hpColor);
        String hpText = String.format(Locale.ROOT, "%.1f / %.0f HP", replay.killerHealth(), replay.killerMaxHealth());
        g.drawString(mc.font, hpText, barX, barY + 9, 0xFFA0AAB8, false);

        // 武器名 & 距離
        String weaponInfo = "§e" + replay.weaponName() + " §7(" + String.format(Locale.ROOT, "%.1fm", replay.distance()) + ")";
        g.drawString(mc.font, weaponInfo, cardX + cardW - 12 - mc.font.width(weaponInfo), cardY + 8, 0xFFFFFFFF, false);

        if (replay.isHeadshot()) {
            String hs = "§6💥 HEADSHOT";
            g.drawString(mc.font, hs, cardX + cardW - 12 - mc.font.width(hs), cardY + 22, 0xFFFFD700, false);
        }

        // リプレイ進行度バー（カード最下部）
        float progress = PvpReplayManager.INSTANCE.progress();
        int progBarY = cardY + cardH - 3;
        g.fill(cardX, progBarY, cardX + cardW, progBarY + 3, 0xFF0C1015);
        g.fill(cardX, progBarY, cardX + (int) (cardW * progress), progBarY + 3, 0xFF5DCBFF);
    }

    private static void renderSimpleKillcam(GuiGraphics g, Minecraft mc) {
        int center = g.guiWidth() / 2;
        int elapsed = killcamTotalTicks - Math.max(0, killcamTicks);
        if (elapsed < 20) {
            int alpha = Math.max(0, 150 - elapsed * 7);
            g.fill(0, 0, g.guiWidth(), g.guiHeight(), (alpha << 24) | 0x550000);
            g.drawCenteredString(mc.font, "死亡", center, g.guiHeight() / 2 - 18, 0xFFFF5555);
        }
        int y = g.guiHeight() - 68;
        g.fill(center - 145, y, center + 145, y + 42, 0xCC090A0D);
        g.drawCenteredString(mc.font, "KILLED BY", center, y + 7, 0xFF999999);
        g.drawCenteredString(mc.font, killcam.killer(), center, y + 22, 0xFFFF6464);
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
