package com.ruskserver.moveearth_addtional.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import com.ruskserver.moveearth_addtional.network.S2C_PrisonerSnapshotPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

@EventBusSubscriber(modid = Moveearth_addtional.MODID, bus = EventBusSubscriber.Bus.GAME, value = Dist.CLIENT)
public final class PrisonerClientState {
    private static S2C_PrisonerSnapshotPacket snapshot = inactive();

    private PrisonerClientState() { }

    public static void update(S2C_PrisonerSnapshotPacket value) { snapshot = value; }
    public static S2C_PrisonerSnapshotPacket snapshot() { return snapshot; }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) { snapshot = inactive(); }

    @SubscribeEvent
    public static void renderHud(RenderGuiEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (snapshot.state() == 0 || minecraft.player == null || minecraft.options.hideGui
                || minecraft.screen != null) return;
        var graphics = event.getGuiGraphics();
        Component title = Component.translatable("hud.moveearth_addtional.prisoner.state." + snapshot.state());
        Component detail = snapshot.state() == 1
                ? Component.translatable("hud.moveearth_addtional.prisoner.escort", snapshot.counterpart())
                : Component.translatable("hud.moveearth_addtional.prisoner.held", snapshot.holdingNation());
        Component time = Component.translatable("hud.moveearth_addtional.prisoner.remaining",
                formatTicks(snapshot.remainingTicks()));
        Component key = Component.translatable("hud.moveearth_addtional.prisoner.details",
                S2ClientKeys.OPEN_PRISONERS.getTranslatedKeyMessage());
        boolean boundaryWarning = snapshot.state() == 3 && snapshot.jailPos() != null
                && snapshot.jailDimension() != null && minecraft.level != null
                && minecraft.level.dimension().location().equals(snapshot.jailDimension())
                && minecraft.player.distanceToSqr(snapshot.jailPos().getCenter()) >= 64.0D;
        int width = Math.max(224, Math.max(minecraft.font.width(detail), minecraft.font.width(time)) + 24);
        int x = graphics.guiWidth() - width - 12;
        int y = Math.max(52, graphics.guiHeight() / 2 - 45);
        int height = boundaryWarning ? 70 : 57;
        graphics.fill(x, y, x + width, y + height, 0xDD12161D);
        graphics.fill(x, y, x + 3, y + height, snapshot.state() == 1 ? 0xFFFFB454 : 0xFFFF6577);
        graphics.drawString(minecraft.font, title, x + 11, y + 7,
                snapshot.state() == 1 ? 0xFFFFB454 : 0xFFFF6577, false);
        graphics.drawString(minecraft.font, detail, x + 11, y + 21, 0xFFE8EDF3, false);
        graphics.drawString(minecraft.font, time, x + 11, y + 34, 0xFFE8EDF3, false);
        graphics.drawString(minecraft.font, key, x + 11, y + 46, 0xFF8F9AA8, false);
        if (boundaryWarning) graphics.drawString(minecraft.font,
                Component.translatable("hud.moveearth_addtional.prisoner.boundary_warning"),
                x + 11, y + 58, 0xFFFF6577, false);
    }

    @SubscribeEvent
    public static void renderBoundary(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || snapshot.state() != 3
                || snapshot.jailPos() == null || snapshot.jailDimension() == null) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null
                || !minecraft.level.dimension().location().equals(snapshot.jailDimension())) return;
        Vec3 center = snapshot.jailPos().getCenter();
        double distance = Math.sqrt(minecraft.player.distanceToSqr(center));
        float red = distance >= 8.0D ? 1.0F : 0.28F;
        float green = distance >= 8.0D ? 0.32F : 1.0F;
        Vec3 camera = event.getCamera().getPosition();
        PoseStack poseStack = event.getPoseStack();
        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        VertexConsumer lines = buffers.getBuffer(RenderType.lines());
        double y = minecraft.player.getY() + 0.04D;
        AABB boundary = new AABB(center.x - 10.0D, y, center.z - 10.0D,
                center.x + 10.0D, y + 0.12D, center.z + 10.0D).move(-camera.x, -camera.y, -camera.z);
        LevelRenderer.renderLineBox(poseStack, lines, boundary, red, green, 0.42F, 0.95F);
        buffers.endBatch();
    }

    private static String formatTicks(long ticks) {
        long seconds = Math.max(0L, (ticks + 19L) / 20L);
        return "%d:%02d:%02d".formatted(seconds / 3600L, seconds / 60L % 60L, seconds % 60L);
    }

    private static S2C_PrisonerSnapshotPacket inactive() {
        return new S2C_PrisonerSnapshotPacket(false, 0, "", "", 0L,
                null, null, null, null, java.util.List.of(), java.util.List.of());
    }
}
