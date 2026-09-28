package com.ruskserver.moveearth_addtional.client.scope;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import com.tacz.guns.api.client.gameplay.IClientPlayerGunOperator;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.IAttachment;
import com.tacz.guns.api.TimelessAPI;
import com.ruskserver.moveearth_addtional.mixin.client.TaczScopePipLayout;
import java.util.List;
import com.tacz.guns.api.item.attachment.AttachmentType;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.GraphicsStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

@EventBusSubscriber(modid = Moveearth_addtional.MODID, value = Dist.CLIENT)
public final class ScopePipRenderer {
    private static ResourceLocation activeOptic;
    private static RenderTarget lens;
    private static boolean renderingLens;
    private static boolean active;
    private static boolean ready;
    private static boolean failed;
    private static String failureReason = "render_failure_reconnect_required";
    private static double magnification = 4.25;
    private static double normalFov = Double.NaN;
    private static float aimingProgress;
    private static String reason = "idle";
    private static double outsideFov;
    private static double insideFov;
    private static boolean composited;
    /** Eligible frames seen; with a lens update interval above 1, only every Nth redraws the lens. */
    private static int lensFrame;
    /** The lens holds a finished image of {@link #lensOptic}, reusable on skipped frames. */
    private static boolean lensFresh;
    private static ResourceLocation lensOptic;
    private static int lensFilter = -1;

    private ScopePipRenderer() { }

    public static boolean isRenderingLens() { return renderingLens; }
    public static boolean overridesZoom() { return active; }
    public static RenderTarget lensTarget() { return lens; }

    public static void retry() {
        release();
        failed = false;
        active = false;
        ready = false;
        reason = "idle";
    }

    public static String diagnostic() {
        return String.format(java.util.Locale.ROOT, "PIP %s | %s | %.2fx | outside %.1f / lens %.1f | composite %s",
                active ? "ON" : "OFF", reason, magnification, outsideFov, insideFov, composited);
    }

    private static boolean reject(String value) {
        reason = value;
        return false;
    }

