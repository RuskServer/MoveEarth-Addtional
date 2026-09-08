package com.ruskserver.moveearth_addtional.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import com.ruskserver.moveearth_addtional.network.S2C_PvpZonePacket;
import com.ruskserver.moveearth_addtional.pvp.PvpZoneState;
import net.minecraft.Util;
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
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

@EventBusSubscriber(modid = Moveearth_addtional.MODID, bus = EventBusSubscriber.Bus.GAME, value = Dist.CLIENT)
public final class PvpHardpointRenderer {
    private static final double MAX_RENDER_DISTANCE_SQUARED = 192.0D * 192.0D;
    private static final double RAIL_WIDTH = 0.10D;
    private static final double RAIL_HEIGHT = 0.055D;
    private static final double FLOOR_OFFSET = 0.035D;

    private PvpHardpointRenderer() {
    }

    @SubscribeEvent
    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;

        Minecraft minecraft = Minecraft.getInstance();
        S2C_PvpZonePacket zone = PvpHardpointClientState.zone();
        if (!zone.active() || minecraft.level == null || minecraft.player == null
                || !minecraft.level.dimension().location().equals(zone.dimension())) return;

        AABB bounds = bounds(zone);
        Vec3 camera = event.getCamera().getPosition();
        double centerX = (bounds.minX + bounds.maxX) * 0.5D;
        double centerY = (bounds.minY + bounds.maxY) * 0.5D;
        double centerZ = (bounds.minZ + bounds.maxZ) * 0.5D;
        if (camera.distanceToSqr(centerX, centerY, centerZ) > MAX_RENDER_DISTANCE_SQUARED) return;
        if (!event.getFrustum().isVisible(bounds.inflate(2.0D))) return;

        long now = Util.getMillis();
        int color = PvpHardpointClientState.color(now);
        float red = ((color >> 16) & 0xFF) / 255.0F;
        float green = ((color >> 8) & 0xFF) / 255.0F;
        float blue = (color & 0xFF) / 255.0F;
        double speed = zone.state() == PvpZoneState.CONTESTED ? 170.0D : 320.0D;
        float pulse = (float) (0.82D + 0.18D * Math.sin(now / speed));
        red = Math.min(1.0F, red * pulse);
        green = Math.min(1.0F, green * pulse);
        blue = Math.min(1.0F, blue * pulse);

        PoseStack poseStack = event.getPoseStack();
        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        renderFloorAndRails(poseStack, buffers, bounds, camera, red, green, blue, now, zone.state());
        buffers.endBatch(RenderType.debugFilledBox());

