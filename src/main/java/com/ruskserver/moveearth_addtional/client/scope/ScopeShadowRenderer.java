package com.ruskserver.moveearth_addtional.client.scope;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexSorting;
import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import com.ruskserver.moveearth_addtional.client.upscale.GlStateCache;
import com.ruskserver.moveearth_addtional.client.upscale.UpscaleIrisState;
import com.tacz.guns.api.client.gameplay.IClientPlayerGunOperator;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.client.model.bedrock.BedrockCube;
import com.tacz.guns.client.model.bedrock.BedrockPart;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

import java.util.List;

/**
 * Draws the riflescope shadow and lens-edge colour fringe ({@link ScopeShadowMath}) over a TaCZ scope's
 * eyepiece, right after TaCZ has stencilled it. Works whether or not Scope PIP is on.
 *
 * <p>With an Iris shader pack the eyepiece pass draws into the pack's own buffers and its stencil is gone
 * by the time the picture reaches the screen, so the shadow would be lost, and turning shaders on would
 * remove it. There the lens is only measured during the pass, and the shadow is drawn once the pack has
 * finished the frame ({@link #flushDeferred}), cut to the lens circle instead of the stencil.
 *
 * <p>The eyepiece's place and size on screen come from its own geometry: its cubes are run through the
 * pose TaCZ is drawing with and projected, so sway, walking bob, recoil and the raise animation all move
 * the measured lens, and that movement is the misalignment the shadow shows.
 */
@EventBusSubscriber(modid = Moveearth_addtional.MODID, value = Dist.CLIENT)
public final class ScopeShadowRenderer {
    private static final ResourceLocation SHADER = ResourceLocation.fromNamespaceAndPath(
            Moveearth_addtional.MODID, "scope_shadow");
    private static final double SHADOW_STRENGTH = 1.0;
    private static ShaderInstance shader;
    private static boolean failed;
    private static RenderTarget scene;
    private static double lagX;
    private static double lagY;
    private static float lastYaw = Float.NaN;
    private static float lastPitch = Float.NaN;
    private static long lastNanos;
    private static final int[] STENCIL_OPS = new int[3];
    /** Lenses measured this frame under a shader pack, drawn when the frame is finished. */
    private static final java.util.Map<Integer, Deferred> DEFERRED = new java.util.TreeMap<>();
    private static long frame;
    private static long deferredFrame = -1L;

    private record Deferred(float[] lens, ScopeShadowMath.Shadow shadow, float aberration) { }

    private ScopeShadowRenderer() { }

