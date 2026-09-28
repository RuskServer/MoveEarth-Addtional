package com.ruskserver.moveearth_addtional.client.upscale;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.platform.TextureUtil;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import java.io.IOException;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;

/** SMAA 1x (iryoku) and FSR 1.0 (AMD FidelityFX) programs plus SMAA's precomputed lookup textures. */
@EventBusSubscriber(modid = Moveearth_addtional.MODID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
final class UpscaleShaders {
    static ShaderInstance smaaEdges;
    static ShaderInstance smaaDepthEdges;
    static ShaderInstance smaaWeights;
    static ShaderInstance smaaBlend;
    static ShaderInstance fsrEasu;
    static ShaderInstance fsrRcas;
    static int generation;
    private static int areaTexture = -1;
    private static int searchTexture = -1;

    private UpscaleShaders() { }

    @SubscribeEvent
    public static void register(RegisterShadersEvent event) {
        smaaEdges = smaaDepthEdges = smaaWeights = smaaBlend = fsrEasu = fsrRcas = null;
        generation++;
        try {
            event.registerShader(program(event, "upscale_smaa_edges"), shader -> smaaEdges = shader);
            event.registerShader(program(event, "upscale_smaa_depth_edges"), shader -> smaaDepthEdges = shader);
            event.registerShader(program(event, "upscale_smaa_weights"), shader -> smaaWeights = shader);
            event.registerShader(program(event, "upscale_smaa_blend"), shader -> smaaBlend = shader);
            event.registerShader(program(event, "upscale_fsr_easu"), shader -> fsrEasu = shader);
            event.registerShader(program(event, "upscale_fsr_rcas"), shader -> fsrRcas = shader);
        } catch (IOException | RuntimeException exception) {
            Moveearth_addtional.LOGGER.warn("Anti-aliasing shaders unavailable; the feature stays inactive", exception);
        }
    }

    private static ShaderInstance program(RegisterShadersEvent event, String name) throws IOException {
        return new ShaderInstance(event.getResourceProvider(),
                ResourceLocation.fromNamespaceAndPath(Moveearth_addtional.MODID, name), DefaultVertexFormat.POSITION);
    }

    static boolean smaaReady() {
        return smaaEdges != null && smaaDepthEdges != null && smaaWeights != null && smaaBlend != null;
    }

    static boolean fsrReady() { return fsrEasu != null && fsrRcas != null; }

    /** Linear filtered, as SMAA samples it; loaded once on the render thread. */
    static int areaTexture() {
        if (areaTexture < 0) areaTexture = load("textures/upscale/smaa_area.png", true);
        return areaTexture;
    }

    /** Point filtered, as SMAA samples it. */
    static int searchTexture() {
        if (searchTexture < 0) searchTexture = load("textures/upscale/smaa_search.png", false);
        return searchTexture;
    }

    /**
     * Rows keep the reference header order: the first data row is uploaded as texture row 0, which is
     * the layout SMAA's texture coordinates address (as in other OpenGL ports).
     */
    private static int load(String path, boolean linear) {
        var location = ResourceLocation.fromNamespaceAndPath(Moveearth_addtional.MODID, path);
        try (var stream = Minecraft.getInstance().getResourceManager().open(location);
             NativeImage image = NativeImage.read(stream)) {
            int id = TextureUtil.generateTextureId();
            TextureUtil.prepareImage(id, image.getWidth(), image.getHeight());
            image.upload(0, 0, 0, 0, 0, image.getWidth(), image.getHeight(), linear, true, false, false);
            return id;
        } catch (IOException exception) {
            throw new IllegalStateException("Missing SMAA lookup texture " + location, exception);
        }
    }
}
