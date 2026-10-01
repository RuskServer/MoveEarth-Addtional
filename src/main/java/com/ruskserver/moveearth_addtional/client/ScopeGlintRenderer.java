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
     * Additive and emissive, the way a mob's glowing eyes are drawn.
     *
     * <p>Translucent was the first choice and it only read at night: laying a
     * half-transparent sprite over a bright sky subtracts as much as it adds,
     * so the thing meant to be a highlight disappeared in exactly the daylight
     * that is supposed to cause it. Additive puts light in instead of painting
     * over, which is also what a reflection does.
     *
     * <p>It still tests depth, so a rifle behind a wall does not shine through
     * it.
     */
    private static final RenderType GLINT = RenderType.eyes(TEXTURE);

    /**
     * How far a glint carries, in blocks.
     *
     * <p>Generous, because the whole point is to be seen from where the rifle
     * can reach. Short of that it would only expose snipers to people already
     * close enough to shoot back with anything.
     */
    private static final double MAX_DISTANCE = 220.0D;

    /** Apparent half-size, in blocks at one block away. Distance is cancelled out. */
    private static final float APPARENT_SIZE = 0.008F;

    /**
     * How far in front of the eyes the lens sits, in blocks.
     *
     * <p>Drawn at the eye position it was inside the head model, which reads as
     * a sliver appearing behind someone's skull rather than as a rifle scope.
     * Pushed towards whoever is looking, so it clears the model from every
     * angle without having to know which way the head is turned.
     */
    private static final double LENS_OFFSET = 0.45D;

    /** Prevent the distant glint from growing into a large sprite. */
    private static final float MAX_WORLD_SIZE = 0.55F;

    /**
     * Whether Timeless Ammunition is here at all.
     *
     * <p>Resolved once and kept, because the alternative is finding out through
     * a NoClassDefFoundError thrown from inside the render loop, which takes
     * the whole world with it rather than just the glint.
     */
    private static final boolean GUNS_PRESENT = ModList.get().isLoaded("tacz");

    /**
     * Whether each player's held gun carries a magnified optic, keyed by the exact stack held.
     *
     * <p>Reading attachments and the optic index is the expensive part of the check and its answer
     * only changes with the gun, so it is looked up once per held stack instead of every frame. A
     * changed attachment arrives as a new stack; the expiry is a backstop for in-place edits.
     */
    private static final java.util.Map<java.util.UUID, OpticCache> OPTICS = new java.util.HashMap<>();
    private static final long OPTIC_CACHE_MILLIS = 2_000L;
    private static final long OPTIC_CACHE_PRUNE_MILLIS = 10_000L;
    private static long lastPrune;

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
        long now = net.minecraft.Util.getMillis();
        pruneOptics(now);

        for (Player other : minecraft.level.players()) {
            if (other == minecraft.player || other.isSpectator() || other.isInvisible()) {
                continue;
            }
            Vec3 lens = other.getEyePosition(partialTick);
            double distanceSquared = lens.distanceToSqr(eye);
            if (distanceSquared > MAX_DISTANCE * MAX_DISTANCE || distanceSquared < 4.0D) {
                continue;
            }
            float strength = strengthOf(other, lens, eye, partialTick, now);
            if (strength <= 0.0F) {
                continue;
            }
            if (vertices == null) {
                vertices = buffers.getBuffer(GLINT);
            }
            draw(poseStack, vertices, camera, eye, lens, Math.sqrt(distanceSquared), strength);
        }
        if (vertices != null) {
            buffers.endBatch(GLINT);
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
    private static float strengthOf(Player other, Vec3 lens, Vec3 eye, float partialTick, long now) {
        // Cheapest test first: almost every player in range is not aiming at all, and for them
        // nothing about the gun or its attachments needs to be read.
        float progress = ScopeGlintPolicy.aimingProgress(other, partialTick);
        if (progress <= 0.0F) {
            return 0.0F;
        }
        Vec3 toViewer = eye.subtract(lens).normalize();
        double facing = other.getViewVector(partialTick).dot(toViewer);
        if (facing <= 0.65D) {
            return 0.0F;
        }
        if (!lit(other) || !magnifiedOptic(other, partialTick, now)) {
            return 0.0F;
        }
        float alignment = (float) ((facing - 0.65D) / 0.35D);
        return 0.45F * alignment * alignment * progress;
    }

    /** Called only while the player is aiming, which is when {@code describe} reports the optic alone. */
    private static boolean magnifiedOptic(Player other, float partialTick, long now) {
        net.minecraft.world.item.ItemStack held = other.getMainHandItem();
        OpticCache cached = OPTICS.get(other.getUUID());
        if (cached != null && cached.stack == held && now - cached.checkedAt < OPTIC_CACHE_MILLIS) {
            return cached.magnified;
        }
        boolean magnified = ScopeGlintPolicy.describe(other, partialTick).glints();
        OPTICS.put(other.getUUID(), new OpticCache(held, magnified, now));
        return magnified;
    }

    private static void pruneOptics(long now) {
        if (now - lastPrune < OPTIC_CACHE_PRUNE_MILLIS) {
            return;
        }
        lastPrune = now;
        OPTICS.values().removeIf(cache -> now - cache.checkedAt > OPTIC_CACHE_PRUNE_MILLIS);
    }

    private record OpticCache(net.minecraft.world.item.ItemStack stack, boolean magnified, long checkedAt) { }

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
                lens.distanceTo(eye), facing,
                strengthOf(other, lens, eye, partialTick, net.minecraft.Util.getMillis()))).toString();
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
        Vec3 front = lens.add(eye.subtract(lens).normalize().scale(LENS_OFFSET));
        poseStack.pushPose();
        poseStack.translate(front.x - eye.x, front.y - eye.y, front.z - eye.z);
        poseStack.mulPose(camera.rotation());
        Matrix4f matrix = poseStack.last().pose();
        int alpha = (int) (255.0F * Math.min(1.0F, strength));
        corner(vertices, matrix, -size, -size, 0.0F, 1.0F, alpha);
        corner(vertices, matrix, size, -size, 1.0F, 1.0F, alpha);
        corner(vertices, matrix, size, size, 1.0F, 0.0F, alpha);
        corner(vertices, matrix, -size, size, 0.0F, 0.0F, alpha);
        // The same quad wound the other way. Whether a render type culls back
        // faces is not something to be sure of from the outside, and a glint
        // that is silently facing away is indistinguishable from one that was
        // never drawn -- which is exactly how long this took to find.
        corner(vertices, matrix, -size, size, 0.0F, 0.0F, alpha);
        corner(vertices, matrix, size, size, 1.0F, 0.0F, alpha);
        corner(vertices, matrix, size, -size, 1.0F, 1.0F, alpha);
        corner(vertices, matrix, -size, -size, 0.0F, 1.0F, alpha);
        poseStack.popPose();
    }

    private static void corner(VertexConsumer vertices, Matrix4f matrix, float x, float y,
                               float u, float v, int alpha) {
        vertices.addVertex(matrix, x, y, 0.0F)
                .setColor(210, 225, 240, alpha)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(0xF000F0)
                .setNormal(0.0F, 0.0F, 1.0F);
    }
}