    @EventBusSubscriber(modid = Moveearth_addtional.MODID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
    public static final class Registration {
        private Registration() { }

        @SubscribeEvent
        public static void registerShader(RegisterShadersEvent event) {
            shader = null;
            failed = false;
            try {
                event.registerShader(new ShaderInstance(event.getResourceProvider(), SHADER,
                        DefaultVertexFormat.POSITION), loaded -> shader = loaded);
            } catch (Exception exception) {
                failed = true;
                Moveearth_addtional.LOGGER.warn("[MoveEarth] Scope shadow shader unavailable ({})",
                        exception.getClass().getSimpleName());
            }
        }
    }

    /** Called at the end of TaCZ's eyepiece pass, with its stencil still holding the eyepieces. */
    public static void render(PoseStack pose, List<List<BedrockPart>> ocularPaths, List<Boolean> scopeOculars,
                              ItemStack gunStack) {
        if (failed || shader == null || ScopePipRenderer.isRenderingLens()) return;
        // The shadow costs a scoped player some of the view, so it is the same for everyone and not a
        // setting: turning it down would only be an edge. The fringe is cosmetic and stays adjustable.
        double strength = SHADOW_STRENGTH;
        double aberration = ScopePipConfig.ABERRATION.get();
        if (ocularPaths == null || scopeOculars == null || ocularPaths.size() != scopeOculars.size()) return;
        if (ModList.get().isLoaded("acceleratedrendering")) return;
        boolean deferred = UpscaleIrisState.current() == UpscaleIrisState.State.ACTIVE;
        // The pack's shadow-map pass is not the picture on screen.
        if (deferred && com.tacz.guns.compat.iris.IrisCompat.isRenderShadow()) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) return;
        IGun gun = IGun.getIGunOrNull(gunStack);
        if (gun == null) return;
        try {
            float partial = minecraft.getTimer().getGameTimeDeltaPartialTick(false);
            double aim = IClientPlayerGunOperator.fromLocalPlayer(minecraft.player).getClientAimingProgress(partial);
            if (aim <= 0.01) return;
            double magnification = gun.getAimingZoom(gunStack);
            updateLag(minecraft, partial, magnification);
            RenderTarget main = minecraft.getMainRenderTarget();
            for (int index = 0; index < ocularPaths.size(); index++) {
                if (!Boolean.TRUE.equals(scopeOculars.get(index))) continue;
                float[] lens = measure(pose, ocularPaths.get(index), main.width, main.height);
                if (lens == null) continue;
                ScopeShadowMath.Shadow shadow = ScopeShadowMath.shadow(lens[0], lens[1], lens[2],
                        main.width / 2.0, main.height / 2.0, lagX, lagY, magnification, aim, strength);
                if (deferred) {
                    // A pack may draw the hand more than once a frame; the last measurement wins.
                    if (deferredFrame != frame) {
                        DEFERRED.clear();
                        deferredFrame = frame;
                    }
                    DEFERRED.put(index, new Deferred(lens, shadow, (float) aberration));
                } else {
                    draw(main, index, lens, shadow, (float) aberration, true);
                }
            }
        } catch (RuntimeException | LinkageError exception) {
            failed = true;
            Moveearth_addtional.LOGGER.warn("[MoveEarth] Scope shadow disabled for this session after a failure",
                    exception);
        }
    }

    /** Called once the world, hand and any shader pack's final pass are on the screen. */
    public static void flushDeferred() {
        frame++;
        if (DEFERRED.isEmpty()) return;
        try {
            if (!failed && shader != null && deferredFrame == frame - 1) {
                RenderTarget main = Minecraft.getInstance().getMainRenderTarget();
                main.bindWrite(true);
                boolean stencilTest = GL11.glIsEnabled(GL11.GL_STENCIL_TEST);
                GL11.glDisable(GL11.GL_STENCIL_TEST);
                try {
                    for (var entry : DEFERRED.entrySet()) {
                        Deferred pending = entry.getValue();
                        draw(main, entry.getKey(), pending.lens(), pending.shadow(), pending.aberration(), false);
                    }
                } finally {
                    if (stencilTest) GL11.glEnable(GL11.GL_STENCIL_TEST);
                }
            }
        } catch (RuntimeException | LinkageError exception) {
            failed = true;
            Moveearth_addtional.LOGGER.warn("[MoveEarth] Scope shadow disabled for this session after a failure",
                    exception);
        } finally {
            DEFERRED.clear();
        }
    }

    private static void updateLag(Minecraft minecraft, float partial, double magnification) {
        long now = System.nanoTime();
        float yaw = minecraft.player.getViewYRot(partial);
        float pitch = minecraft.player.getViewXRot(partial);
        double seconds = lastNanos == 0L ? 0.0 : (now - lastNanos) / 1.0e9;
        if (seconds > 0.0005 || Float.isNaN(lastYaw)) {
            // A long gap (menu, first frame) is not a turn.
            boolean fresh = Float.isNaN(lastYaw) || seconds > 0.25;
            double right = fresh ? 0.0 : wrapDegrees(yaw - lastYaw);
            double up = fresh ? 0.0 : lastPitch - pitch; // pitch grows looking down
            double[] lag = ScopeShadowMath.lag(lagX, lagY, right, up, fresh ? 1.0 : seconds, magnification);
            lagX = lag[0];
            lagY = lag[1];
            lastYaw = yaw;
            lastPitch = pitch;
            lastNanos = now;
        }
    }

