package com.ruskserver.moveearth_addtional.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import com.ruskserver.moveearth_addtional.item.ModItems;
import com.ruskserver.moveearth_addtional.s2.reinforcement.ReinforcementVisualStyle;
import com.ruskserver.moveearth_addtional.s2.reinforcement.ReinforcementBrushPattern;
import com.ruskserver.moveearth_addtional.s2.reinforcement.ReinforcementGreedyMesher;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.debug.DebugRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import org.joml.Quaternionf;
import org.joml.Vector3d;
import org.joml.Vector3dc;

@EventBusSubscriber(modid = Moveearth_addtional.MODID, bus = EventBusSubscriber.Bus.GAME, value = Dist.CLIENT)
public final class ReinforcementOverlayRenderer {
    private static final int MAX_RENDER_QUADS = ReinforcementRenderBudget.INSTANCED_FACE_CAPACITY;
    private static final ReinforcementGreedyMesher.Face[] FACES = ReinforcementGreedyMesher.Face.values();
    private static final double MAX_RENDER_DISTANCE = 68.0D;
    private static final double MAX_RENDER_DISTANCE_SQUARED = MAX_RENDER_DISTANCE * MAX_RENDER_DISTANCE;
    /** Main-thread time per frame for remeshing chunks changed by a snapshot or delta. */
    private static final long REBUILD_BUDGET_NANOS = 2_000_000L;
    private static final int STRIDE = ReinforcementClientState.FACE_STRIDE;
    private static final float[] INSTANCE_POSITIONS = new float[MAX_RENDER_QUADS * 3];
    private static final float[] INSTANCE_SCALES = new float[MAX_RENDER_QUADS * 3];
    private static final float[] INSTANCE_COLORS = new float[MAX_RENDER_QUADS * 4];
    private static final int[] INSTANCE_FACES = new int[MAX_RENDER_QUADS];
    // Per-frame scratch, reused so a frame allocates nothing per face.
    private static long[] chunkOrder = new long[64];
    private static ReinforcementClientState.ChunkBucket[] chunkSlots = new ReinforcementClientState.ChunkBucket[64];
    private static ClientSubLevel[] chunkSubLevels = new ClientSubLevel[64];
    /** RGBA per visual style for this frame; pulses are evaluated once per style, not per face. */
    private static float[] styleColors = new float[64];
    private static final java.util.IdentityHashMap<ClientSubLevel, Pose3dc> POSES = new java.util.IdentityHashMap<>();
    private static final Vector3d LOCAL = new Vector3d();
    private static final Vector3d WORLD = new Vector3d();

    private ReinforcementOverlayRenderer() {
    }

