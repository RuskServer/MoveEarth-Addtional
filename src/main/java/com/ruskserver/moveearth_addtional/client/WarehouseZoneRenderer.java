package com.ruskserver.moveearth_addtional.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import com.ruskserver.moveearth_addtional.network.s2c.other.S2C_WarehouseZonesPacket;
import com.ruskserver.moveearth_addtional.warehouse.WarehouseSitePolicy;
import com.ruskserver.moveearth_addtional.warehouse.WarehouseZoneView;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

import java.util.List;

/**
 * Draws each Warehouse footprint as a world-border force field: red where stepping
 * in calls out the guards, orange while they fight, blue when entering does
 * nothing. It fades in from {@link #FADE_DISTANCE} blocks away, like the vanilla
 * border, but only draws; unlike a real border it blocks nothing.
 */
@EventBusSubscriber(modid = Moveearth_addtional.MODID, value = Dist.CLIENT)
public final class WarehouseZoneRenderer {
    private static final double FADE_DISTANCE = 32.0D;
    private static final ResourceLocation FORCEFIELD = ResourceLocation.withDefaultNamespace("textures/misc/forcefield.png");
    private static final RenderType FIELD = RenderType.create("moveearth_warehouse_field",
            DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS, 256, false, true,
            RenderType.CompositeState.builder()
                    .setShaderState(new RenderStateShard.ShaderStateShard(GameRenderer::getPositionTexColorShader))
                    .setTextureState(new RenderStateShard.TextureStateShard(FORCEFIELD, false, false))
                    .setTransparencyState(RenderStateShard.LIGHTNING_TRANSPARENCY)
                    .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                    .setCullState(RenderStateShard.NO_CULL)
                    .setLayeringState(RenderStateShard.POLYGON_OFFSET_LAYERING)
                    .createCompositeState(false));

    private static List<S2C_WarehouseZonesPacket.Zone> zones = List.of();

    private WarehouseZoneRenderer() { }

    public static void update(S2C_WarehouseZonesPacket packet) {
        zones = packet.zones();
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        zones = List.of();
    }

    @SubscribeEvent
    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || zones.isEmpty()) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) return;
        ResourceLocation dimension = minecraft.level.dimension().location();
        Vec3 camera = event.getCamera().getPosition();
        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        VertexConsumer consumer = null;
        float scroll = (Util.getMillis() % 3000L) / 3000.0F;
        for (S2C_WarehouseZonesPacket.Zone zone : zones) {
            if (!zone.dimension().equals(dimension)) continue;
            int minX = zone.min().getX();
            int minZ = zone.min().getZ();
            float alpha = WarehouseZoneView.fieldAlpha(
                    WarehouseZoneView.distanceToFootprint(minX, minZ, camera.x, camera.z), FADE_DISTANCE);
            if (alpha <= 0.0F) continue;
            AABB box = new AABB(minX, zone.min().getY(), minZ, minX + WarehouseSitePolicy.WIDTH,
                    zone.min().getY() + WarehouseSitePolicy.HEIGHT, minZ + WarehouseSitePolicy.LENGTH);
            if (!event.getFrustum().isVisible(box.inflate(1.0D))) continue;
            if (consumer == null) consumer = buffers.getBuffer(FIELD);
            drawWalls(event.getPoseStack(), consumer, box.move(-camera.x, -camera.y, -camera.z),
                    zone.state().color(), alpha, scroll);
        }
        if (consumer != null) buffers.endBatch(FIELD);
    }

    private static void drawWalls(PoseStack poseStack, VertexConsumer consumer, AABB box, int color,
                                  float alpha, float scroll) {
        Matrix4f pose = poseStack.last().pose();
        int argb = ((int) (alpha * 255.0F) << 24) | color;
        float height = (float) (box.maxY - box.minY);
        wall(consumer, pose, box.minX, box.minZ, box.maxX, box.minZ, box, height, argb, scroll);
        wall(consumer, pose, box.maxX, box.minZ, box.maxX, box.maxZ, box, height, argb, scroll);
        wall(consumer, pose, box.maxX, box.maxZ, box.minX, box.maxZ, box, height, argb, scroll);
        wall(consumer, pose, box.minX, box.maxZ, box.minX, box.minZ, box, height, argb, scroll);
    }

    /** One wall; the texture repeats every two blocks and drifts like the vanilla border's. */
    private static void wall(VertexConsumer consumer, Matrix4f pose, double x0, double z0, double x1, double z1,
                             AABB box, float height, int argb, float scroll) {
        float length = (float) (Math.abs(x1 - x0) + Math.abs(z1 - z0));
        float u0 = scroll;
        float u1 = scroll + length * 0.5F;
        float v0 = scroll;
        float v1 = scroll + height * 0.5F;
        consumer.addVertex(pose, (float) x0, (float) box.minY, (float) z0).setUv(u0, v1).setColor(argb);
        consumer.addVertex(pose, (float) x1, (float) box.minY, (float) z1).setUv(u1, v1).setColor(argb);
        consumer.addVertex(pose, (float) x1, (float) box.maxY, (float) z1).setUv(u1, v0).setColor(argb);
        consumer.addVertex(pose, (float) x0, (float) box.maxY, (float) z0).setUv(u0, v0).setColor(argb);
    }
}