    private static double wrapDegrees(double degrees) {
        double wrapped = degrees % 360.0;
        if (wrapped >= 180.0) wrapped -= 360.0;
        if (wrapped < -180.0) wrapped += 360.0;
        return wrapped;
    }

    /** The eyepiece's centre and radius in framebuffer pixels (origin bottom left), or null when off screen. */
    private static float[] measure(PoseStack pose, List<BedrockPart> path, int width, int height) {
        if (path == null || path.isEmpty()) return null;
        Matrix4f projection = new Matrix4f(RenderSystem.getProjectionMatrix());
        Bounds bounds = new Bounds(projection, width, height);
        pose.pushPose();
        try {
            // The same transform TaCZ uses to find the eyepiece centre.
            pose.last().pose().mulLocal(RenderSystem.getModelViewMatrix());
            for (BedrockPart part : path) part.translateAndRotateAndScale(pose);
            capture(pose, path.getLast(), bounds);
        } finally {
            pose.popPose();
        }
        return bounds.result();
    }

    private static void capture(PoseStack pose, BedrockPart part, Bounds bounds) {
        for (BedrockCube cube : part.cubes) cube.compile(pose.last(), bounds, 0, 0, 1F, 1F, 1F, 1F);
        for (BedrockPart child : part.children) {
            pose.pushPose();
            child.translateAndRotateAndScale(pose);
            capture(pose, child, bounds);
            pose.popPose();
        }
    }

