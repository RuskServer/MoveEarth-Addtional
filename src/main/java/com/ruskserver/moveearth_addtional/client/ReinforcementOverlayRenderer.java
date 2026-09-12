package com.ruskserver.moveearth_addtional.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import com.ruskserver.moveearth_addtional.item.ModItems;
import com.ruskserver.moveearth_addtional.network.S2C_ReinforcementSnapshotPacket;
import com.ruskserver.moveearth_addtional.s2.reinforcement.ReinforcementVisualStyle;
import com.ruskserver.moveearth_addtional.s2.reinforcement.ReinforcementBrushPattern;
import com.ruskserver.moveearth_addtional.s2.reinforcement.ReinforcementGreedyMesher;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
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

@EventBusSubscriber(modid = Moveearth_addtional.MODID, bus = EventBusSubscriber.Bus.GAME, value = Dist.CLIENT)
public final class ReinforcementOverlayRenderer {
    private static final int PASSIVE_RENDER_LIMIT = 4096;
    private static final int DETAILED_RENDER_LIMIT = 8192;
    private static final int FACES_PER_BLOCK = 6;
    private static final int MAX_RENDER_QUADS = DETAILED_RENDER_LIMIT * FACES_PER_BLOCK;
    private static final double MAX_RENDER_DISTANCE_SQUARED = 68.0D * 68.0D;
    private static final float[] INSTANCE_POSITIONS = new float[MAX_RENDER_QUADS * 3];
    private static final float[] INSTANCE_SCALES = new float[MAX_RENDER_QUADS * 3];
    private static final float[] INSTANCE_COLORS = new float[MAX_RENDER_QUADS * 4];
    private static final int[] INSTANCE_FACES = new int[MAX_RENDER_QUADS];

    private ReinforcementOverlayRenderer() {
    }

