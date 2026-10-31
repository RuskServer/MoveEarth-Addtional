package com.ruskserver.moveearth_addtional.compat.aeronautics;

import com.ruskserver.moveearth_addtional.config.AeronauticsSwivelConfig;
import dev.eriksonn.aeronautics.content.blocks.hot_air.balloon.graph.BalloonBuilder;
import dev.eriksonn.aeronautics.content.blocks.hot_air.balloon.graph.BalloonLayerGraph;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * The part of a holed Create Aeronautics balloon that still holds hot air.
 *
 * <p>A balloon is the air its envelope caps from above, flood-filled up from its burner. A hole in the side lets
 * out the air at and below the hole, but the air above it is still capped and stays. Aeronautics rebuilds a balloon
 * only from the burner, at the very bottom, so any hole above the burner emptied the whole balloon. This rebuilds
 * it from the burner's column instead: the first space above the burner that is still sealed.
 */
public final class BalloonHoles {
    private static final TagKey<Block> AIRTIGHT = TagKey.create(Registries.BLOCK,
            ResourceLocation.fromNamespaceAndPath("aeronautics", "airtight"));

    private BalloonHoles() {
    }

    /**
     * The sealed space above {@code burner}, searched no higher than {@code ceilingY}; null when there is none or
     * the feature is off.
     */
    @Nullable
    public static BalloonLayerGraph sealedAbove(Level level, BlockPos burner, int ceilingY) {
        if (!AeronauticsSwivelConfig.balloonHolesEnabled()) return null;
        BlockPos.MutableBlockPos pos = burner.mutable();
        while (pos.getY() < ceilingY) {
            pos.move(0, 1, 0);
            if (airtight(level.getBlockState(pos))) return null;
            BalloonLayerGraph graph = BalloonBuilder.buildBalloon(level, pos.immutable(), null);
            if (graph != null) return graph;
        }
        return null;
    }

    /** Whether hot air from {@code burner} rises into {@code graph}: straight up through open space. */
    public static boolean feeds(Level level, BalloonLayerGraph graph, BlockPos burner) {
        if (graph.hasBlockAt(burner)) return true;
        if (!AeronauticsSwivelConfig.balloonHolesEnabled()) return false;
        BlockPos.MutableBlockPos pos = burner.mutable();
        while (pos.getY() < graph.getMaxY()) {
            pos.move(0, 1, 0);
            if (graph.hasBlockAt(pos)) return true;
            if (airtight(level.getBlockState(pos))) return false;
        }
        return false;
    }

    private static boolean airtight(BlockState state) {
        return !state.isAir() && state.is(AIRTIGHT);
    }
}
