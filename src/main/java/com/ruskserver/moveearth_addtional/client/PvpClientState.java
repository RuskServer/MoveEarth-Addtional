package com.ruskserver.moveearth_addtional.client;

import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import com.ruskserver.moveearth_addtional.network.S2C_PvpHudPacket;
import com.ruskserver.moveearth_addtional.network.S2C_PvpKillcamPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.RenderNameTagEvent;
import net.neoforged.neoforge.common.util.TriState;
import net.minecraft.world.entity.player.Player;
import net.minecraft.sounds.SoundEvents;

import java.util.*;

@EventBusSubscriber(modid = Moveearth_addtional.MODID, bus = EventBusSubscriber.Bus.GAME, value = Dist.CLIENT)
public final class PvpClientState {
    private static S2C_PvpHudPacket hud = S2C_PvpHudPacket.inactive();
    private static final Set<UUID> allies = new HashSet<>();
    private static S2C_PvpKillcamPacket killcam;
    private static int killcamTicks;
    private static int killcamTotalTicks;
    private static UUID highlightedKiller;
    private static final Map<UUID, Boolean> originalGlow = new HashMap<>();

    private PvpClientState() {}

    public static void updateHud(S2C_PvpHudPacket packet) { hud = packet; }
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

    public static void reset() {
        for (UUID id : new HashSet<>(originalGlow.keySet())) restoreGlow(id);
        allies.clear();
        highlightedKiller = null;
        hud = S2C_PvpHudPacket.inactive();
        killcam = null;
        killcamTicks = 0;
        killcamTotalTicks = 0;
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
        if (killcam != null && killcamTicks-- > 0 && mc.player != null) {
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
        if (killcam != null) {
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
    }

    private static Entity findEntity(Minecraft mc, UUID id) {
        if (mc.level == null) return null;
        for (Entity entity : mc.level.entitiesForRendering()) if (entity.getUUID().equals(id)) return entity;
        return null;
    }
    private static void applyGlow(UUID id) {
        Minecraft mc = Minecraft.getInstance();
        Entity entity = findEntity(mc, id);
        if (entity == null) return;
        originalGlow.putIfAbsent(id, entity.isCurrentlyGlowing());
        entity.setGlowingTag(true);
    }

    private static void restoreGlow(UUID id) {
        Boolean wasGlowing = originalGlow.remove(id);
        Entity entity = findEntity(Minecraft.getInstance(), id);
        if (entity != null && wasGlowing != null) entity.setGlowingTag(wasGlowing);
    }

    private static void clearKillerHighlight() {
        if (highlightedKiller == null) return;
        UUID oldKiller = highlightedKiller;
        highlightedKiller = null;
        if (!allies.contains(oldKiller)) restoreGlow(oldKiller);
    }
}
