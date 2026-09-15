package com.ruskserver.moveearth_addtional.client;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

/** Short-lived client draft used while selecting the initial capital in the world. */
public final class NationFoundationClientState {
    private static Draft draft;
    private static BlockPos candidate;

    private NationFoundationClientState() { }

    public static void begin(long revision, String name, String tag) {
        draft = new Draft(revision, name, tag);
        candidate = null;
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
        candidate = candidate(minecraft);
    }

    public static void handleInteraction(boolean attack, boolean use) {
        if (draft == null) return;
        if (attack) reopen(null);
        else if (use && candidate != null) reopen(candidate);
    }

    private static BlockPos candidate(Minecraft minecraft) {
        if (!(minecraft.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK
                || hit.getDirection() != Direction.UP) return null;
        BlockPos pos = hit.getBlockPos().above();
        return minecraft.level.getBlockState(pos).canBeReplaced() ? pos.immutable() : null;
    }

    private static void reopen(BlockPos selected) {
        Minecraft minecraft = Minecraft.getInstance();
        Draft current = draft;
        draft = null;
        candidate = null;
        ResourceLocation dimension = selected == null || minecraft.level == null
                ? null : minecraft.level.dimension().location();
        minecraft.setScreen(new NationCreateScreen(current.revision, current.name, current.tag,
                dimension, selected));
    }

    public static boolean active() { return draft != null; }
    public static BlockPos candidate() { return candidate; }

    public static void clear() {
        draft = null;
        candidate = null;
    }

    private record Draft(long revision, String name, String tag) { }
}