    private static boolean eligible(DeltaTracker timer) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!ScopePipConfig.ENABLED.get()) return reject("disabled_in_config");
        if (failed) return reject(failureReason);
        if (!ModList.get().isLoaded("tacz")) return reject("tacz_missing");
        if (ModList.get().isLoaded("oculus")) return reject("oculus_unsupported");
        String irisReason = ScopePipIrisBridge.unavailableReason();
        if (irisReason != null) return reject(irisReason);
        if (ModList.get().isLoaded("acceleratedrendering")) return reject("accelerated_rendering_installed");
        if (minecraft.options.graphicsMode().get() == GraphicsStatus.FABULOUS) return reject("fabulous_graphics");
        if (minecraft.gameRenderer.currentEffect() != null) return reject("post_effect_active");
        if (minecraft.player == null || !minecraft.player.isAlive() || minecraft.level == null
                || minecraft.screen != null || !minecraft.options.getCameraType().isFirstPerson()
                || minecraft.getCameraEntity() != minecraft.player) return reject("camera_or_screen");
        var stack = minecraft.player.getMainHandItem();
        IGun gun = IGun.getIGunOrNull(stack);
        if (gun == null) return reject("no_gun");
        var scope = gun.getAttachment(minecraft.player.registryAccess(), stack, AttachmentType.SCOPE);
        ResourceLocation id = opticId(stack, scope);
        if (id == null) return reject("no_scope");
        var index = TimelessAPI.getClientAttachmentIndex(id).orElse(null);
        if (index == null || !index.isScope()) return reject("not_scope_definition");
        var model = index.getAttachmentModel();
        if (!(model instanceof TaczScopePipLayout layout)) return reject("lens_accessor_missing");
        if (index.isSight() && layout.moveearth$ocularKinds().stream().noneMatch(Boolean.TRUE::equals)) return reject("scope_lens_missing");
        magnification = gun.getAimingZoom(stack);
        activeOptic = id;
        aimingProgress = IClientPlayerGunOperator.fromLocalPlayer(minecraft.player)
                .getClientAimingProgress(timer.getGameTimeDeltaPartialTick(false));
        boolean eligible = ScopePipMath.eligibleOptic(true, magnification, ScopePipConfig.MINIMUM_MAGNIFICATION.get(),
                aimingProgress, layout.moveearth$ocularKinds().size());
        reason = eligible ? "active" : "aim_zoom_or_mask_ineligible";
        return eligible;
    }

    private static ResourceLocation opticId(ItemStack gunStack, ItemStack scope) {
        if (scope != null && !scope.isEmpty()) {
            IAttachment attachment = IAttachment.getIAttachmentOrNull(scope);
            return attachment == null ? null : attachment.getAttachmentId(scope);
        }
        IGun gun = IGun.getIGunOrNull(gunStack);
        return gun == null ? null : gun.getBuiltInAttachmentId(gunStack, AttachmentType.SCOPE);
    }

    public static void beginFrame(DeltaTracker timer, Runnable renderWorld) {
        if (renderingLens) return;
        active = false;
        ready = false;
        composited = false;
        outsideFov = 0;
        insideFov = 0;
        normalFov = Double.NaN;
        if (!eligible(timer)) {
            lensFresh = false;
            if (!ScopePipConfig.ENABLED.get() || failed || Minecraft.getInstance().level == null
                    || reason.equals("iris_experimental_disabled")) release();
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        RenderTarget main = minecraft.getMainRenderTarget();
        int width = Math.max(1, (int) (main.width * ScopePipConfig.RESOLUTION.get()));
        int height = Math.max(1, (int) (main.height * ScopePipConfig.RESOLUTION.get()));
        try {
            main.enableStencil();
            if (lens == null || lens.width != width || lens.height != height) {
                release();
                lens = new TextureTarget(width, height, true, Minecraft.ON_OSX);
                lens.enableStencil();
            }
            applyLensFilter();
            active = true;
            if (reuseLens()) {
                ready = true;
                return;
            }
            lensFresh = false;
            renderingLens = true;
            lens.clear(Minecraft.ON_OSX);
            lens.bindWrite(true);
            ScopePipIrisBridge.renderLens(renderWorld);
            lensFresh = true;
            lensOptic = activeOptic;
            ready = true;
        } catch (RuntimeException exception) {
            failed = true;
            active = false;
            failureReason = "lens_render_failed_" + rootCauseName(exception);
            reason = failureReason;
            Moveearth_addtional.LOGGER.warn("Scope PIP disabled for this session after a render failure", exception);
        } finally {
            renderingLens = false;
            main.bindWrite(true);
        }
    }

    /** True when this frame can show the previous lens image instead of drawing the world again. */
    private static boolean reuseLens() {
        int interval = ScopePipConfig.UPDATE_INTERVAL.get();
        lensFrame = (lensFrame + 1) % 6; // divisible by every allowed interval
        return interval > 1 && lensFresh && activeOptic.equals(lensOptic) && lensFrame % interval != 0;
    }

    private static void applyLensFilter() {
        int filter = ScopePipConfig.SMOOTH_LENS.get() ? GL11.GL_LINEAR : GL11.GL_NEAREST;
        if (filter == lensFilter) return;
        lens.setFilterMode(filter);
        lensFilter = filter;
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void captureNormalFov(ViewportEvent.ComputeFov event) {
        if (active && event.usedConfiguredFov()) normalFov = event.getFOV();
    }

    public static double worldFov(double original) {
        if (!active || !Double.isFinite(normalFov)) return original;
        double zoom = 1 + (magnification - 1) * Math.clamp(aimingProgress, 0, 1);
        double result = renderingLens ? ScopePipMath.lensFov(normalFov, zoom) : normalFov;
        if (renderingLens) insideFov = result; else outsideFov = result;
        return result;
    }

    public static void composite(ItemStack gunStack, ItemStack scope, List<Boolean> kinds, boolean selective) {
        if (!active || !ready || renderingLens || lens == null) return;
        if (activeOptic == null || !activeOptic.equals(opticId(gunStack, scope)) || kinds.isEmpty() || kinds.size() > 127) return;
        boolean stencilEnabled = GL11.glIsEnabled(GL11.GL_STENCIL_TEST);
        if (!stencilEnabled || stencilBits() == 0) {
            failed = true;
            active = false;
            failureReason = stencilEnabled ? "stencil_buffer_unavailable" : "stencil_test_disabled";
            reason = failureReason;
            Moveearth_addtional.LOGGER.warn("Scope PIP disabled: lens stencil buffer is unavailable");
            return;
        }
        int stencilFunction = GL11.glGetInteger(GL11.GL_STENCIL_FUNC);
        int stencilReference = GL11.glGetInteger(GL11.GL_STENCIL_REF);
        int stencilMask = GL11.glGetInteger(GL11.GL_STENCIL_VALUE_MASK);
        boolean depth = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        boolean blend = GL11.glIsEnabled(GL11.GL_BLEND);
        try {
            RenderSystem.disableBlend();
            var main = Minecraft.getInstance().getMainRenderTarget();
            for (int index = 0; index < kinds.size(); index++) {
                if (selective && !Boolean.TRUE.equals(kinds.get(index))) continue;
                RenderSystem.stencilFunc(GL11.GL_EQUAL, index + 1, 0xFF);
                lens.blitToScreen(main.width, main.height, false);
                composited = true;
            }
        } catch (RuntimeException exception) {
            failed = true;
            active = false;
            failureReason = "lens_composite_failed_" + rootCauseName(exception);
            reason = failureReason;
            Moveearth_addtional.LOGGER.warn("Scope PIP disabled after a lens composite failure", exception);
        } finally {
            RenderSystem.stencilFunc(stencilFunction, stencilReference, stencilMask);
            if (depth) RenderSystem.enableDepthTest(); else RenderSystem.disableDepthTest();
            if (blend) RenderSystem.enableBlend(); else RenderSystem.disableBlend();
        }
    }

    private static int stencilBits() {
        int framebuffer = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        int attachment = framebuffer == 0 ? GL11.GL_STENCIL : GL30.GL_STENCIL_ATTACHMENT;
        int type = GL30.glGetFramebufferAttachmentParameteri(GL30.GL_DRAW_FRAMEBUFFER, attachment,
                GL30.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_TYPE);
        if (type == GL11.GL_NONE) return 0;
        return GL30.glGetFramebufferAttachmentParameteri(GL30.GL_DRAW_FRAMEBUFFER, attachment,
                GL30.GL_FRAMEBUFFER_ATTACHMENT_STENCIL_SIZE);
    }

    private static String rootCauseName(Throwable exception) {
        Throwable cause = exception;
        for (int depth = 0; depth < 16 && cause.getCause() != null && cause.getCause() != cause; depth++) {
            cause = cause.getCause();
        }
        return cause.getClass().getSimpleName();
    }

    private static void release() {
        lensFresh = false;
        lensOptic = null;
        lensFilter = -1;
        ScopePipIrisBridge.release();
        if (lens != null) {
            lens.destroyBuffers();
            lens = null;
        }
    }

    @SubscribeEvent
    public static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
        active = false;
        ready = false;
        failed = false;
        release();
    }
}