    @SubscribeEvent
    public static void renderWorld(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        Minecraft minecraft = Minecraft.getInstance();
        boolean holdingWelder = minecraft.player != null
                && minecraft.player.getMainHandItem().is(ModItems.WELDING_TOOL.get());
        if (!holdingWelder || !validWorld(minecraft) || !ReinforcementClientState.allowed()) return;
        ReinforcementClientState.processDirty(REBUILD_BUDGET_NANOS);

        PoseStack poseStack = event.getPoseStack();
        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        Vec3 camera = event.getCamera().getPosition();
        boolean detailed = ReinforcementClientState.overlayActive();
        boolean instanced = ReinforcementGl45Renderer.available();
        int renderLimit = ReinforcementRenderBudget.faceLimit(instanced, detailed);
        float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        updateStyleColors(detailed, Util.getMillis());

        POSES.clear();
        int chunkCount = collectChunks(event.getFrustum(), camera, partialTick);
        int rendered = 0;
        int movingRendered = 0;
        for (int order = 0; order < chunkCount && rendered + movingRendered < renderLimit; order++) {
            int slot = ReinforcementRenderBudget.index(chunkOrder[order]);
            ReinforcementClientState.ChunkBucket chunk = chunkSlots[slot];
            ClientSubLevel subLevel = chunkSubLevels[slot];
            if (subLevel == null) {
                rendered = collectStaticFaces(chunk, camera, rendered, renderLimit - movingRendered);
            } else {
                movingRendered += renderMovingChunk(poseStack, buffers, chunk, POSES.get(subLevel), camera,
                        renderLimit - rendered - movingRendered);
            }
        }
        java.util.Arrays.fill(chunkSlots, 0, chunkCount, null);
        java.util.Arrays.fill(chunkSubLevels, 0, chunkCount, null);
        POSES.clear();

        boolean drawn = instanced && ReinforcementGl45Renderer.render(poseStack, camera.x, camera.y, camera.z,
                INSTANCE_POSITIONS, INSTANCE_SCALES, INSTANCE_COLORS, INSTANCE_FACES, rendered);
        if (!drawn && rendered > 0) {
            // Faces are nearest-first, so the cap drops the far end of the overlay.
            renderCompatibility(poseStack, buffers,
                    Math.min(rendered, ReinforcementRenderBudget.COMPATIBILITY_FACE_LIMIT));
        }

        BlockHitResult targetHit = targetHit(minecraft);
        BlockPos target = targetHit == null ? null : targetHit.getBlockPos();
        if (target != null && minecraft.level != null) {
            poseStack.pushPose();
            applyLocalSpace(poseStack, target, camera, event.getPartialTick().getGameTimeDeltaPartialTick(false));
            Direction clickedFace = targetHit.getDirection();
            ReinforcementBrushPattern.Axis axis = switch (targetHit.getDirection().getAxis()) {
                case X -> ReinforcementBrushPattern.Axis.X;
                case Y -> ReinforcementBrushPattern.Axis.Y;
                case Z -> ReinforcementBrushPattern.Axis.Z;
            };
            for (ReinforcementBrushPattern.Offset offset : ReinforcementBrushPattern.offsets(
                    axis, WeldingBrushClientState.radius())) {
                BlockPos selected = target.offset(offset.x(), offset.y(), offset.z());
                if (minecraft.level.getBlockState(selected).isAir()) continue;
                ReinforcementClientState.Entry selectedEntry = ReinforcementClientState.at(selected);
                float red = selectedEntry == null || selectedEntry.siegeDisabled() ? 1.0F : !selectedEntry.enabled() ? 0.72F
                        : selectedEntry.constructionInProgress() ? 1.0F : 0.16F;
                float green = selectedEntry == null || selectedEntry.siegeDisabled() ? 0.16F : !selectedEntry.enabled() ? 0.27F
                        : selectedEntry.constructionInProgress() ? 0.63F : 1.0F;
                float blue = selectedEntry == null || selectedEntry.siegeDisabled() ? 0.12F : !selectedEntry.enabled() ? 1.0F
                        : selectedEntry.constructionInProgress() ? 0.12F : 0.30F;
                DebugRenderer.renderFilledBox(poseStack, buffers,
                        new AABB(selected.subtract(target)).inflate(0.008D),
                        red, green, blue, detailed ? 0.31F : 0.22F);
            }
            AABB faceBounds = selectionFaceBounds(BlockPos.ZERO, clickedFace, WeldingBrushClientState.radius())
                    ;
            VertexConsumer outline = buffers.getBuffer(RenderType.lines());
            LevelRenderer.renderLineBox(poseStack, outline, faceBounds.inflate(0.012D),
                    0.05F, 0.32F, 0.06F, 1.0F);
            LevelRenderer.renderLineBox(poseStack, outline, faceBounds,
                    0.22F, 1.0F, 0.30F, 1.0F);
            poseStack.popPose();
        }
        if (detailed && target != null && minecraft.level != null
                && !minecraft.level.getBlockState(target).isAir()) {
            poseStack.pushPose();
            applyLocalSpace(poseStack, target, camera, event.getPartialTick().getGameTimeDeltaPartialTick(false));
            var entry = ReinforcementClientState.at(target);
            boolean reinforced = entry != null;
            int targetColor = entry != null && entry.siegeDisabled() ? 0xFFFF3D30
                    : entry != null && !entry.enabled() ? 0xFFC67AFF
                    : reinforced ? 0xFF68E09B : 0xFFFF6577;
            DebugRenderer.renderFilledBox(poseStack, buffers,
                    new AABB(BlockPos.ZERO).inflate(0.012D),
                    entry != null && entry.siegeDisabled() ? 1.0F : reinforced ? 0.50F : 1.0F,
                    entry != null && entry.siegeDisabled() ? 0.18F
                            : entry != null && !entry.enabled() ? 0.28F : reinforced ? 0.94F : 0.30F,
                    entry != null && entry.siegeDisabled() ? 0.12F
                            : entry != null && !entry.enabled() ? 0.92F : reinforced ? 0.63F : 0.22F, 0.24F);
            String marker = entry == null ? "×" : progressBar(entry) + " " + Math.round(progress(entry) * 100.0F) + "%";
            poseStack.popPose();
            Vec3 markerPos = new Vec3(target.getX() + 0.5D, target.getY() + 1.18D, target.getZ() + 0.5D);
            ClientSubLevel markerBody = Sable.HELPER.getContainingClient(target);
            if (markerBody != null) markerPos = markerBody.renderPose(
                    event.getPartialTick().getGameTimeDeltaPartialTick(false)).transformPosition(markerPos);
            DebugRenderer.renderFloatingText(poseStack, buffers, marker,
                    markerPos.x, markerPos.y, markerPos.z,
                    targetColor, 0.024F, true, 0.0F, true);
        }
        buffers.endBatch(RenderType.debugFilledBox());
        buffers.endBatch();
    }

