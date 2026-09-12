package com.ruskserver.moveearth_addtional.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import com.ruskserver.moveearth_addtional.network.S2C_TerritoryPreviewPacket;
import com.ruskserver.moveearth_addtional.network.S2C_TerritoryClosurePacket;
import com.ruskserver.moveearth_addtional.s2.territory.TerritoryPreviewArea;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.debug.DebugRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.core.BlockPos;
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
        S2C_TerritoryClosurePacket closure = TerritoryPreviewClientState.closure();
        if (minecraft.level == null || minecraft.player == null) return;
        boolean showPreview = packet != null
                && minecraft.level.dimension().location().equals(packet.dimension());
        boolean showClosure = closure != null
                && minecraft.level.dimension().location().equals(closure.dimension());
        if (!showPreview && !showClosure) return;

        Vec3 camera = event.getCamera().getPosition();
        PoseStack poseStack = event.getPoseStack();
        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();

        if (showPreview) renderPreview(packet, minecraft, poseStack, buffers, camera);
        VaultClientState.VaultMarker vault = VaultClientState.marker();
        if (showPreview && vault != null && vault.dimension().equals(packet.dimension())) {
            renderVault(vault, minecraft, poseStack, buffers, camera);
        }
        if (showClosure) renderClosure(closure, poseStack, buffers, camera);
        buffers.endBatch(RenderType.debugFilledBox());
        buffers.endBatch();
    }

    private static void renderVault(VaultClientState.VaultMarker vault, Minecraft minecraft,
                                    PoseStack poseStack, MultiBufferSource.BufferSource buffers, Vec3 camera) {
        double y = Math.floor(minecraft.player.getY()) + 0.045D;
        double minX = vault.chunkX() << 4;
        double minZ = vault.chunkZ() << 4;
        VertexConsumer outline = buffers.getBuffer(RenderType.lines());
        AABB chunk = new AABB(minX, y, minZ, minX + 16.0D, y + 0.16D, minZ + 16.0D)
                .move(-camera.x, -camera.y, -camera.z);
        LevelRenderer.renderLineBox(poseStack, outline, chunk, 0.20F, 1.0F, 0.54F, 1.0F);
        double centerX = minX + 8.0D;
        double centerZ = minZ + 8.0D;
        DebugRenderer.renderFilledBox(poseStack, buffers,
                new AABB(centerX - 0.14D, y, centerZ - 0.14D,
                        centerX + 0.14D, y + 3.6D, centerZ + 0.14D)
                        .move(-camera.x, -camera.y, -camera.z),
                0.20F, 1.0F, 0.54F, 0.78F);
        DebugRenderer.renderFloatingText(poseStack, buffers,
                Component.translatable("overlay.moveearth_addtional.vault").getString(),
                centerX, y + 3.8D, centerZ, 0xFF52FF8A, 0.028F, true, 0.0F, true);
    }

    private static void renderPreview(S2C_TerritoryPreviewPacket packet, Minecraft minecraft,
                                      PoseStack poseStack, MultiBufferSource.BufferSource buffers, Vec3 camera) {
        TerritoryPreviewArea area = new TerritoryPreviewArea(
                packet.centerChunkX(), packet.centerChunkZ(), packet.radius());
        double y = Math.floor(minecraft.player.getY()) + 0.035D;
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
    }

    private static void renderClosure(S2C_TerritoryClosurePacket packet, PoseStack poseStack,
                                      MultiBufferSource.BufferSource buffers, Vec3 camera) {
        java.util.Set<BlockPos> unreinforced = new java.util.HashSet<>(packet.unreinforcedLeakBlocks());
        for (BlockPos pos : packet.escapePath()) {
            if (unreinforced.contains(pos)) continue;
            AABB marker = new AABB(pos).inflate(-0.32D).move(-camera.x, -camera.y, -camera.z);
            DebugRenderer.renderFilledBox(poseStack, buffers, marker, 1.0F, 0.58F, 0.10F, 0.72F);
        }
        if (!packet.escapePath().isEmpty()) {
            BlockPos leak = packet.escapePath().get(packet.escapePath().size() - 1);
            AABB marker = new AABB(leak).inflate(-0.07D).move(-camera.x, -camera.y, -camera.z);
            DebugRenderer.renderFilledBox(poseStack, buffers, marker, 1.0F, 0.12F, 0.10F, 0.82F);
        }
        float pulse = 0.5F + 0.5F * (float) Math.sin(net.minecraft.Util.getMillis() / 180.0D);
        VertexConsumer outline = buffers.getBuffer(RenderType.lines());
        for (BlockPos pos : packet.unreinforcedLeakBlocks()) {
            AABB full = new AABB(pos).inflate(0.018D).move(-camera.x, -camera.y, -camera.z);
            DebugRenderer.renderFilledBox(poseStack, buffers, full,
                    1.0F, 0.04F + pulse * 0.10F, 0.30F, 0.34F + pulse * 0.20F);
            LevelRenderer.renderLineBox(poseStack, outline, full.inflate(0.025D),
                    0.28F, 0.01F, 0.06F, 1.0F);
            LevelRenderer.renderLineBox(poseStack, outline, full,
                    1.0F, 0.22F, 0.52F, 1.0F);
            AABB beacon = new AABB(pos.getX() + 0.43D, pos.getY() + 1.0D, pos.getZ() + 0.43D,
                    pos.getX() + 0.57D, pos.getY() + 3.8D + pulse, pos.getZ() + 0.57D)
                    .move(-camera.x, -camera.y, -camera.z);
            DebugRenderer.renderFilledBox(poseStack, buffers, beacon, 1.0F, 0.08F, 0.36F, 0.62F);
        }
        if (!packet.unreinforcedLeakBlocks().isEmpty()) {
            BlockPos nearest = packet.unreinforcedLeakBlocks().getFirst();
            DebugRenderer.renderFloatingText(poseStack, buffers,
                    Component.translatable("overlay.moveearth_addtional.territory_closure.unreinforced_marker",
                            nearest.getX(), nearest.getY(), nearest.getZ()).getString(),
                    nearest.getX() + 0.5D, nearest.getY() + 1.35D, nearest.getZ() + 0.5D,
                    0xFFFF3970, 0.028F, true, 0.0F, true);
        }
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
        S2C_TerritoryClosurePacket closure = TerritoryPreviewClientState.closure();
        if (minecraft.options.hideGui || minecraft.player == null
                || minecraft.level == null || minecraft.screen != null) return;
        boolean showPreview = packet != null
                && minecraft.level.dimension().location().equals(packet.dimension());
        boolean showClosure = closure != null
                && minecraft.level.dimension().location().equals(closure.dimension());
        if (!showPreview && !showClosure) return;
        var graphics = event.getGuiGraphics();
        int x = 12;
        int y = 12;
        boolean showLeakCount = showClosure && !closure.unreinforcedLeakBlocks().isEmpty();
        int panelHeight = (showPreview && showClosure ? 80 : 52) + (showLeakCount ? 13 : 0);
        graphics.fill(x, y, x + 266, y + panelHeight, 0xD012161D);
        graphics.fill(x, y, x + 3, y + panelHeight, 0xFF68E09B);
        int lineY = y + 8;
        if (showPreview) {
            graphics.drawString(minecraft.font,
                    Component.translatable("overlay.moveearth_addtional.territory_preview"),
                    x + 11, lineY, 0xFF68E09B, false);
            graphics.drawString(minecraft.font,
                    Component.translatable("overlay.moveearth_addtional.territory_preview.detail",
                            packet.radius(), packet.chunkCount()), x + 11, lineY + 13, 0xFFE8EDF3, false);
            lineY += 28;
        }
        if (showClosure) {
            int color = closure.success() ? 0xFF68E09B : 0xFFFF785F;
            graphics.drawString(minecraft.font,
                    Component.translatable("overlay.moveearth_addtional.territory_closure"),
                    x + 11, lineY, color, false);
            graphics.drawString(minecraft.font,
                    Component.translatable(closure.messageKey(), closure.visited()),
                    x + 11, lineY + 13, 0xFFE8EDF3, false);
            if (showLeakCount) {
                BlockPos nearest = closure.unreinforcedLeakBlocks().getFirst();
                graphics.drawString(minecraft.font,
                        Component.translatable("overlay.moveearth_addtional.territory_closure.unreinforced",
                                closure.unreinforcedLeakBlocks().size(),
                                nearest.getX(), nearest.getY(), nearest.getZ()),
                        x + 11, lineY + 26, 0xFFFF3970, false);
            }
            lineY += showLeakCount ? 41 : 28;
        }
        graphics.drawString(minecraft.font,
                Component.translatable("overlay.moveearth_addtional.territory_preview.exit",
                        S2ClientKeys.CLEAR_TERRITORY_PREVIEW.getTranslatedKeyMessage()),
                x + 11, lineY, 0xFF8F9AA8, false);
    }
}
