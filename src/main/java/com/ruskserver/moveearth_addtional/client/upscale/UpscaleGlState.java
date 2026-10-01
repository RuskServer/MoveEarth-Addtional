package com.ruskserver.moveearth_addtional.client.upscale;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.renderer.ShaderInstance;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

/**
 * Restores the GL state touched by target allocation, depth copies and the fullscreen passes.
 *
 * <p>State Minecraft caches in {@link GlStateManager} (enables, masks, viewport, texture bindings)
 * is read from that cache; the passes change it through {@code RenderSystem}, so the cache stays
 * exact. Only what vanilla does not cache (framebuffers, program, stencil test, clear values) is
 * queried from the driver: 6 synchronous queries per frame instead of 20.
 */
final class UpscaleGlState implements AutoCloseable {
    private final int read = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
    private final int draw = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
    private final int program = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
    private final int active = GlStateManager._getActiveTexture();
    private final int activeBinding = GlStateCache.textureBinding(active - GL13.GL_TEXTURE0);
    private final int[] textures = new int[4];
    private final int[] viewport = {GlStateManager.Viewport.x(), GlStateManager.Viewport.y(),
            GlStateManager.Viewport.width(), GlStateManager.Viewport.height()};
    private final float[] clearColor = new float[4];
    private final byte[] colorMask = new byte[4];
    private final double clearDepth = GL11.glGetDouble(GL11.GL_DEPTH_CLEAR_VALUE);
    private final boolean depth = GlStateCache.depthTest();
    private final boolean writeDepth = GlStateCache.depthMask();
    private final boolean blend = GlStateCache.blend();
    private final boolean cull = GlStateCache.cull();
    private final boolean scissor = GlStateCache.scissor();
    private final boolean stencil = GL11.glIsEnabled(GL11.GL_STENCIL_TEST);
    private final ShaderInstance shader = RenderSystem.getShader();

    UpscaleGlState() {
        GL11.glGetFloatv(GL11.GL_COLOR_CLEAR_VALUE, clearColor);
        GlStateCache.colorMask(colorMask);
        for (int i = 0; i < textures.length; i++) textures[i] = GlStateCache.textureBinding(i);
    }

    @Override
    public void close() {
        GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, read);
        GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, draw);
        RenderSystem.viewport(viewport[0], viewport[1], viewport[2], viewport[3]);
        if (depth) RenderSystem.enableDepthTest(); else RenderSystem.disableDepthTest();
        RenderSystem.depthMask(writeDepth);
        if (blend) RenderSystem.enableBlend(); else RenderSystem.disableBlend();
        if (cull) RenderSystem.enableCull(); else RenderSystem.disableCull();
        if (scissor) GlStateManager._enableScissorTest(); else RenderSystem.disableScissor();
        if (stencil) GL11.glEnable(GL11.GL_STENCIL_TEST); else GL11.glDisable(GL11.GL_STENCIL_TEST);
        RenderSystem.colorMask(colorMask[0] != 0, colorMask[1] != 0, colorMask[2] != 0, colorMask[3] != 0);
        RenderSystem.clearColor(clearColor[0], clearColor[1], clearColor[2], clearColor[3]);
        RenderSystem.clearDepth(clearDepth);
        for (int i = 0; i < textures.length; i++) {
            RenderSystem.activeTexture(GL13.GL_TEXTURE0 + i);
            GlStateManager._bindTexture(textures[i]);
        }
        RenderSystem.activeTexture(active);
        GlStateManager._bindTexture(activeBinding);
        RenderSystem.setShader(() -> shader);
        GlStateManager._glUseProgram(program);
    }
}