    @SubscribeEvent
    public static void renderHud(RenderGuiEvent.Post event) {
        if (Minecraft.getInstance().screen != null) return;
        renderHudPanel(event.getGuiGraphics());
    }

    @SubscribeEvent
    public static void renderHudAboveChat(ScreenEvent.Render.Post event) {
        if (!(event.getScreen() instanceof ChatScreen)) return;
        renderHudPanel(event.getGuiGraphics());
    }

    private static void renderHudPanel(GuiGraphics graphics) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!validWorld(minecraft) || minecraft.options.hideGui
                || !minecraft.player.getMainHandItem().is(ModItems.WELDING_TOOL.get())) return;
        BlockPos target = targetPos(minecraft);
        // The crosshair sabotage prompt owns the screen while aiming at a core.
        if (target == null || WeldingTargetClientState.hasPrompt(target)) return;
        var entry = ReinforcementClientState.at(target);
        // Without the role permission nothing is reinforceable; with it, only own land is.
        boolean noPermission = !ReinforcementClientState.allowed();
        boolean unavailable = noPermission;
        if (!unavailable && entry == null) {
            // No local entry is either unreinforced own land or land we may not reinforce; wait for the server.
            Boolean reinforceable = WeldingTargetClientState.reinforceable(target);
            if (reinforceable == null) return;
            unavailable = !reinforceable;
        }
        int boxWidth = 236;
        boxWidth = Math.min(boxWidth, Math.max(140, graphics.guiWidth() - 24));
        int x = Math.max(12, graphics.guiWidth() - boxWidth - 12);
        int boxHeight = 72;
        int y = Math.max(12, (graphics.guiHeight() - boxHeight) / 2);
        int accent = unavailable ? 0xFF8F9AA8 : entry == null ? 0xFFFF6577
                : entry.siegeDisabled() ? 0xFFFF3D30
                : !entry.enabled() ? 0xFFC67AFF
                : entry.durability() < entry.material().maxDurability() ? 0xFFFFB454 : 0xFF68E09B;
        graphics.fill(x, y, x + boxWidth, y + boxHeight, 0xD012161D);
        graphics.fill(x, y, x + 3, y + boxHeight, accent);
        graphics.drawString(minecraft.font,
                Component.translatable(unavailable
                        ? "overlay.moveearth_addtional.reinforcement.unavailable"
                        : entry == null
                        ? "overlay.moveearth_addtional.reinforcement.unreinforced"
                        : entry.siegeDisabled()
                        ? "overlay.moveearth_addtional.reinforcement.siege_disabled"
                        : !entry.enabled()
                        ? "overlay.moveearth_addtional.reinforcement.curing"
                        : entry.constructionInProgress()
                        ? "overlay.moveearth_addtional.reinforcement.filling"
                        : "overlay.moveearth_addtional.reinforcement.reinforced"),
                x + 11, y + 8, accent, false);
        int reservationMinutes = WeldingTargetClientState.reservationMinutes(target);
        Component detail = unavailable
                ? Component.translatable(noPermission
                        ? "overlay.moveearth_addtional.reinforcement.unavailable.no_permission"
                        : reservationMinutes == 0
                        ? "overlay.moveearth_addtional.reinforcement.unavailable.lapsed_claimed"
                        : "overlay.moveearth_addtional.reinforcement.unavailable.outside")
                : entry == null
                ? Component.translatable("overlay.moveearth_addtional.reinforcement.material_hint")
                : entry.siegeDisabled()
                ? Component.translatable("overlay.moveearth_addtional.reinforcement.siege_disabled.detail",
                entry.material().id(), entry.durability(), entry.material().maxDurability())
                : !entry.enabled()
                ? Component.translatable("overlay.moveearth_addtional.reinforcement.curing.detail",
                entry.material().id(), Math.max(0, (ReinforcementClientState.activationTicksRemaining(entry) + 19) / 20))
                : Component.translatable("overlay.moveearth_addtional.reinforcement.detail",
                entry.material().id(), entry.durability(), entry.material().maxDurability());
        graphics.drawString(minecraft.font, detail, x + 11, y + 23, 0xFFE8EDF3, false);
        int progressWidth = boxWidth - 22;
        int filledWidth = entry == null ? 0 : Math.round(progressWidth * progress(entry));
        graphics.fill(x + 11, y + 38, x + 11 + progressWidth, y + 44, 0xFF282F39);
        if (filledWidth > 0) graphics.fill(x + 11, y + 38, x + 11 + filledWidth, y + 44, accent);
        graphics.fill(x + 11, y + 38, x + 11 + progressWidth, y + 39, 0x668F9AA8);
        graphics.drawString(minecraft.font,
                Component.translatable("overlay.moveearth_addtional.reinforcement.brush",
                        WeldingBrushClientState.size(), WeldingBrushClientState.size()),
                x + 11, y + 53, 0xFFFFB454, false);
        Component mode = Component.translatable(ReinforcementClientState.overlayActive()
                ? "overlay.moveearth_addtional.reinforcement.detail_on"
                : "overlay.moveearth_addtional.reinforcement.detail_hint");
        graphics.drawString(minecraft.font, mode, x + boxWidth - minecraft.font.width(mode) - 9, y + 8,
                ReinforcementClientState.overlayActive() ? 0xFF5DCBFF : 0xFF8F9AA8, false);
        if (reservationMinutes >= 0 && !noPermission) {
            // A configuring core holds its land for one hour of open time; say how much is left,
            // or that it ran out and only unclaimed land can still be sealed.
            Component reservation = reservationMinutes > 0
                    ? Component.translatable("overlay.moveearth_addtional.reinforcement.reservation.left", reservationMinutes)
                    : Component.translatable("overlay.moveearth_addtional.reinforcement.reservation.lapsed");
            int reservationY = y + boxHeight + 4;
            graphics.fill(x, reservationY, x + boxWidth, reservationY + 14, 0xD012161D);
            graphics.drawString(minecraft.font, reservation, x + 11, reservationY + 3,
                    reservationMinutes > 10 ? 0xFF8F9AA8 : 0xFFFFB454, false);
        }
    }

    private static boolean validWorld(Minecraft minecraft) {
        return minecraft.level != null && minecraft.player != null
                && ReinforcementClientState.dimension() != null
                && minecraft.level.dimension().location().equals(ReinforcementClientState.dimension());
    }

    private static BlockPos targetPos(Minecraft minecraft) {
        BlockHitResult hit = targetHit(minecraft);
        return hit == null ? null : hit.getBlockPos();
    }

    private static BlockHitResult targetHit(Minecraft minecraft) {
        return minecraft.hitResult instanceof BlockHitResult hit ? hit : null;
    }

    private static void applyLocalSpace(PoseStack poseStack, BlockPos target, Vec3 camera, float partialTick) {
        ClientSubLevel subLevel = Sable.HELPER.getContainingClient(target);
        if (subLevel == null) {
            poseStack.translate(target.getX() - camera.x, target.getY() - camera.y, target.getZ() - camera.z);
            return;
        }
        var pose = subLevel.renderPose(partialTick);
        Vec3 worldOrigin = pose.transformPosition(Vec3.atLowerCornerOf(target));
        poseStack.translate(worldOrigin.x - camera.x, worldOrigin.y - camera.y, worldOrigin.z - camera.z);
        poseStack.mulPose(new Quaternionf(pose.orientation()));
        poseStack.scale((float) pose.scale().x(), (float) pose.scale().y(), (float) pose.scale().z());
    }

    /**
     * Gathers chunks that can contribute this frame, culled before any per-face work: frustum and
     * distance for world chunks, distance through the body's pose for Sable chunks. Sable decides
     * per chunk ({@code getContainingClient} looks only at the chunk), once per frame.
     */
    private static int collectChunks(Frustum frustum, Vec3 camera, float partialTick) {
        int count = 0;
        for (ReinforcementClientState.ChunkBucket chunk : ReinforcementClientState.chunks()) {
            if (chunk.faceCount() == 0) continue;
            ClientSubLevel subLevel = Sable.HELPER.getContainingClient(chunk.chunkX(), chunk.chunkZ());
            double distanceSquared;
            if (subLevel == null) {
                distanceSquared = nearestDistanceSquared(camera, chunk.bounds());
                if (distanceSquared > MAX_RENDER_DISTANCE_SQUARED || !frustum.isVisible(chunk.bounds())) continue;
            } else {
                Pose3dc pose = POSES.get(subLevel);
                if (pose == null) {
                    pose = subLevel.renderPose(partialTick);
                    POSES.put(subLevel, pose);
                }
                double halfHeight = (chunk.maxY() - chunk.minY() + 1) * 0.5D;
                LOCAL.set((chunk.chunkX() << 4) + 8.0D, chunk.minY() + halfHeight, (chunk.chunkZ() << 4) + 8.0D);
                pose.transformPosition(LOCAL, WORLD);
                Vector3dc scale = pose.scale();
                double radius = Math.sqrt(128.0D + halfHeight * halfHeight)
                        * Math.max(scale.x(), Math.max(scale.y(), scale.z()));
                double gap = Math.max(0.0D, Math.sqrt(WORLD.distanceSquared(camera.x, camera.y, camera.z)) - radius);
                if (gap > MAX_RENDER_DISTANCE) continue;
                distanceSquared = gap * gap;
            }
            if (count == chunkOrder.length) growChunkScratch();
            chunkSlots[count] = chunk;
            chunkSubLevels[count] = subLevel;
            chunkOrder[count] = ReinforcementRenderBudget.key(distanceSquared, count);
            count++;
        }
        ReinforcementRenderBudget.sortNearestFirst(chunkOrder, count);
        return count;
    }

    private static int collectStaticFaces(ReinforcementClientState.ChunkBucket chunk, Vec3 camera,
                                          int rendered, int limit) {
        int[] data = chunk.faceData();
        boolean wholeChunkInRange = farthestDistanceSquared(camera, chunk.bounds()) <= MAX_RENDER_DISTANCE_SQUARED;
        for (int face = 0, offset = 0; face < chunk.faceCount() && rendered < limit; face++, offset += STRIDE) {
            int x = data[offset], y = data[offset + 1], z = data[offset + 2];
            int sizeX = data[offset + 3], sizeY = data[offset + 4], sizeZ = data[offset + 5];
            double relativeX = x - camera.x;
            double relativeY = y - camera.y;
            double relativeZ = z - camera.z;
            if (!wholeChunkInRange) {
                double centerX = relativeX + sizeX * 0.5D;
                double centerY = relativeY + sizeY * 0.5D;
                double centerZ = relativeZ + sizeZ * 0.5D;
                if (centerX * centerX + centerY * centerY + centerZ * centerZ > MAX_RENDER_DISTANCE_SQUARED) continue;
            }
            int positionIndex = rendered * 3;
            int colorIndex = rendered * 4;
            int styleIndex = data[offset + 7] * 4;
            INSTANCE_POSITIONS[positionIndex] = (float) relativeX;
            INSTANCE_POSITIONS[positionIndex + 1] = (float) relativeY;
            INSTANCE_POSITIONS[positionIndex + 2] = (float) relativeZ;
            INSTANCE_SCALES[positionIndex] = sizeX;
            INSTANCE_SCALES[positionIndex + 1] = sizeY;
            INSTANCE_SCALES[positionIndex + 2] = sizeZ;
            INSTANCE_COLORS[colorIndex] = styleColors[styleIndex];
            INSTANCE_COLORS[colorIndex + 1] = styleColors[styleIndex + 1];
            INSTANCE_COLORS[colorIndex + 2] = styleColors[styleIndex + 2];
            INSTANCE_COLORS[colorIndex + 3] = styleColors[styleIndex + 3];
            INSTANCE_FACES[rendered] = data[offset + 6];
            rendered++;
        }
        return rendered;
    }

    /** Draws one Sable chunk's faces in the body's local space; returns how many were drawn. */
    private static int renderMovingChunk(PoseStack poseStack, MultiBufferSource.BufferSource buffers,
                                         ReinforcementClientState.ChunkBucket chunk, Pose3dc pose,
                                         Vec3 camera, int limit) {
        if (limit <= 0) return 0;
        int originX = chunk.chunkX() << 4;
        int originY = chunk.minY();
        int originZ = chunk.chunkZ() << 4;
        poseStack.pushPose();
        LOCAL.set(originX, originY, originZ);
        pose.transformPosition(LOCAL, WORLD);
        poseStack.translate(WORLD.x - camera.x, WORLD.y - camera.y, WORLD.z - camera.z);
        poseStack.mulPose(new Quaternionf(pose.orientation()));
        poseStack.scale((float) pose.scale().x(), (float) pose.scale().y(), (float) pose.scale().z());
        VertexConsumer consumer = buffers.getBuffer(RenderType.debugFilledBox());
        int[] data = chunk.faceData();
        int drawn = 0;
        for (int face = 0, offset = 0; face < chunk.faceCount() && drawn < limit; face++, offset += STRIDE) {
            int x = data[offset], y = data[offset + 1], z = data[offset + 2];
            int sizeX = data[offset + 3], sizeY = data[offset + 4], sizeZ = data[offset + 5];
            LOCAL.set(x + sizeX * 0.5D, y + sizeY * 0.5D, z + sizeZ * 0.5D);
            pose.transformPosition(LOCAL, WORLD);
            if (WORLD.distanceSquared(camera.x, camera.y, camera.z) > MAX_RENDER_DISTANCE_SQUARED) continue;
            int styleIndex = data[offset + 7] * 4;
            emitFace(poseStack, consumer, x - originX, y - originY, z - originZ, sizeX, sizeY, sizeZ,
                    data[offset + 6], styleColors[styleIndex], styleColors[styleIndex + 1],
                    styleColors[styleIndex + 2], styleColors[styleIndex + 3]);
            drawn++;
        }
        poseStack.popPose();
        return drawn;
    }

    private static void renderCompatibility(PoseStack poseStack, MultiBufferSource.BufferSource buffers, int count) {
        VertexConsumer consumer = buffers.getBuffer(RenderType.debugFilledBox());
        for (int index = 0; index < count; index++) {
            int positionIndex = index * 3;
            int colorIndex = index * 4;
            emitFace(poseStack, consumer, INSTANCE_POSITIONS[positionIndex], INSTANCE_POSITIONS[positionIndex + 1],
                    INSTANCE_POSITIONS[positionIndex + 2], INSTANCE_SCALES[positionIndex],
                    INSTANCE_SCALES[positionIndex + 1], INSTANCE_SCALES[positionIndex + 2], INSTANCE_FACES[index],
                    INSTANCE_COLORS[colorIndex], INSTANCE_COLORS[colorIndex + 1],
                    INSTANCE_COLORS[colorIndex + 2], INSTANCE_COLORS[colorIndex + 3]);
        }
    }

    /** One colour per visual style for this frame; the pulse is evaluated per style, never per face. */
    private static void updateStyleColors(boolean detailed, long now) {
        int count = ReinforcementClientState.styleCount();
        if (styleColors.length < count * 4) styleColors = new float[Math.max(count * 4, styleColors.length * 2)];
        for (int id = 0; id < count; id++) {
            ReinforcementClientState.VisualStyle visual = ReinforcementClientState.style(id);
            int index = id * 4;
            if (visual.siegeDisabled()) {
                styleColors[index] = 1.0F;
                styleColors[index + 1] = 0.18F;
                styleColors[index + 2] = 0.12F;
                styleColors[index + 3] = detailed ? 0.42F : 0.27F;
                continue;
            }
            ReinforcementVisualStyle.Style style = ReinforcementVisualStyle.forEntry(
                    visual.material(), visual.durability(), visual.enabled(), detailed, now);
            styleColors[index] = style.red();
            styleColors[index + 1] = style.green();
            styleColors[index + 2] = style.blue();
            styleColors[index + 3] = style.alpha();
        }
    }

    private static void growChunkScratch() {
        int size = chunkOrder.length * 2;
        chunkOrder = java.util.Arrays.copyOf(chunkOrder, size);
        chunkSlots = java.util.Arrays.copyOf(chunkSlots, size);
        chunkSubLevels = java.util.Arrays.copyOf(chunkSubLevels, size);
    }

    private static double nearestDistanceSquared(Vec3 point, AABB box) {
        double dx = Math.max(0.0D, Math.max(box.minX - point.x, point.x - box.maxX));
        double dy = Math.max(0.0D, Math.max(box.minY - point.y, point.y - box.maxY));
        double dz = Math.max(0.0D, Math.max(box.minZ - point.z, point.z - box.maxZ));
        return dx * dx + dy * dy + dz * dz;
    }

    private static double farthestDistanceSquared(Vec3 point, AABB box) {
        double dx = Math.max(Math.abs(point.x - box.minX), Math.abs(point.x - box.maxX));
        double dy = Math.max(Math.abs(point.y - box.minY), Math.abs(point.y - box.maxY));
        double dz = Math.max(Math.abs(point.z - box.minZ), Math.abs(point.z - box.maxZ));
        return dx * dx + dy * dy + dz * dz;
    }

    private static float progress(ReinforcementClientState.Entry entry) {
        if (!entry.enabled()) {
            return Math.max(0.0F, Math.min(1.0F, 1.0F
                    - ReinforcementClientState.activationTicksRemaining(entry) / (float) com.ruskserver.moveearth_addtional.s2.reinforcement.ReinforcementEntry.ACTIVATION_DELAY_TICKS));
        }
        return Math.max(0.0F, Math.min(1.0F,
                entry.durability() / (float) Math.max(1, entry.material().maxDurability())));
    }

    private static String progressBar(ReinforcementClientState.Entry entry) {
        int filled = Math.round(progress(entry) * 8.0F);
        return "▰".repeat(filled) + "▱".repeat(8 - filled);
    }

    /** A merged face as a thin box, emitted straight into the strip without an {@link AABB}. */
    private static void emitFace(PoseStack poseStack, VertexConsumer consumer, float x, float y, float z,
                                 float sizeX, float sizeY, float sizeZ, int face,
                                 float red, float green, float blue, float alpha) {
        float e = 0.003F;
        switch (FACES[face]) {
            case NORTH -> LevelRenderer.addChainedFilledBoxVertices(poseStack, consumer,
                    x, y, z - e, x + sizeX, y + sizeY, z + e, red, green, blue, alpha);
            case SOUTH -> LevelRenderer.addChainedFilledBoxVertices(poseStack, consumer,
                    x, y, z + sizeZ - e, x + sizeX, y + sizeY, z + sizeZ + e, red, green, blue, alpha);
            case WEST -> LevelRenderer.addChainedFilledBoxVertices(poseStack, consumer,
                    x - e, y, z, x + e, y + sizeY, z + sizeZ, red, green, blue, alpha);
            case EAST -> LevelRenderer.addChainedFilledBoxVertices(poseStack, consumer,
                    x + sizeX - e, y, z, x + sizeX + e, y + sizeY, z + sizeZ, red, green, blue, alpha);
            case DOWN -> LevelRenderer.addChainedFilledBoxVertices(poseStack, consumer,
                    x, y - e, z, x + sizeX, y + e, z + sizeZ, red, green, blue, alpha);
            case UP -> LevelRenderer.addChainedFilledBoxVertices(poseStack, consumer,
                    x, y + sizeY - e, z, x + sizeX, y + sizeY + e, z + sizeZ, red, green, blue, alpha);
        }
    }

    static AABB selectionFaceBounds(BlockPos center, Direction face, int radius) {
        int safeRadius = ReinforcementBrushPattern.clamp(radius);
        double epsilon = 0.018D;
        return switch (face.getAxis()) {
            case X -> {
                double x = center.getX() + (face.getAxisDirection() == Direction.AxisDirection.POSITIVE ? 1.0D : 0.0D);
                yield new AABB(x - epsilon, center.getY() - safeRadius, center.getZ() - safeRadius,
                        x + epsilon, center.getY() + safeRadius + 1.0D, center.getZ() + safeRadius + 1.0D);
            }
            case Y -> {
                double y = center.getY() + (face.getAxisDirection() == Direction.AxisDirection.POSITIVE ? 1.0D : 0.0D);
                yield new AABB(center.getX() - safeRadius, y - epsilon, center.getZ() - safeRadius,
                        center.getX() + safeRadius + 1.0D, y + epsilon, center.getZ() + safeRadius + 1.0D);
            }
            case Z -> {
                double z = center.getZ() + (face.getAxisDirection() == Direction.AxisDirection.POSITIVE ? 1.0D : 0.0D);
                yield new AABB(center.getX() - safeRadius, center.getY() - safeRadius, z - epsilon,
                        center.getX() + safeRadius + 1.0D, center.getY() + safeRadius + 1.0D, z + epsilon);
            }
        };
    }
}
