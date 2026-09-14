package com.ruskserver.moveearth_addtional.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;

/** One-pass procedural gas-mask lens vignette with a safe legacy fallback. */
@EventBusSubscriber(modid = Moveearth_addtional.MODID, value = Dist.CLIENT,
        bus = EventBusSubscriber.Bus.MOD)
public final class GasMaskShaderRenderer {
    private static final ResourceLocation SHADER = ResourceLocation.fromNamespaceAndPath(
            Moveearth_addtional.MODID, "gas_mask_vignette");
    private static ShaderInstance instance;
    private static boolean failed;

    private GasMaskShaderRenderer() { }

    @SubscribeEvent
    public static void registerShader(RegisterShadersEvent event) {
        instance = null;
        failed = false;
        try {
            event.registerShader(new ShaderInstance(event.getResourceProvider(), SHADER,
                    DefaultVertexFormat.POSITION), loaded -> instance = loaded);
        } catch (Exception exception) {
            failed = true;
            Moveearth_addtional.LOGGER.warn(
                    "[MoveEarth] Gas-mask shader unavailable; using fallback vignette ({})",
                    exception.getClass().getSimpleName());
        }
    }

    public static boolean render(GuiGraphics graphics, int width, int height, float opacity,
                                 float fogStrength, float panicStrength, long nowMillis) {
        ShaderInstance shader = instance;
        if (failed || shader == null || opacity <= 0.001F) return false;
        try {
            graphics.flush();
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.disableDepthTest();
            RenderSystem.depthMask(false);
            RenderSystem.setShader(() -> shader);
            shader.safeGetUniform("MaskAlpha").set(opacity);
            shader.safeGetUniform("FogStrength").set(fogStrength);
            shader.safeGetUniform("PanicStrength").set(panicStrength);
            shader.safeGetUniform("Time").set((nowMillis % 60_000L) / 1_000.0F);

            BufferBuilder buffer = Tesselator.getInstance().begin(
                    VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION);
            var matrix = graphics.pose().last().pose();
            buffer.addVertex(matrix, 0.0F, height, 0.0F);
            buffer.addVertex(matrix, width, height, 0.0F);
            buffer.addVertex(matrix, width, 0.0F, 0.0F);
            buffer.addVertex(matrix, 0.0F, 0.0F, 0.0F);
            BufferUploader.drawWithShader(buffer.buildOrThrow());
            return true;
        } catch (RuntimeException | LinkageError exception) {
            failed = true;
            instance = null;
            Moveearth_addtional.LOGGER.warn(
                    "[MoveEarth] Gas-mask shader failed while rendering; using fallback vignette ({})",
                    exception.getClass().getSimpleName());
            return false;
        } finally {
            RenderSystem.depthMask(true);
            RenderSystem.enableDepthTest();
            RenderSystem.disableBlend();
            RenderSystem.setShader(GameRenderer::getPositionColorShader);
        }
    }
}
