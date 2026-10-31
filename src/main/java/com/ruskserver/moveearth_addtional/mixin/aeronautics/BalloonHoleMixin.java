package com.ruskserver.moveearth_addtional.mixin.aeronautics;

import com.ruskserver.moveearth_addtional.compat.aeronautics.BalloonHoles;
import com.ruskserver.moveearth_addtional.config.AeronauticsSwivelConfig;
import dev.eriksonn.aeronautics.content.blocks.hot_air.balloon.graph.BalloonBuilder;
import dev.eriksonn.aeronautics.content.blocks.hot_air.balloon.graph.BalloonLayerGraph;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A holed Create Aeronautics balloon keeps the hot air above the hole ({@link BalloonHoles}). Runs on both sides,
 * so the client draws the same hot air the server lifts with.
 *
 * <p>Aeronautics answered a broken envelope block by tearing its layer graph down from the hole, which in practice
 * emptied every connected layer: one block lost the whole balloon. Here a hole only marks the balloon to be rebuilt
 * on its next tick, from the burner's column when the burner's own layer has opened. That also folds a blast that
 * removes fifty envelope blocks into one rebuild instead of fifty.
 */
@Pseudo
@Mixin(targets = "dev.eriksonn.aeronautics.content.blocks.hot_air.balloon.Balloon", remap = false)
public abstract class BalloonHoleMixin {
    @Shadow @Final protected Level level;
    @Shadow protected BalloonLayerGraph graph;
    @Shadow protected BlockPos controllerPos;
    @Shadow protected int capacity;
    @Shadow private boolean assembling;

    @Shadow protected abstract void rebuildBalloonFromController();

    @Shadow public abstract boolean shouldSpawnGust(BlockPos pos);

    @Shadow public abstract void spawnGust(Level level, BlockPos pos, Direction dir);

    @Shadow
    private Iterable<Direction> findGustDirections(BlockPos pos) {
        throw new AssertionError();
    }

    @Unique
    private final List<BlockPos> moveearth$holes = new ArrayList<>();

    @Inject(method = "onAirtightBlockRemoved", at = @At("HEAD"), cancellable = true)
    private void moveearth$queueHole(BlockPos pos, CallbackInfo ci) {
        if (this.assembling || !AeronauticsSwivelConfig.balloonHolesEnabled()) return;
        if (this.moveearth$holes.size() < 16) this.moveearth$holes.add(pos.immutable());
        ci.cancel();
    }

    @Inject(method = "tick", at = @At("HEAD"))
    private void moveearth$settleHoles(CallbackInfo ci) {
        if (this.moveearth$holes.isEmpty() || this.assembling) return;
        List<BlockPos> holes = List.copyOf(this.moveearth$holes);
        this.moveearth$holes.clear();
        // Gusts are decided by the balloon as it was: how full it was up to the hole.
        Map<BlockPos, Iterable<Direction>> gusts = new LinkedHashMap<>();
        for (BlockPos hole : holes) {
            if (this.shouldSpawnGust(hole)) gusts.put(hole, this.findGustDirections(hole));
        }
        BalloonLayerGraph before = this.graph;
        int capacityBefore = this.capacity;
        this.rebuildBalloonFromController();
        // The same graph back means nothing was left to rebuild: the balloon is leaking out entirely.
        if (this.graph != before && this.capacity >= capacityBefore) return;
        gusts.forEach((hole, directions) -> {
            for (Direction direction : directions) this.spawnGust(this.level, hole.relative(direction), direction);
        });
    }

    @Redirect(method = "rebuildBalloonFromController", at = @At(value = "INVOKE",
            target = "Ldev/eriksonn/aeronautics/content/blocks/hot_air/balloon/graph/BalloonBuilder;buildBalloon(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Ldev/eriksonn/aeronautics/content/blocks/hot_air/balloon/graph/BalloonLayerGraph;)Ldev/eriksonn/aeronautics/content/blocks/hot_air/balloon/graph/BalloonLayerGraph;"))
    private BalloonLayerGraph moveearth$buildAboveHole(Level level, BlockPos start, BalloonLayerGraph mainGraph) {
        BalloonLayerGraph whole = BalloonBuilder.buildBalloon(level, start, mainGraph);
        if (whole != null || this.graph == null) return whole;
        // Searched no higher than the balloon reached, so a burner under open sky does not climb the world.
        return BalloonHoles.sealedAbove(level, start, this.graph.getMaxY());
    }

    @Redirect(method = "splitHeaters", at = @At(value = "INVOKE",
            target = "Ldev/eriksonn/aeronautics/content/blocks/hot_air/balloon/graph/BalloonLayerGraph;hasBlockAt(Lnet/minecraft/core/BlockPos;)Z"))
    private boolean moveearth$burnerStillFeeds(BalloonLayerGraph graph, BlockPos burner) {
        return BalloonHoles.feeds(this.level, graph, burner);
    }
}
