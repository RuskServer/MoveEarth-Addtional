package com.ruskserver.moveearth_addtional.client.upscale;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import com.ruskserver.moveearth_addtional.client.scope.ScopePipRenderer;
import com.ruskserver.moveearth_addtional.client.upscale.UpscaleClientConfig.Mode;
import com.ruskserver.moveearth_addtional.client.upscale.UpscaleClientConfig.Quality;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

/**
 * SMAA 1x on the world image, optionally with FSR 1.0 upscaling from a lower world resolution.
 * Single-frame only: no history, jitter or motion vectors, so moving Sable bodies, entities and
 * particles cannot ghost. Hands and HUD are drawn afterwards at display resolution.
 */
@EventBusSubscriber(modid = Moveearth_addtional.MODID, value = Dist.CLIENT)
public final class UpscaleRenderer {
    private static final String KEY = "upscale.moveearth_addtional.";
    private static Mode mode = Mode.OFF;
    private static Quality quality = Quality.ULTRA_QUALITY;
    private static int sharpnessPercent = 80;
    private static Mode failedMode;
    private static String status = "off";
    /** World render target below display resolution (SMAA_FSR). */
    private static TextureTarget world;
    /** Copy of the display-resolution world colour, since the pass cannot read what it writes (SMAA). */
    private static TextureTarget input;
    private static TextureTarget edges;
    private static TextureTarget weights;
    private static TextureTarget smaaOutput;
    private static TextureTarget easuOutput;
    private static RenderTarget display;
    private static boolean worldRedirected;

    private UpscaleRenderer() { }

    /** Sodium applies bindings before it flushes the NeoForge client config. */
    public static void settingsChanged() {
        if (RenderSystem.isOnRenderThread()) refreshSettings();
        else RenderSystem.recordRenderCall(UpscaleRenderer::refreshSettings);
    }

    private static void refreshSettings() {
        Mode selected = UpscaleClientConfig.mode();
        if (selected != failedMode) failedMode = null;
        Mode next = selected == failedMode ? Mode.OFF : selected;
        Quality nextQuality = UpscaleClientConfig.quality();
        if (next != mode || nextQuality != quality) {
            release();
            mode = next;
            quality = nextQuality;
            status = next == Mode.OFF ? "off" : "waiting";
        }
        sharpnessPercent = UpscaleClientConfig.sharpnessPercent();
    }

    /** While the world renders at reduced resolution, it is the main target for everything in it. */
    public static RenderTarget worldTargetOverride() { return worldRedirected ? world : null; }

    /**
     * Just before LevelRenderer.renderLevel: redirect the world pass to the low-resolution target.
     * Not at GameRenderer.renderLevel HEAD, where the scope PiP renders its lens: depending on mixin
     * order the lens pass then saw our target as the main one and nested into our redirect.
     */
    public static void beginWorld() {
        refreshSettings();
        if (mode == Mode.OFF || ScopePipRenderer.isRenderingLens()) return;
        UpscaleProfiler.mark(UpscaleProfiler.WORLD_START);
        if (mode != Mode.SMAA_FSR) return;
        Minecraft mc = Minecraft.getInstance();
        RenderTarget main = mc.getMainRenderTarget();
        String reason = unavailable(mc, main);
        if (reason != null) {
            status = reason;
            return;
        }
        try {
            world = ensure(world, UpscalePolicy.renderSize(main.width, quality.factor),
                    UpscalePolicy.renderSize(main.height, quality.factor), true, main.isStencilEnabled(), GL11.GL_LINEAR);
            world.clear(Minecraft.ON_OSX);
            display = main;
            worldRedirected = true;
            world.bindWrite(true);
        } catch (RuntimeException | LinkageError exception) {
            worldRedirected = false;
            display = null;
            fail(Mode.SMAA_FSR, "Upscaled world target unavailable", exception);
            main.bindWrite(true);
        }
    }

    /** After the AFTER_LEVEL stage, before hands: anti-alias, then upscale back to the display. */
    public static void finishWorld() {
        if (ScopePipRenderer.isRenderingLens() || mode == Mode.OFF && !worldRedirected) return;
        UpscaleProfiler.mark(UpscaleProfiler.WORLD_END);
        try {
            if (worldRedirected) finishUpscaled(); else finishNative();
        } finally {
            UpscaleProfiler.mark(UpscaleProfiler.FRAME_END);
            UpscaleProfiler.endFrame();
        }
    }

    private static void finishNative() {
        if (mode != Mode.SMAA || ScopePipRenderer.isRenderingLens()) return;
        Minecraft mc = Minecraft.getInstance();
        RenderTarget main = mc.getMainRenderTarget();
        String reason = unavailable(mc, main);
        if (reason != null) {
            status = reason;
            return;
        }
        try (UpscaleGlState ignored = new UpscaleGlState()) {
            input = ensure(input, main.width, main.height, false, false, GL11.GL_LINEAR);
            blitColor(main, input);
            smaa(input, main.getDepthTextureId(), main);
            UpscaleProfiler.mark(UpscaleProfiler.SMAA_END);
            status = "smaa_active";
        } catch (RuntimeException | LinkageError exception) {
            fail(Mode.SMAA, "SMAA disabled after a render failure", exception);
        }
    }

