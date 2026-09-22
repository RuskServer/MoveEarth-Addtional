package com.ruskserver.moveearth_addtional.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import com.ruskserver.moveearth_addtional.compat.tacz.ScopeGlintPolicy;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

/**
 * The glint a scoped rifle throws back at whoever it is pointed near.
 *
 * <p>A siege currently favours whoever lies still the longest. A scope that
 * gives away roughly where its owner is puts a cost on staying put without
 * taking the shot away: the glint says a rifle is up there, and never says
 * that it is pointed at you in particular. Moving after firing becomes worth
 * doing, which is the behaviour this is for.
 *
 * <p>Drawn entirely on the client, from state Timeless Ammunition already
 * synchronises for its own animations. Nothing new goes over the network, and
 * nothing is computed for a player nobody can see.
 *
 * <p>There is deliberately no setting to turn it off. It is a signal other
 * players are meant to act on, so a client that hid it would be a client that
 * could not be shot back at.
 */
@EventBusSubscriber(modid = Moveearth_addtional.MODID, value = Dist.CLIENT,
        bus = EventBusSubscriber.Bus.GAME)
public final class ScopeGlintRenderer {

    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
            Moveearth_addtional.MODID, "textures/effect/scope_glint.png");

    /**
     * How far a glint carries, in blocks.
     *
     * <p>Generous, because the whole point is to be seen from where the rifle
     * can reach. Short of that it would only expose snipers to people already
     * close enough to shoot back with anything.
     */
    private static final double MAX_DISTANCE = 220.0D;

    /** Apparent size, in blocks at one block away. Distance is cancelled out. */
    private static final float APPARENT_SIZE = 0.045F;

    /** Never larger than this in world units, so it cannot swallow a face up close. */
    private static final float MAX_WORLD_SIZE = 1.1F;

    /**
     * Whether Timeless Ammunition is here at all.
     *
     * <p>Resolved once and kept, because the alternative is finding out through
     * a NoClassDefFoundError thrown from inside the render loop, which takes
     * the whole world with it rather than just the glint.
     */
    private static final boolean GUNS_PRESENT = ModList.get().isLoaded("tacz");

    private ScopeGlintRenderer() { }

    @SubscribeEvent
    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
            return;
        }
        if (!GUNS_PRESENT) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) {
            return;
        }
        Camera camera = event.getCamera();
        Vec3 eye = camera.getPosition();
        float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(false);

        PoseStack poseStack = event.getPoseStack();
        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        VertexConsumer vertices = null;

        for (Player other : minecraft.level.players()) {
            if (other == minecraft.player || other.isSpectator() || other.isInvisible()) {
                continue;
            }
            Vec3 lens = other.getEyePosition(partialTick);
            double distanceSquared = lens.distanceToSqr(eye);
            if (distanceSquared > MAX_DISTANCE * MAX_DISTANCE || distanceSquared < 4.0D) {
                continue;
            }
            float strength = strengthOf(other, lens, eye, partialTick);
            if (strength <= 0.0F) {
                continue;
            }
            if (vertices == null) {
                vertices = buffers.getBuffer(RenderType.entityTranslucentEmissive(TEXTURE));
            }
            draw(poseStack, vertices, camera, eye, lens, Math.sqrt(distanceSquared), strength);
        }
        if (vertices != null) {
            buffers.endBatch(RenderType.entityTranslucentEmissive(TEXTURE));
        }
    }

    /**
     * How brightly this player's scope shows, or zero for not at all.
     *
     * <p>Strength follows how squarely the scope faces the viewer rather than
     * where the sun is. A reflection that tracked the sun would be the honest
     * model and an unreadable rule: players cannot plan around a glint that
     * depends on the time of day and the direction they happen to be lying in.
     * Facing is something they can reason about, and reasoning about it is the
     * skill this is supposed to reward.
     */
    private static float strengthOf(Player other, Vec3 lens, Vec3 eye, float partialTick) {
        if (!ScopeGlintPolicy.scopedAndAiming(other, partialTick)) {
            return 0.0F;
        }
        if (!lit(other)) {
            return 0.0F;
        }
        Vec3 toViewer = eye.subtract(lens).normalize();
        double facing = other.getViewVector(partialTick).dot(toViewer);
        if (facing <= 0.0D) {
            // Pointed away: the objective lens is not showing at all.
            return 0.0F;
        }
        // Squared so the glint is faint across the wide arc and unmistakable
        // down the barrel, without ever being absent from the arc.
        float aim = (float) (facing * facing);
        return (0.28F + 0.72F * aim) * ScopeGlintPolicy.aimingProgress(other, partialTick);
    }

    /**
     * Why this player is or is not glinting, in words.
     *
     * <p>Written here rather than in the command that prints it, because the
     * conditions are the ones the renderer actually applies. A diagnosis kept
     * anywhere else is a second copy of the rules, and the first thing a second
     * copy does is disagree with the first about the case being investigated.
     */
    public static String explain(Player other, float partialTick) {
        if (!GUNS_PRESENT) {
            return "tacz absent";
        }
        ScopeGlintPolicy.Reading reading = ScopeGlintPolicy.describe(other, partialTick);
        StringBuilder out = new StringBuilder();
        out.append(other.getGameProfile().getName()).append(": ");
        if (!reading.gun()) {
            return out.append("no gun in main hand").toString();
        }
        out.append("aim=").append(String.format(java.util.Locale.ROOT, "%.2f", reading.progress()));
        if (reading.scopeId().isBlank()) {
            return out.append(" scope=none").toString();
        }
        out.append(" scope=").append(reading.scopeId())
                .append(" zoom=").append(java.util.Arrays.toString(reading.zoom()))
                .append(" magnified=").append(reading.glints());
        if (!lit(other)) {
            var level = other.level();
            out.append(" | not lit:")
                    .append(level.dimensionType().hasSkyLight() ? "" : " no-skylight")
                    .append(level.isDay() ? "" : " night")
                    .append(level.isRaining() || level.isThundering() ? " weather" : "")
                    .append(level.canSeeSky(BlockPos.containing(other.getEyePosition()))
                            ? "" : " roofed");
            return out.toString();
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return out.toString();
        }
        if (other == minecraft.player) {
            return out.append(" | you cannot see your own glint").toString();
        }
        Vec3 lens = other.getEyePosition(partialTick);
        Vec3 eye = minecraft.player.getEyePosition(partialTick);
        double facing = other.getViewVector(partialTick).dot(eye.subtract(lens).normalize());
        return out.append(String.format(java.util.Locale.ROOT,
                " | distance=%.0f facing=%.2f strength=%.2f",
                lens.distanceTo(eye), facing, strengthOf(other, lens, eye, partialTick))).toString();
    }

    /** No glint underground, indoors, at night or in the rain: there is no sun. */
    private static boolean lit(Player other) {
        var level = other.level();
        if (!level.dimensionType().hasSkyLight() || level.isRaining() || level.isThundering()) {
            return false;
        }
        if (!level.isDay()) {
            return false;
        }
        BlockPos pos = BlockPos.containing(other.getEyePosition());
        return level.canSeeSky(pos);
    }

    private static void draw(PoseStack poseStack, VertexConsumer vertices, Camera camera,
                             Vec3 eye, Vec3 lens, double distance, float strength) {
        // Apparent size held constant: a fixed world-size sprite shrinks to
        // nothing at exactly the range this exists to cover.
        float size = Math.min(MAX_WORLD_SIZE, (float) (APPARENT_SIZE * distance));
        poseStack.pushPose();
        poseStack.translate(lens.x - eye.x, lens.y - eye.y, lens.z - eye.z);
        poseStack.mulPose(camera.rotation());
        Matrix4f matrix = poseStack.last().pose();
        int alpha = (int) (255.0F * Math.min(1.0F, strength));
        corner(vertices, matrix, -size, -size, 0.0F, 1.0F, alpha);
        corner(vertices, matrix, size, -size, 1.0F, 1.0F, alpha);
        corner(vertices, matrix, size, size, 1.0F, 0.0F, alpha);
        corner(vertices, matrix, -size, size, 0.0F, 0.0F, alpha);
        poseStack.popPose();
    }

    private static void corner(VertexConsumer vertices, Matrix4f matrix, float x, float y,
                               float u, float v, int alpha) {
        vertices.addVertex(matrix, x, y, 0.0F)
                .setColor(255, 255, 255, alpha)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(0xF000F0)
                .setNormal(0.0F, 0.0F, 1.0F);
    }
}
