package com.ruskserver.moveearth_addtional.client.nether;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import com.ruskserver.moveearth_addtional.nether.BlazeRodRefinerBlockEntity;
import com.ruskserver.moveearth_addtional.nether.NetherGateRegistry;
import com.simibubi.create.content.kinetics.base.HorizontalAxisKineticBlock;
import com.simibubi.create.content.kinetics.base.KineticBlockEntityRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.ModelEvent;

/**
 * Draws the refiner's moving parts over its static frame. The rod through the
 * machine is its drive shaft and turns with the network; while the refiner works
 * the press rides it, rising slowly and slamming down once per turn.
 */
@EventBusSubscriber(modid = Moveearth_addtional.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class BlazeRodRefinerRenderer implements BlockEntityRenderer<BlazeRodRefinerBlockEntity> {
    public static final ModelResourceLocation PRESS = model("blaze_processing_machine_press");
    public static final ModelResourceLocation ROD = model("blaze_processing_machine_rod");
    /** How far the press lifts before each stroke, in blocks. */
    public static final float PRESS_TRAVEL = 2.0F / 16.0F;
    /**
     * The rod's centre line, along X. The model is raised so that it runs through
     * the middle of the block, where Create shafts attach.
     */
    private static final float ROD_Y = 0.5F;
    private static final float ROD_Z = 0.5F;

    public BlazeRodRefinerRenderer(BlockEntityRendererProvider.Context context) { }

    @SubscribeEvent
    public static void register(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(NetherGateRegistry.BLAZE_ROD_REFINER_ENTITY.get(), BlazeRodRefinerRenderer::new);
    }

    @SubscribeEvent
    public static void models(ModelEvent.RegisterAdditional event) {
        event.register(PRESS);
        event.register(ROD);
    }

    private static ModelResourceLocation model(String name) {
        return ModelResourceLocation.standalone(ResourceLocation.fromNamespaceAndPath(Moveearth_addtional.MODID,
                "block/nether/" + name));
    }

    @Override
    public void render(BlazeRodRefinerBlockEntity refiner, float partialTick, PoseStack pose, MultiBufferSource buffers,
                       int light, int overlay) {
        var renderer = Minecraft.getInstance().getBlockRenderer().getModelRenderer();
        var models = Minecraft.getInstance().getModelManager();
        var vertices = buffers.getBuffer(RenderType.cutout());
        Direction.Axis axis = refiner.getBlockState().getValue(HorizontalAxisKineticBlock.HORIZONTAL_AXIS);
        float angle = KineticBlockEntityRenderer.getAngleForBe(refiner, refiner.getBlockPos(), axis);

        pose.pushPose();
        if (axis == Direction.Axis.Z) {
            // Same turn as the blockstate's y = 90, about the block centre.
            pose.translate(0.5F, 0.0F, 0.5F);
            pose.mulPose(Axis.YP.rotationDegrees(-90.0F));
            pose.translate(-0.5F, 0.0F, -0.5F);
        }

        pose.pushPose();
        pose.translate(0.0F, ROD_Y, ROD_Z);
        // After the turn above the model's +X is the world's +Z, so one rotation about +X
        // matches Create's shafts on either axis.
        pose.mulPose(Axis.XP.rotation(angle));
        pose.translate(0.0F, -ROD_Y, -ROD_Z);
        renderer.renderModel(pose.last(), vertices, refiner.getBlockState(), models.getModel(ROD),
                1.0F, 1.0F, 1.0F, light, overlay);
        pose.popPose();

        pose.pushPose();
        if (refiner.working()) {
            float turn = (float) (angle / (2.0D * Math.PI));
            pose.translate(0.0F, PRESS_TRAVEL * lift(turn - (float) Math.floor(turn)), 0.0F);
        }
        renderer.renderModel(pose.last(), vertices, refiner.getBlockState(), models.getModel(PRESS),
                1.0F, 1.0F, 1.0F, light, overlay);
        pose.popPose();

        pose.popPose();
    }

    /** Rises over 80% of a turn, slams down in the last 20%. */
    public static float lift(float cycle) {
        return cycle < 0.8F ? cycle / 0.8F : 1.0F - (cycle - 0.8F) / 0.2F;
    }
}