    private static void finishUpscaled() {
        worldRedirected = false;
        RenderTarget main = display;
        display = null;
        try (UpscaleGlState ignored = new UpscaleGlState()) {
            smaaOutput = ensure(smaaOutput, world.width, world.height, false, false, GL11.GL_NEAREST);
            if (UpscalePolicy.rcasEnabled(sharpnessPercent)) {
                easuOutput = ensure(easuOutput, main.width, main.height, false, false, GL11.GL_NEAREST);
            } else easuOutput = destroy(easuOutput);
            smaa(world, world.getDepthTextureId(), smaaOutput);
            UpscaleProfiler.mark(UpscaleProfiler.SMAA_END);
            float[] con = UpscalePolicy.easuConstants(world.width, world.height, main.width, main.height);
            // At 0% sharpness RCAS is skipped and EASU writes the display directly: one full pass fewer.
            RenderTarget easuTarget = UpscalePolicy.rcasEnabled(sharpnessPercent) ? easuOutput : main;
            pass(UpscaleShaders.fsrEasu, easuTarget, false, shader -> {
                shader.setSampler("InputTex", smaaOutput.getColorTextureId());
                for (int i = 0; i < 4; i++) {
                    shader.safeGetUniform("Con" + i).set(con[i * 4], con[i * 4 + 1], con[i * 4 + 2], con[i * 4 + 3]);
                }
            });
            UpscaleProfiler.mark(UpscaleProfiler.EASU_END);
            if (UpscalePolicy.rcasEnabled(sharpnessPercent)) {
                float[] rcas = UpscalePolicy.rcasConstants(UpscalePolicy.sharpnessStops(sharpnessPercent));
                pass(UpscaleShaders.fsrRcas, main, false, shader -> {
                    shader.setSampler("InputTex", easuOutput.getColorTextureId());
                    shader.safeGetUniform("Con").set(rcas[0], rcas[1], rcas[2], rcas[3]);
                });
            }
            // No depth copy: the hand pass clears depth next, and AFTER_LEVEL already ran on the world target.
            status = "fsr_active";
        } catch (RuntimeException | LinkageError exception) {
            try {
                // Keep this frame on screen with a plain linear upscale before disabling the feature.
                GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, world.frameBufferId);
                GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, main.frameBufferId);
                GlStateManager._glBlitFrameBuffer(0, 0, world.width, world.height, 0, 0, main.width, main.height,
                        GL11.GL_COLOR_BUFFER_BIT, GL11.GL_LINEAR);
            } catch (RuntimeException | LinkageError fallbackFailure) {
                exception.addSuppressed(fallbackFailure);
            }
            fail(Mode.SMAA_FSR, "FSR upscaling disabled after a render failure", exception);
        } finally {
            main.bindWrite(true);
        }
    }

    /** SMAA 1x: edges, blending weights, neighbourhood blending from {@code source} into {@code out}. */
    private static void smaa(RenderTarget source, int depthTexture, RenderTarget out) {
        int width = source.width, height = source.height;
        edges = ensure(edges, width, height, false, false, GL11.GL_LINEAR);
        weights = ensure(weights, width, height, false, false, GL11.GL_LINEAR);
        float[] metrics = UpscalePolicy.rtMetrics(width, height);
        Consumer<ShaderInstance> rt = shader -> shader.safeGetUniform("RtMetrics")
                .set(metrics[0], metrics[1], metrics[2], metrics[3]);
        if (UpscaleClientConfig.edgeDetection() == UpscaleClientConfig.EdgeDetection.DEPTH) {
            // The world projection is still current here; hands install their own afterwards.
            var projection = RenderSystem.getProjectionMatrix();
            pass(UpscaleShaders.smaaDepthEdges, edges, true, shader -> {
                shader.setSampler("DepthTex", depthTexture);
                shader.safeGetUniform("DepthParams").set(projection.m22(), projection.m32());
                shader.safeGetUniform("Threshold").set(UpscalePolicy.DEPTH_EDGE_THRESHOLD);
            });
        } else {
            pass(UpscaleShaders.smaaEdges, edges, true, shader -> {
                rt.accept(shader);
                shader.setSampler("ColorTex", source.getColorTextureId());
            });
        }
        pass(UpscaleShaders.smaaWeights, weights, true, shader -> {
            rt.accept(shader);
            shader.setSampler("EdgesTex", edges.getColorTextureId());
            shader.setSampler("AreaTex", UpscaleShaders.areaTexture());
            shader.setSampler("SearchTex", UpscaleShaders.searchTexture());
        });
        pass(UpscaleShaders.smaaBlend, out, false, shader -> {
            rt.accept(shader);
            shader.setSampler("ColorTex", source.getColorTextureId());
            shader.setSampler("BlendTex", weights.getColorTextureId());
        });
    }

    private static void pass(ShaderInstance shader, RenderTarget out, boolean clear, Consumer<ShaderInstance> setup) {
        out.bindWrite(true);
        RenderSystem.disableScissor();
        RenderSystem.colorMask(true, true, true, true);
        if (clear) {
            RenderSystem.clearColor(0F, 0F, 0F, 0F);
            RenderSystem.clear(GL11.GL_COLOR_BUFFER_BIT, Minecraft.ON_OSX);
        }
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableBlend();
        RenderSystem.disableCull();
        GL11.glDisable(GL11.GL_STENCIL_TEST);
        setup.accept(shader);
        RenderSystem.setShader(() -> shader);
        var buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION);
        buffer.addVertex(-1F, -1F, 0F);
        buffer.addVertex(1F, -1F, 0F);
        buffer.addVertex(1F, 1F, 0F);
        buffer.addVertex(-1F, 1F, 0F);
        try {
            BufferUploader.drawWithShader(buffer.buildOrThrow());
        } finally {
            shader.clear();
        }
    }

    private static TextureTarget ensure(TextureTarget target, int width, int height, boolean depth,
                                        boolean stencil, int filter) {
        if (target != null && target.width == width && target.height == height
                && target.isStencilEnabled() == stencil) return target;
        if (target != null) target.destroyBuffers();
        UpscaleProfiler.reallocated();
        TextureTarget created = new TextureTarget(width, height, depth, Minecraft.ON_OSX);
        // PiP can enable stencil on the main target; depth copies need matching formats.
        if (stencil) created.enableStencil();
        created.setFilterMode(filter);
        return created;
    }

    private static void blitColor(RenderTarget from, RenderTarget to) {
        GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, from.frameBufferId);
        GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, to.frameBufferId);
        GlStateManager._glBlitFrameBuffer(0, 0, from.width, from.height, 0, 0, to.width, to.height,
                GL11.GL_COLOR_BUFFER_BIT, GL11.GL_NEAREST);
    }

    private static String unavailable(Minecraft mc, RenderTarget main) {
        if (mc.level == null || mc.player == null) return "waiting";
        if (!UpscaleShaders.smaaReady() || mode == Mode.SMAA_FSR && !UpscaleShaders.fsrReady()) return "shader";
        var iris = UpscaleIrisState.current();
        if (iris == UpscaleIrisState.State.ACTIVE) return "shader_pack";
        if (iris == UpscaleIrisState.State.UNKNOWN) return "iris_api";
        if (ModList.get().isLoaded("oculus") || ModList.get().isLoaded("distanthorizons")
                || ModList.get().isLoaded("acceleratedrendering")) return "pipeline";
        if (Minecraft.useShaderTransparency() || mc.gameRenderer.currentEffect() != null) return "pipeline";
        if (main.width <= 0 || main.height <= 0 || (long) main.width * main.height > UpscalePolicy.MAX_PIXELS
                || main.getDepthTextureId() < 0) return "target";
        return null;
    }

    private static void fail(Mode failed, String message, Throwable exception) {
        Moveearth_addtional.LOGGER.warn(message, exception);
        failedMode = failed;
        mode = Mode.OFF;
        status = "failed";
        release();
    }

    @SubscribeEvent
    public static void hud(RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mode == Mode.OFF && failedMode == null || mc.player == null || mc.options.hideGui
                || !UpscaleClientConfig.showStatus()) return;
        RenderTarget main = mc.getMainRenderTarget();
        String resolution = mode == Mode.SMAA_FSR && world != null
                ? world.width + "x" + world.height + " -> " + main.width + "x" + main.height
                : main.width + "x" + main.height;
        event.getGuiGraphics().drawString(mc.font, Component.translatable(KEY + "status",
                Component.translatable(KEY + "mode." + UpscaleClientConfig.mode().name().toLowerCase(java.util.Locale.ROOT)),
                Component.translatable(KEY + "state." + status), resolution), 8, 8, 0xFFFFFF, true);
        if (mode == Mode.OFF) return;
        event.getGuiGraphics().drawString(mc.font, Component.translatable(KEY + "timing",
                format(UpscaleProfiler.millis(0)), format(UpscaleProfiler.millis(1)),
                format(UpscaleProfiler.millis(2)), format(UpscaleProfiler.millis(3)),
                UpscaleProfiler.reallocationsPerSecond()), 8, 20, 0xFFFFFF, true);
    }

    @SubscribeEvent
    public static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
        if (RenderSystem.isOnRenderThread()) release(); else RenderSystem.recordRenderCall(UpscaleRenderer::release);
    }

    private static void release() {
        worldRedirected = false;
        display = null;
        world = destroy(world);
        input = destroy(input);
        edges = destroy(edges);
        weights = destroy(weights);
        smaaOutput = destroy(smaaOutput);
        easuOutput = destroy(easuOutput);
        UpscaleProfiler.release();
    }

    private static String format(double millis) {
        return String.format(java.util.Locale.ROOT, "%.2f", millis);
    }

    private static TextureTarget destroy(TextureTarget target) {
        if (target != null) target.destroyBuffers();
        return null;
    }
}
