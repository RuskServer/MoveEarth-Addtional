package com.ruskserver.moveearth_addtional.client;

import com.ruskserver.moveearth_addtional.s2.nation.NationFoundationSite;
import dev.ryanhcode.sable.Sable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Short-lived client draft used while selecting the initial capital in the world. */
public final class NationFoundationClientState {
    private static Draft draft;
    private static BlockPos candidate;
    private static NationFoundationSite.Verdict verdict;

    private NationFoundationClientState() { }

    public static void begin(long revision, String name, String tag) {
        draft = new Draft(revision, name, tag);
        candidate = null;
        verdict = null;
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.setScreen(null);
        if (minecraft.player != null) minecraft.player.displayClientMessage(Component.translatable(
                "overlay.moveearth_addtional.nation.foundation.select"), true);
    }

    public static void tick() {
        Minecraft minecraft = Minecraft.getInstance();
        if (draft == null) return;
        if (minecraft.player == null || minecraft.level == null) {
            clear();
            return;
        }
        if (minecraft.screen != null) return;
        aim(minecraft.player, minecraft.level);
    }

    public static void handleInteraction(boolean attack, boolean use) {
        if (draft == null) return;
        if (attack) reopen(null);
        else if (use && placeable()) reopen(candidate);
    }

    /**
     * Finds the block the player is pointing at and judges it.
     *
     * <p>Deliberately not {@code Minecraft.hitResult}. The crosshair traces with
     * {@link ClipContext.Block#OUTLINE}, and grass, ferns, flowers and a single
     * snow layer all have an outline while having no collision at all. Aiming at
     * a grassy field therefore struck the side of a grass plant — not an upward
     * face, so no site at all — or, looking steeply down, its top face, which put
     * the capital one block in the air for the server to refuse. A capital could
     * not be founded on a plain. {@link ClipContext.Block#COLLIDER} passes
     * through all of them and is what the server traces with.
     */
    private static void aim(LocalPlayer player, ClientLevel level) {
        Vec3 eye = player.getEyePosition();
        Vec3 end = eye.add(player.getViewVector(1.0F).scale(NationFoundationSite.REACH));
        BlockHitResult hit = level.clip(new ClipContext(eye, end,
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        if (hit.getType() != HitResult.Type.BLOCK || hit.getDirection() != Direction.UP) {
            // Pointing at a wall or at nothing. There is no block to report on,
            // so the overlay asks for ground rather than naming a fault.
            candidate = null;
            verdict = null;
            return;
        }
        BlockPos pos = hit.getBlockPos().above().immutable();
        candidate = pos;
        verdict = NationFoundationSite.judge(read(player, level, pos));
    }

    /**
     * Answers the same nine questions the server asks, from the client's copy of
     * the world.
     *
     * <p>Sight is the one the client cannot honestly re-derive, and does not need
     * to: the trace that produced this position started at the player's own eye,
     * so it is clear by construction. The server traces it again for itself,
     * which is what that check is for.
     */
    private static NationFoundationSite.Reading read(LocalPlayer player, ClientLevel level, BlockPos pos) {
        if (!level.hasChunkAt(pos)) return NationFoundationSite.Reading.unloaded();
        BlockPos support = pos.below();
        boolean onVehicle = false;
        try {
            onVehicle = Sable.HELPER.getContainingClient(pos) != null;
        } catch (RuntimeException | LinkageError ignored) {
            // Sable is optional at runtime; the server refuses a vehicle deck anyway.
        }
        return new NationFoundationSite.Reading(true,
                !level.isOutsideBuildHeight(pos) && level.getWorldBorder().isWithinBounds(pos),
                player.distanceToSqr(Vec3.atCenterOf(pos)) <= NationFoundationSite.REACH_SQR,
                true,
                onVehicle,
                level.getBlockState(pos).canBeReplaced(),
                !level.getFluidState(pos).isEmpty(),
                level.getBlockState(support).isFaceSturdy(level, support, Direction.UP),
                !level.getEntities((Entity) null, new AABB(pos), entity -> !entity.isSpectator())
                        .isEmpty());
    }

    private static void reopen(BlockPos selected) {
        Minecraft minecraft = Minecraft.getInstance();
        Draft current = draft;
        draft = null;
        candidate = null;
        verdict = null;
        ResourceLocation dimension = selected == null || minecraft.level == null
                ? null : minecraft.level.dimension().location();
        minecraft.setScreen(new NationCreateScreen(current.revision, current.name, current.tag,
                dimension, selected));
    }

    public static boolean active() { return draft != null; }

    /** The block being aimed at, whether or not it may be built on. */
    public static BlockPos candidate() { return candidate; }

    /** Why the aimed-at block was refused, or null when nothing is being aimed at. */
    public static NationFoundationSite.Verdict verdict() { return verdict; }

    /** Whether confirming right now would be accepted by the server. */
    public static boolean placeable() {
        return candidate != null && verdict != null && verdict.allowed();
    }

    public static void clear() {
        draft = null;
        candidate = null;
        verdict = null;
    }

    private record Draft(long revision, String name, String tag) { }
}
