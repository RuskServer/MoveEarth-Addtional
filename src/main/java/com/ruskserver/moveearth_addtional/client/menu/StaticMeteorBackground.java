package com.ruskserver.moveearth_addtional.client.menu;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;

import java.util.List;

/** Dependency-free title background: one static texture and a single batched meteor draw. */
public final class StaticMeteorBackground {
    private static final ResourceLocation BACKGROUND = ResourceLocation.fromNamespaceAndPath(
            Moveearth_addtional.MODID, "menu/static_background.png");
    private static final float SOURCE_ASPECT = 16.0F / 9.0F;
    private static final double DIAGONAL = Math.sqrt(0.5D);

    /**
     * How much of the picture is held back on each side for the pan to use.
     *
     * <p>It is spent whether the cursor moves or not, so the framing is this
     * much tighter than before: anything that has to stay in shot needs to be
     * clear of the outer three percent.
     */
    private static final float PARALLAX_MARGIN = 0.03F;
    /**
     * How far the meteors move, as a fraction of the screen.
     *
     * <p>Larger than the background's margin on purpose. Two layers that shift
     * together are one layer; it is the difference between them that reads as
     * depth, and the meteors are the nearer of the two.
     */
    private static final float METEOR_PARALLAX = 0.05F;

    private final List<MeteorShowerModel.Meteor> meteors =
            MeteorShowerModel.create(MeteorShowerModel.DEFAULT_SEED);
    private final ParallaxTracker parallax = new ParallaxTracker();

    public StaticMeteorBackground() {
    }

    public void render(GuiGraphics graphics, int width, int height) {
        render(graphics, width, height, true);
    }

    /** Renders looking straight ahead, easing back if it was panned. */
    public void render(GuiGraphics graphics, int width, int height, boolean animate) {
        parallax.recentre();
        draw(graphics, width, height, animate);
    }

    /** Renders panned towards the cursor. */
    public void render(GuiGraphics graphics, int width, int height, boolean animate,
                       double mouseX, double mouseY) {
        parallax.aim(mouseX, mouseY, width, height);
        draw(graphics, width, height, animate);
    }

    private void draw(GuiGraphics graphics, int width, int height, boolean animate) {
        long now = Util.getMillis();
        parallax.advance(now);
        drawBackground(graphics, width, height, parallax.x(), parallax.y());
        if (animate) drawMeteors(graphics, width, height, now / 1_000.0D,
                parallax.x(), parallax.y());
    }