    private static void draw(RenderTarget main, int index, float[] lens, ScopeShadowMath.Shadow shadow,
                             float aberration, boolean stencilled) {
        int minX = Math.max(0, (int) Math.floor(lens[0] - lens[2]) - 2);
        int minY = Math.max(0, (int) Math.floor(lens[1] - lens[2]) - 2);
        int maxX = Math.min(main.width, (int) Math.ceil(lens[0] + lens[2]) + 2);
        int maxY = Math.min(main.height, (int) Math.ceil(lens[1] + lens[2]) + 2);
        if (maxX <= minX || maxY <= minY) return;

        if (scene == null || scene.width != main.width || scene.height != main.height) {
            if (scene != null) scene.destroyBuffers();
            scene = new TextureTarget(main.width, main.height, false, Minecraft.ON_OSX);
            scene.setFilterMode(GL11.GL_LINEAR);
        }
        // Copy only the eyepiece's square: the fringe reads the picture it is about to replace.
        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, main.frameBufferId);
        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, scene.frameBufferId);
        GL30.glBlitFramebuffer(minX, minY, maxX, maxY, minX, minY, maxX, maxY,
                GL11.GL_COLOR_BUFFER_BIT, GL11.GL_NEAREST);
        main.bindWrite(false);

        int stencilFunction = GlStateCache.stencilFunc();
        int stencilReference = GlStateCache.stencilRef();
        int stencilMask = GlStateCache.stencilValueMask();
        GlStateCache.stencilOps(STENCIL_OPS);
        boolean depth = GlStateCache.depthTest();
        boolean depthMask = GlStateCache.depthMask();
        boolean blend = GlStateCache.blend();
        RenderSystem.backupProjectionMatrix();
        var modelView = RenderSystem.getModelViewStack();
        modelView.pushMatrix();
        try {
            modelView.identity();
            RenderSystem.applyModelViewMatrix();
            RenderSystem.setProjectionMatrix(new Matrix4f().setOrtho(0, main.width, 0, main.height, -1, 1),
                    VertexSorting.ORTHOGRAPHIC_Z);
            RenderSystem.disableBlend();
            RenderSystem.disableDepthTest();
            RenderSystem.depthMask(false);
            RenderSystem.stencilOp(GL11.GL_KEEP, GL11.GL_KEEP, GL11.GL_KEEP);
            ShaderInstance instance = shader;
            RenderSystem.setShader(() -> instance);
            instance.setSampler("Scene", scene);
            instance.safeGetUniform("ScreenSize").set((float) main.width, (float) main.height);
            instance.safeGetUniform("LensCenter").set(lens[0], lens[1]);
            instance.safeGetUniform("LensRadius").set(lens[2]);
            instance.safeGetUniform("ClearCenter").set(shadow.clearX(), shadow.clearY());
            instance.safeGetUniform("ClearRadius").set(shadow.clearRadius());
            instance.safeGetUniform("Softness").set(shadow.softness());
            instance.safeGetUniform("Darkness").set(shadow.darkness());
            instance.safeGetUniform("Aberration").set(aberration);
            instance.safeGetUniform("CircleMask").set(stencilled ? 0.0F : 1.0F);
            if (!stencilled) {
                quad(minX, minY, maxX, maxY);
                return;
            }
            // TaCZ marks the eyepiece i+1 and inverts the bits inside its scope-view circle.
            int marked = index + 1;
            for (int value : new int[] {marked, ~marked & 0xFF}) {
                RenderSystem.stencilFunc(GL11.GL_EQUAL, value, 0xFF);
                quad(minX, minY, maxX, maxY);
            }
        } finally {
            modelView.popMatrix();
            RenderSystem.applyModelViewMatrix();
            RenderSystem.restoreProjectionMatrix();
            RenderSystem.stencilFunc(stencilFunction, stencilReference, stencilMask);
            RenderSystem.stencilOp(STENCIL_OPS[0], STENCIL_OPS[1], STENCIL_OPS[2]);
            RenderSystem.depthMask(depthMask);
            if (depth) RenderSystem.enableDepthTest(); else RenderSystem.disableDepthTest();
            if (blend) RenderSystem.enableBlend(); else RenderSystem.disableBlend();
            RenderSystem.setShader(GameRenderer::getPositionColorShader);
        }
    }

    private static void quad(int minX, int minY, int maxX, int maxY) {
        BufferBuilder buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION);
        buffer.addVertex(minX, minY, 0F);
        buffer.addVertex(maxX, minY, 0F);
        buffer.addVertex(maxX, maxY, 0F);
        buffer.addVertex(minX, maxY, 0F);
        BufferUploader.drawWithShader(buffer.buildOrThrow());
    }

    @SubscribeEvent
    public static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
        DEFERRED.clear();
        failed = false;
        lagX = 0.0;
        lagY = 0.0;
        lastYaw = Float.NaN;
        lastNanos = 0L;
        if (scene != null) {
            scene.destroyBuffers();
            scene = null;
        }
    }

    /** Collects projected vertices; every attribute but the position is ignored. */
    private static final class Bounds implements VertexConsumer {
        private final Matrix4f projection;
        private final int width;
        private final int height;
        private final Vector4f point = new Vector4f();
        private float minX = Float.POSITIVE_INFINITY;
        private float minY = Float.POSITIVE_INFINITY;
        private float maxX = Float.NEGATIVE_INFINITY;
        private float maxY = Float.NEGATIVE_INFINITY;

        Bounds(Matrix4f projection, int width, int height) {
            this.projection = projection;
            this.width = width;
            this.height = height;
        }

        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            projection.transform(point.set(x, y, z, 1F));
            if (point.w > 1.0e-4F) {
                float px = (point.x / point.w * 0.5F + 0.5F) * width;
                float py = (point.y / point.w * 0.5F + 0.5F) * height;
                minX = Math.min(minX, px);
                minY = Math.min(minY, py);
                maxX = Math.max(maxX, px);
                maxY = Math.max(maxY, py);
            }
            return this;
        }

        float[] result() {
            if (!(maxX > minX) || !(maxY > minY)) return null;
            float radius = Math.max(maxX - minX, maxY - minY) / 2F;
            if (radius < 2F || radius > Math.max(width, height)) return null;
            return new float[] {(minX + maxX) / 2F, (minY + maxY) / 2F, radius};
        }

        @Override public VertexConsumer setColor(int red, int green, int blue, int alpha) { return this; }
        @Override public VertexConsumer setUv(float u, float v) { return this; }
        @Override public VertexConsumer setUv1(int u, int v) { return this; }
        @Override public VertexConsumer setUv2(int u, int v) { return this; }
        @Override public VertexConsumer setNormal(float x, float y, float z) { return this; }
    }
}
