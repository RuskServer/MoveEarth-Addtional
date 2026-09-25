package com.ruskserver.moveearth_addtional.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import com.ruskserver.moveearth_addtional.network.S2C_WaypointPacket;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.debug.DebugRenderer;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/** A world-space, map-style destination beacon. No chunk loading or per-tick server work. */
@EventBusSubscriber(modid = Moveearth_addtional.MODID, bus = EventBusSubscriber.Bus.GAME, value = Dist.CLIENT)
public final class WaypointWorldRenderer {
    private static final double MAX_VISUAL_DISTANCE = 192.0D;
    private static final double BEAM_HEIGHT = 96.0D;

    private WaypointWorldRenderer() { }

    @SubscribeEvent
    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        Minecraft mc = Minecraft.getInstance();
        S2C_WaypointPacket waypoint = EconomyWaypointHud.waypoint();
        if (!waypoint.active() || mc.level == null || mc.player == null || mc.options.hideGui
                || mc.screen != null || !mc.level.dimension().location().equals(waypoint.dimension())) return;

        Vec3 camera = event.getCamera().getPosition();
        Vec3 target = waypoint.pos().getCenter();
        double actualDistance = mc.player.position().distanceTo(target);
        if (actualDistance < 2.5D) return;
        double dx = target.x - camera.x;
        double dz = target.z - camera.z;
        double horizontal = Math.hypot(dx, dz);
        double visualDistance = Math.min(MAX_VISUAL_DISTANCE,
                Math.max(32.0D, mc.options.renderDistance().get() * 16.0D - 24.0D));
        double factor = horizontal > visualDistance ? visualDistance / horizontal : 1.0D;
        double x = camera.x + dx * factor;
        double z = camera.z + dz * factor;
        // For remote targets, keep the projected marker within a useful vertical range.
        double y = factor < 1.0D
                ? camera.y + Math.clamp((target.y - camera.y) * factor, -24.0D, 24.0D)
                : target.y;
        float markerScale = WaypointNavigation.worldMarkerScale(
                camera.distanceTo(new Vec3(x, y, z)));
        double beamWidthScale = Math.min(markerScale, 8.0F);
        double beamHeight = BEAM_HEIGHT + Math.min(96.0D, (markerScale - 1.0F) * 12.0D);
        float pulse = 0.90F + 0.10F * (float) Math.sin(Util.getMillis() / 350.0D);
        float red = waypoint.market() ? 1.0F : 0.28F;
        float green = waypoint.market() ? 0.73F : 0.86F;
        float blue = waypoint.market() ? 0.33F : 1.0F;

        PoseStack pose = event.getPoseStack();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        // Layered, low-alpha columns read as a light beam rather than a solid wall.
        beam(pose, buffers, camera, x, y, z, 0.34D * beamWidthScale, beamHeight,
                red, green, blue, 0.055F * pulse);
        beam(pose, buffers, camera, x, y, z, 0.14D * beamWidthScale, beamHeight,
                red, green, blue, 0.16F * pulse);
        beam(pose, buffers, camera, x, y, z, 0.035D * beamWidthScale, beamHeight,
                red, green, blue, 0.55F * pulse);
        DebugRenderer.renderFilledBox(pose, buffers,
                new AABB(x - 0.35D * beamWidthScale, y + 0.05D, z - 0.35D * beamWidthScale,
                        x + 0.35D * beamWidthScale, y + 0.12D, z + 0.35D * beamWidthScale)
                        .move(-camera.x, -camera.y, -camera.z), red, green, blue, 0.72F);
        buffers.endBatch(RenderType.debugFilledBox());

        String icon = waypoint.market() ? "▣" : "◆";
        String name = mc.font.plainSubstrByWidth(waypoint.name(), 180);
        int color = waypoint.market() ? 0xFFFFC363 : 0xFF70DCFF;
        float scale = 0.035F * markerScale;
        DebugRenderer.renderFloatingText(pose, buffers, icon, x, y + 3.05D * markerScale, z,
                color, scale * 1.6F, true, 0.0F, true);
        DebugRenderer.renderFloatingText(pose, buffers, name, x, y + 2.35D * markerScale, z,
                0xFFF3F7FA, scale, true, 0.0F, true);
        DebugRenderer.renderFloatingText(pose, buffers, Math.round(actualDistance) + " m",
                x, y + 1.85D * markerScale, z,
                color, scale * 0.85F, true, 0.0F, true);
        buffers.endBatch();
    }

    private static void beam(PoseStack pose, MultiBufferSource buffers, Vec3 camera,
                             double x, double y, double z, double halfWidth, double height,
                             float red, float green, float blue, float alpha) {
        DebugRenderer.renderFilledBox(pose, buffers,
                new AABB(x - halfWidth, y, z - halfWidth,
                        x + halfWidth, y + height, z + halfWidth)
                        .move(-camera.x, -camera.y, -camera.z), red, green, blue, alpha);
    }
}