    @SubscribeEvent
    public static void renderWorld(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        Minecraft minecraft = Minecraft.getInstance();
        boolean holdingWelder = minecraft.player != null
                && minecraft.player.getMainHandItem().is(ModItems.WELDING_TOOL.get());
        if (!holdingWelder || !validWorld(minecraft) || !ReinforcementClientState.allowed()) return;

        PoseStack poseStack = event.getPoseStack();
        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        Vec3 camera = event.getCamera().getPosition();
        long now = Util.getMillis();
        boolean detailed = ReinforcementClientState.overlayActive();
        int renderLimit = (detailed ? DETAILED_RENDER_LIMIT : PASSIVE_RENDER_LIMIT) * FACES_PER_BLOCK;
        int rendered = 0;
        outer:
        for (ReinforcementClientState.ChunkBucket chunk : ReinforcementClientState.chunks()) {
            AABB chunkBounds = new AABB(chunk.chunkX() << 4, chunk.minY(), chunk.chunkZ() << 4,
                    (chunk.chunkX() << 4) + 16, chunk.maxY() + 1, (chunk.chunkZ() << 4) + 16).inflate(0.03D);
            if (!event.getFrustum().isVisible(chunkBounds)) continue;
            for (ReinforcementClientState.MergedFace merged : chunk.faces()) {
                ReinforcementGreedyMesher.Quad quad = merged.quad();
                S2C_ReinforcementSnapshotPacket.Entry entry = merged.style();
                if (camera.distanceToSqr(quad.x() + quad.sizeX() * 0.5D,
                        quad.y() + quad.sizeY() * 0.5D, quad.z() + quad.sizeZ() * 0.5D)
                        > MAX_RENDER_DISTANCE_SQUARED) continue;
                if (rendered >= renderLimit) break outer;
                ReinforcementVisualStyle.Style style = ReinforcementVisualStyle.forEntry(
                        entry.material(), entry.durability(), entry.enabled(), detailed, now);
                int positionIndex = rendered * 3;
                int colorIndex = rendered * 4;
                INSTANCE_POSITIONS[positionIndex] = (float) (quad.x() - camera.x);
                INSTANCE_POSITIONS[positionIndex + 1] = (float) (quad.y() - camera.y);
                INSTANCE_POSITIONS[positionIndex + 2] = (float) (quad.z() - camera.z);
                INSTANCE_SCALES[positionIndex] = quad.sizeX();
                INSTANCE_SCALES[positionIndex + 1] = quad.sizeY();
                INSTANCE_SCALES[positionIndex + 2] = quad.sizeZ();
                INSTANCE_COLORS[colorIndex] = entry.siegeDisabled() ? 1.0F : style.red();
                INSTANCE_COLORS[colorIndex + 1] = entry.siegeDisabled() ? 0.18F : style.green();
                INSTANCE_COLORS[colorIndex + 2] = entry.siegeDisabled() ? 0.12F : style.blue();
                INSTANCE_COLORS[colorIndex + 3] = entry.siegeDisabled()
                        ? (detailed ? 0.42F : 0.27F) : style.alpha();
                INSTANCE_FACES[rendered] = quad.face().ordinal();
                rendered++;
            }
        }
        if (!ReinforcementGl45Renderer.render(
                poseStack, INSTANCE_POSITIONS, INSTANCE_SCALES,
                INSTANCE_COLORS, INSTANCE_FACES, rendered)) {
            for (int index = 0; index < rendered; index++) {
                int positionIndex = index * 3;
                int colorIndex = index * 4;
                float x = INSTANCE_POSITIONS[positionIndex];
                float y = INSTANCE_POSITIONS[positionIndex + 1];
                float z = INSTANCE_POSITIONS[positionIndex + 2];
                float sizeX = INSTANCE_SCALES[positionIndex];
                float sizeY = INSTANCE_SCALES[positionIndex + 1];
                float sizeZ = INSTANCE_SCALES[positionIndex + 2];
                DebugRenderer.renderFilledBox(poseStack, buffers,
                        mergedFaceBounds(x, y, z, sizeX, sizeY, sizeZ, INSTANCE_FACES[index]),
                        INSTANCE_COLORS[colorIndex], INSTANCE_COLORS[colorIndex + 1],
                        INSTANCE_COLORS[colorIndex + 2], INSTANCE_COLORS[colorIndex + 3]);
            }
        }

        BlockHitResult targetHit = targetHit(minecraft);
        BlockPos target = targetHit == null ? null : targetHit.getBlockPos();
        if (target != null && minecraft.level != null) {
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
                S2C_ReinforcementSnapshotPacket.Entry selectedEntry = ReinforcementClientState.at(selected);
                float red = selectedEntry == null || selectedEntry.siegeDisabled() ? 1.0F : !selectedEntry.enabled() ? 0.72F
                        : selectedEntry.constructionInProgress() ? 1.0F : 0.16F;
                float green = selectedEntry == null || selectedEntry.siegeDisabled() ? 0.16F : !selectedEntry.enabled() ? 0.27F
                        : selectedEntry.constructionInProgress() ? 0.63F : 1.0F;
                float blue = selectedEntry == null || selectedEntry.siegeDisabled() ? 0.12F : !selectedEntry.enabled() ? 1.0F
                        : selectedEntry.constructionInProgress() ? 0.12F : 0.30F;
                DebugRenderer.renderFilledBox(poseStack, buffers,
                        new AABB(selected).inflate(0.008D).move(-camera.x, -camera.y, -camera.z),
                        red, green, blue, detailed ? 0.31F : 0.22F);
            }
            AABB faceBounds = selectionFaceBounds(target, clickedFace, WeldingBrushClientState.radius())
                    .move(-camera.x, -camera.y, -camera.z);
            VertexConsumer outline = buffers.getBuffer(RenderType.lines());
            LevelRenderer.renderLineBox(poseStack, outline, faceBounds.inflate(0.012D),
                    0.05F, 0.32F, 0.06F, 1.0F);
            LevelRenderer.renderLineBox(poseStack, outline, faceBounds,
                    0.22F, 1.0F, 0.30F, 1.0F);
        }
        if (detailed && target != null && minecraft.level != null
                && !minecraft.level.getBlockState(target).isAir()) {
            var entry = ReinforcementClientState.at(target);
            boolean reinforced = entry != null;
            int targetColor = entry != null && entry.siegeDisabled() ? 0xFFFF3D30
                    : entry != null && !entry.enabled() ? 0xFFC67AFF
                    : reinforced ? 0xFF68E09B : 0xFFFF6577;
            DebugRenderer.renderFilledBox(poseStack, buffers,
                    new AABB(target).inflate(0.012D).move(-camera.x, -camera.y, -camera.z),
                    entry != null && entry.siegeDisabled() ? 1.0F : reinforced ? 0.50F : 1.0F,
                    entry != null && entry.siegeDisabled() ? 0.18F
                            : entry != null && !entry.enabled() ? 0.28F : reinforced ? 0.94F : 0.30F,
                    entry != null && entry.siegeDisabled() ? 0.12F
                            : entry != null && !entry.enabled() ? 0.92F : reinforced ? 0.63F : 0.22F, 0.24F);
            String marker = entry == null ? "×" : progressBar(entry) + " " + Math.round(progress(entry) * 100.0F) + "%";
            DebugRenderer.renderFloatingText(poseStack, buffers, marker,
                    target.getX() + 0.5D, target.getY() + 1.18D, target.getZ() + 0.5D,
                    targetColor, 0.024F, true, 0.0F, true);
        }
        buffers.endBatch(RenderType.debugFilledBox());
        buffers.endBatch();
    }

    @SubscribeEvent
    public static void renderHud(RenderGuiEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!validWorld(minecraft) || minecraft.options.hideGui || minecraft.screen != null
                || !minecraft.player.getMainHandItem().is(ModItems.WELDING_TOOL.get())) return;
        BlockPos target = targetPos(minecraft);
        if (target == null) return;
        var entry = ReinforcementClientState.at(target);
        var graphics = event.getGuiGraphics();
        int boxWidth = 236;
        boxWidth = Math.min(boxWidth, Math.max(140, graphics.guiWidth() - 24));
        int x = Math.max(12, graphics.guiWidth() - boxWidth - 12);
        int boxHeight = 72;
        int y = Math.max(12, (graphics.guiHeight() - boxHeight) / 2);
        int accent = !ReinforcementClientState.allowed() ? 0xFF8F9AA8 : entry == null ? 0xFFFF6577
                : entry.siegeDisabled() ? 0xFFFF3D30
                : !entry.enabled() ? 0xFFC67AFF
                : entry.durability() < entry.material().maxDurability() ? 0xFFFFB454 : 0xFF68E09B;
        graphics.fill(x, y, x + boxWidth, y + boxHeight, 0xD012161D);
        graphics.fill(x, y, x + 3, y + boxHeight, accent);
        graphics.drawString(minecraft.font,
                Component.translatable(!ReinforcementClientState.allowed()
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
        Component detail = !ReinforcementClientState.allowed()
                ? Component.translatable("overlay.moveearth_addtional.reinforcement.unavailable.detail")
                : entry == null
                ? Component.translatable("overlay.moveearth_addtional.reinforcement.material_hint")
                : entry.siegeDisabled()
                ? Component.translatable("overlay.moveearth_addtional.reinforcement.siege_disabled.detail",
                entry.material().id(), entry.durability(), entry.material().maxDurability())
                : !entry.enabled()
                ? Component.translatable("overlay.moveearth_addtional.reinforcement.curing.detail",
                entry.material().id(), Math.max(0, (entry.activationTicksRemaining() + 19) / 20))
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

    private static float progress(S2C_ReinforcementSnapshotPacket.Entry entry) {
        if (!entry.enabled()) {
            return Math.max(0.0F, Math.min(1.0F, 1.0F
                    - entry.activationTicksRemaining() / (float) com.ruskserver.moveearth_addtional.s2.reinforcement.ReinforcementEntry.ACTIVATION_DELAY_TICKS));
        }
        return Math.max(0.0F, Math.min(1.0F,
                entry.durability() / (float) Math.max(1, entry.material().maxDurability())));
    }

    private static String progressBar(S2C_ReinforcementSnapshotPacket.Entry entry) {
        int filled = Math.round(progress(entry) * 8.0F);
        return "▰".repeat(filled) + "▱".repeat(8 - filled);
    }

    private static AABB mergedFaceBounds(float x, float y, float z,
                                         float sizeX, float sizeY, float sizeZ, int face) {
        double e = 0.003D;
        return switch (ReinforcementGreedyMesher.Face.values()[face]) {
            case NORTH -> new AABB(x, y, z - e, x + sizeX, y + sizeY, z + e);
            case SOUTH -> new AABB(x, y, z + sizeZ - e, x + sizeX, y + sizeY, z + sizeZ + e);
            case WEST -> new AABB(x - e, y, z, x + e, y + sizeY, z + sizeZ);
            case EAST -> new AABB(x + sizeX - e, y, z, x + sizeX + e, y + sizeY, z + sizeZ);
            case DOWN -> new AABB(x, y - e, z, x + sizeX, y + e, z + sizeZ);
            case UP -> new AABB(x, y + sizeY - e, z, x + sizeX, y + sizeY + e, z + sizeZ);
        };
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
