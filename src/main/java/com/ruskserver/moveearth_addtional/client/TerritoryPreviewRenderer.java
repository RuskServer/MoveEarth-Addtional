package com.ruskserver.moveearth_addtional.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import com.ruskserver.moveearth_addtional.network.S2C_TerritoryPreviewPacket;
import com.ruskserver.moveearth_addtional.s2.territory.TerritoryPreviewArea;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.debug.DebugRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

@EventBusSubscriber(modid = Moveearth_addtional.MODID, bus = EventBusSubscriber.Bus.GAME, value = Dist.CLIENT)
public final class TerritoryPreviewRenderer {
    private static final double LINE_WIDTH = 0.055D;

    private TerritoryPreviewRenderer() {
    }

    @SubscribeEvent
    public static void renderWorld(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        Minecraft minecraft = Minecraft.getInstance();
        S2C_TerritoryPreviewPacket packet = TerritoryPreviewClientState.preview();
        if (packet == null || minecraft.level == null || minecraft.player == null
                || !minecraft.level.dimension().location().equals(packet.dimension())) return;

        TerritoryPreviewArea area = new TerritoryPreviewArea(
                packet.centerChunkX(), packet.centerChunkZ(), packet.radius());
        double y = Math.floor(minecraft.player.getY()) + 0.035D;
        Vec3 camera = event.getCamera().getPosition();
        PoseStack poseStack = event.getPoseStack();
        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();

        for (int chunkX = area.minChunkX(); chunkX <= area.maxChunkX() + 1; chunkX++) {
            double x = chunkX << 4;
            boolean outer = chunkX == area.minChunkX() || chunkX == area.maxChunkX() + 1;
            renderBox(poseStack, buffers, camera,
                    new AABB(x - LINE_WIDTH, y, area.minBlockZ(), x + LINE_WIDTH,
                            y + (outer ? 0.18D : 0.06D), area.maxBlockZExclusive()), outer);
        }
        for (int chunkZ = area.minChunkZ(); chunkZ <= area.maxChunkZ() + 1; chunkZ++) {
            double z = chunkZ << 4;
            boolean outer = chunkZ == area.minChunkZ() || chunkZ == area.maxChunkZ() + 1;
            renderBox(poseStack, buffers, camera,
                    new AABB(area.minBlockX(), y, z - LINE_WIDTH, area.maxBlockXExclusive(),
                            y + (outer ? 0.18D : 0.06D), z + LINE_WIDTH), outer);
        }

        double centerX = (packet.centerChunkX() << 4) + 8.0D;
        double centerZ = (packet.centerChunkZ() << 4) + 8.0D;
        DebugRenderer.renderFilledBox(poseStack, buffers,
                new AABB(centerX - 0.18D, y, centerZ - 0.18D,
                        centerX + 0.18D, y + 2.2D, centerZ + 0.18D)
                        .move(-camera.x, -camera.y, -camera.z),
                1.0F, 0.71F, 0.25F, 0.72F);
        buffers.endBatch(RenderType.debugFilledBox());
    }

    private static void renderBox(PoseStack poseStack, MultiBufferSource buffers, Vec3 camera,
                                  AABB box, boolean outer) {
        DebugRenderer.renderFilledBox(poseStack, buffers,
                box.move(-camera.x, -camera.y, -camera.z),
                outer ? 0.30F : 0.36F, outer ? 0.94F : 0.80F,
                outer ? 0.63F : 1.0F, outer ? 0.62F : 0.30F);
    }

    @SubscribeEvent
    public static void renderHud(RenderGuiEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        S2C_TerritoryPreviewPacket packet = TerritoryPreviewClientState.preview();
        if (packet == null || minecraft.options.hideGui || minecraft.player == null
                || minecraft.level == null || minecraft.screen != null
                || !minecraft.level.dimension().location().equals(packet.dimension())) return;
        var graphics = event.getGuiGraphics();
        int x = 12;
        int y = 12;
        graphics.fill(x, y, x + 218, y + 48, 0xD012161D);
        graphics.fill(x, y, x + 3, y + 48, 0xFF68E09B);
        graphics.drawString(minecraft.font,
                Component.translatable("overlay.moveearth_addtional.territory_preview"),
                x + 11, y + 9, 0xFF68E09B, false);
        graphics.drawString(minecraft.font,
                Component.translatable("overlay.moveearth_addtional.territory_preview.detail",
                        packet.radius(), packet.chunkCount()), x + 11, y + 23, 0xFFE8EDF3, false);
        graphics.drawString(minecraft.font,
                Component.translatable("overlay.moveearth_addtional.territory_preview.exit"),
                x + 11, y + 35, 0xFF8F9AA8, false);
    }
}