        if (!minecraft.options.hideGui) {
            renderMarker(poseStack, buffers, bounds, zone, minecraft, color);
            buffers.endBatch();
        }
    }

    private static void renderFloorAndRails(PoseStack poseStack, MultiBufferSource buffers, AABB bounds, Vec3 camera,
                                            float red, float green, float blue, long now, PvpZoneState state) {
        double floorY = bounds.minY + FLOOR_OFFSET;
        float fillAlpha = state == PvpZoneState.CONTESTED ? 0.105F : 0.075F;
        renderBox(poseStack, buffers, camera,
                new AABB(bounds.minX + RAIL_WIDTH, floorY, bounds.minZ + RAIL_WIDTH,
                        bounds.maxX - RAIL_WIDTH, floorY + 0.018D, bounds.maxZ - RAIL_WIDTH),
                red, green, blue, fillAlpha);

        float railAlpha = state == PvpZoneState.CONTESTED ? 0.82F : 0.66F;
        renderBox(poseStack, buffers, camera,
                new AABB(bounds.minX, floorY, bounds.minZ, bounds.maxX, floorY + RAIL_HEIGHT, bounds.minZ + RAIL_WIDTH),
                red, green, blue, railAlpha);
        renderBox(poseStack, buffers, camera,
                new AABB(bounds.minX, floorY, bounds.maxZ - RAIL_WIDTH, bounds.maxX, floorY + RAIL_HEIGHT, bounds.maxZ),
                red, green, blue, railAlpha);
        renderBox(poseStack, buffers, camera,
                new AABB(bounds.minX, floorY, bounds.minZ + RAIL_WIDTH, bounds.minX + RAIL_WIDTH, floorY + RAIL_HEIGHT, bounds.maxZ - RAIL_WIDTH),
                red, green, blue, railAlpha);
        renderBox(poseStack, buffers, camera,
                new AABB(bounds.maxX - RAIL_WIDTH, floorY, bounds.minZ + RAIL_WIDTH, bounds.maxX, floorY + RAIL_HEIGHT, bounds.maxZ - RAIL_WIDTH),
                red, green, blue, railAlpha);

        double pylonHeight = Math.min(1.45D, Math.max(0.45D, bounds.maxY - bounds.minY));
        double pylonWidth = 0.075D;
        renderPylon(poseStack, buffers, camera, bounds.minX, floorY, bounds.minZ, pylonWidth, pylonHeight, red, green, blue);
        renderPylon(poseStack, buffers, camera, bounds.minX, floorY, bounds.maxZ, pylonWidth, pylonHeight, red, green, blue);
        renderPylon(poseStack, buffers, camera, bounds.maxX, floorY, bounds.minZ, pylonWidth, pylonHeight, red, green, blue);
        renderPylon(poseStack, buffers, camera, bounds.maxX, floorY, bounds.maxZ, pylonWidth, pylonHeight, red, green, blue);

        double scanProgress = (now % 1800L) / 1800.0D;
        double scanZ = bounds.minZ + RAIL_WIDTH + (bounds.maxZ - bounds.minZ - RAIL_WIDTH * 2.0D) * scanProgress;
        renderBox(poseStack, buffers, camera,
                new AABB(bounds.minX + 0.22D, floorY + 0.024D, scanZ - 0.035D,
                        bounds.maxX - 0.22D, floorY + 0.044D, scanZ + 0.035D),
                red, green, blue, state == PvpZoneState.CONTESTED ? 0.48F : 0.31F);
    }

    private static void renderPylon(PoseStack poseStack, MultiBufferSource buffers, Vec3 camera,
                                    double x, double y, double z, double width, double height,
                                    float red, float green, float blue) {
        renderBox(poseStack, buffers, camera,
                new AABB(x - width, y, z - width, x + width, y + height, z + width),
                red, green, blue, 0.72F);
    }

    private static void renderBox(PoseStack poseStack, MultiBufferSource buffers, Vec3 camera, AABB worldBox,
                                  float red, float green, float blue, float alpha) {
        DebugRenderer.renderFilledBox(poseStack, buffers,
                worldBox.move(-camera.x, -camera.y, -camera.z), red, green, blue, alpha);
    }

    private static void renderMarker(PoseStack poseStack, MultiBufferSource buffers, AABB bounds,
                                     S2C_PvpZonePacket zone, Minecraft minecraft, int color) {
        double x = (bounds.minX + bounds.maxX) * 0.5D;
        double z = (bounds.minZ + bounds.maxZ) * 0.5D;
        double y = Math.min(bounds.maxY + 0.35D, bounds.minY + 3.1D);
        double distance = minecraft.player.position().distanceTo(new Vec3(x, bounds.minY, z));
        String title = Component.translatable("overlay.moveearth_addtional.pvp.hardpoint.title").getString()
                + "  " + Math.round(distance) + "m";
        String status = Component.translatable(zone.state().translationKey(), zone.redPlayers(), zone.bluePlayers()).getString();
        int argb = 0xFF000000 | color;
        DebugRenderer.renderFloatingText(poseStack, buffers, title, x, y + 0.34D, z,
                argb, 0.026F, true, 0.0F, true);
        DebugRenderer.renderFloatingText(poseStack, buffers, status, x, y, z,
                0xFFEAF3FA, 0.018F, true, 0.0F, true);
    }

    private static AABB bounds(S2C_PvpZonePacket zone) {
        double minX = Math.min(zone.min().getX(), zone.max().getX());
        double minY = Math.min(zone.min().getY(), zone.max().getY());
        double minZ = Math.min(zone.min().getZ(), zone.max().getZ());
        double maxX = Math.max(zone.min().getX(), zone.max().getX()) + 1.0D;
        double maxY = Math.max(zone.min().getY(), zone.max().getY()) + 1.0D;
        double maxZ = Math.max(zone.min().getZ(), zone.max().getZ()) + 1.0D;
        return new AABB(minX, minY, minZ, maxX, maxY, maxZ);
    }
}