    private static void drawBackground(GuiGraphics graphics, int width, int height,
                                       float panX, float panY) {
        float screenAspect = width / (float) Math.max(1, height);
        float u0 = 0.0F;
        float u1 = 1.0F;
        float v0 = 0.0F;
        float v1 = 1.0F;
        if (SOURCE_ASPECT > screenAspect) {
            float visible = screenAspect / SOURCE_ASPECT;
            u0 = (1.0F - visible) * 0.5F;
            u1 = 1.0F - u0;
        } else if (SOURCE_ASPECT < screenAspect) {
            float visible = SOURCE_ASPECT / screenAspect;
            v0 = (1.0F - visible) * 0.5F;
            v1 = 1.0F - v0;
        }

        // Reserve the margin inside whatever the aspect fit left, then spend it.
        // Doing it in this order means the pan can never run off the picture,
        // whatever the window shape, so no border is ever exposed.
        float[] horizontal = ParallaxTracker.pan(u0, u1, PARALLAX_MARGIN, panX);
        float[] vertical = ParallaxTracker.pan(v0, v1, PARALLAX_MARGIN, panY);
        u0 = horizontal[0];
        u1 = horizontal[1];
        v0 = vertical[0];
        v1 = vertical[1];

        graphics.flush();
        try {
            RenderSystem.disableDepthTest();
            RenderSystem.depthMask(false);
            RenderSystem.setShader(GameRenderer::getPositionTexShader);
            RenderSystem.setShaderTexture(0, BACKGROUND);
            BufferBuilder buffer = Tesselator.getInstance().begin(
                    VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
            Matrix4f matrix = graphics.pose().last().pose();
            buffer.addVertex(matrix, 0.0F, height, 0.0F).setUv(u0, v1);
            buffer.addVertex(matrix, width, height, 0.0F).setUv(u1, v1);
            buffer.addVertex(matrix, width, 0.0F, 0.0F).setUv(u1, v0);
            buffer.addVertex(matrix, 0.0F, 0.0F, 0.0F).setUv(u0, v0);
            BufferUploader.drawWithShader(buffer.buildOrThrow());
        } finally {
            RenderSystem.setShaderTexture(0, 0);
            RenderSystem.depthMask(true);
            RenderSystem.enableDepthTest();
        }
    }

    private void drawMeteors(GuiGraphics graphics, int width, int height, double elapsedSeconds,
                             float panX, float panY) {
        float scale = Math.max(1.0F, Math.min(width, height));
        graphics.flush();
        // Shifted the way the picture went, only further, so the sky has depth
        // rather than sliding as one board.
        graphics.pose().pushPose();
        graphics.pose().translate(-panX * width * METEOR_PARALLAX,
                -panY * height * METEOR_PARALLAX, 0.0F);
        try {
            RenderSystem.disableDepthTest();
            RenderSystem.depthMask(false);
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.setShader(GameRenderer::getPositionColorShader);
            BufferBuilder buffer = null;
            Matrix4f matrix = graphics.pose().last().pose();

            for (MeteorShowerModel.Meteor meteor : meteors) {
                double progress = MeteorShowerModel.progress(meteor, elapsedSeconds);
                if (progress < 0.0D) continue;
                if (buffer == null) {
                    buffer = Tesselator.getInstance().begin(
                            VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
                }
                float fade = fade((float) progress);
                float headX = (float) (meteor.startX() * width - meteor.travel() * scale * progress);
                float headY = (float) (meteor.startY() * height + meteor.travel() * scale * progress);
                float length = (float) (meteor.length() * scale);
                float meteorWidth = Math.max(0.75F, (float) (meteor.width() * scale));
                float strength = fade * (float) meteor.brightness();
                int green = 210 + (int) Math.round(meteor.colorVariation() * 40.0D);
                int blue = 145 + (int) Math.round(meteor.colorVariation() * 80.0D);

                tail(buffer, matrix, headX, headY, length * 1.16F, meteorWidth * 2.8F,
                        55, green, blue, alpha(34, strength));
                tail(buffer, matrix, headX, headY, length, meteorWidth * 1.45F,
                        80, 255, 150, alpha(112, strength));
                tail(buffer, matrix, headX, headY, length * 0.56F, meteorWidth * 0.55F,
                        220, 255, 222, alpha(230, strength));
                head(buffer, matrix, headX, headY, meteorWidth * 1.55F,
                        235, 255, 225, alpha(245, strength));
            }
            if (buffer != null) BufferUploader.drawWithShader(buffer.buildOrThrow());
        } finally {
            RenderSystem.depthMask(true);
            RenderSystem.enableDepthTest();
            RenderSystem.disableBlend();
            graphics.pose().popPose();
        }
    }

    private static float fade(float progress) {
        float fadeIn = Math.min(1.0F, progress / 0.12F);
        float fadeOut = Math.min(1.0F, (1.0F - progress) / 0.22F);
        return Math.max(0.0F, Math.min(fadeIn, fadeOut));
    }

    private static int alpha(int maximum, float strength) {
        return Math.max(0, Math.min(255, Math.round(maximum * strength)));
    }

    private static void tail(BufferBuilder buffer, Matrix4f matrix, float headX, float headY,
                             float length, float width, int red, int green, int blue, int alpha) {
        float directionX = (float) DIAGONAL;
        float directionY = (float) -DIAGONAL;
        float perpendicularX = -directionY;
        float perpendicularY = directionX;
        float halfWidth = width * 0.5F;
        float tailX = headX + directionX * length;
        float tailY = headY + directionY * length;
        float tailWidth = halfWidth * 0.08F;

        buffer.addVertex(matrix, headX - perpendicularX * halfWidth,
                headY - perpendicularY * halfWidth, 0.0F).setColor(red, green, blue, alpha);
        buffer.addVertex(matrix, headX + perpendicularX * halfWidth,
                headY + perpendicularY * halfWidth, 0.0F).setColor(red, green, blue, alpha);
        buffer.addVertex(matrix, tailX + perpendicularX * tailWidth,
                tailY + perpendicularY * tailWidth, 0.0F).setColor(red, green, blue, 0);
        buffer.addVertex(matrix, tailX - perpendicularX * tailWidth,
                tailY - perpendicularY * tailWidth, 0.0F).setColor(red, green, blue, 0);
    }

    private static void head(BufferBuilder buffer, Matrix4f matrix, float x, float y, float radius,
                             int red, int green, int blue, int alpha) {
        buffer.addVertex(matrix, x, y + radius, 0.0F).setColor(red, green, blue, alpha);
        buffer.addVertex(matrix, x + radius, y, 0.0F).setColor(red, green, blue, alpha);
        buffer.addVertex(matrix, x, y - radius, 0.0F).setColor(red, green, blue, alpha);
        buffer.addVertex(matrix, x - radius, y, 0.0F).setColor(red, green, blue, alpha);
    }
}
