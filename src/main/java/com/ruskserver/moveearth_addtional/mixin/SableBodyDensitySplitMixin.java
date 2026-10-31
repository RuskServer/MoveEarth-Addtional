package com.ruskserver.moveearth_addtional.mixin;

import com.ruskserver.moveearth_addtional.compat.vehicle.SableBodyDensityLimit;
import com.ruskserver.moveearth_addtional.config.AeronauticsSwivelConfig;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.plot.heat.SubLevelHeatMapManager;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Defers the complete split, preserving blocks and pending heatmap work instead of returning a null body. */
@Pseudo
@Mixin(value = SubLevelHeatMapManager.class, remap = false)
public abstract class SableBodyDensitySplitMixin {
    @Shadow @Final private ServerSubLevel subLevel;
    @Shadow @Final private Long2IntOpenHashMap subLevelSplits;
    @Shadow @Final private IntArrayList splitIndexMap;
    @Shadow private int solidCount;
    @Unique private long moveearth$retrySplitAt = Long.MIN_VALUE;

    @Inject(method = "step", at = @At("HEAD"), cancellable = true, remap = false)
    private void moveearth$waitForDensityBudget(CallbackInfoReturnable<Boolean> callback) {
        if (AeronauticsSwivelConfig.bodyDensityEnabled()
                && subLevel.getLevel().getGameTime() < moveearth$retrySplitAt) {
            // true stops Sable's per-tick step loop as well; no repeated scans in this tick.
            callback.setReturnValue(true);
        }
    }

    @Inject(method = "split", at = @At("HEAD"), cancellable = true, remap = false)
    private void moveearth$reviewAllFragments(CallbackInfo callback) {
        if (!AeronauticsSwivelConfig.bodyDensityEnabled()) return;
        var groups = new Int2ObjectOpenHashMap<List<BlockPos>>();
        for (long position : subLevelSplits.keySet()) {
            int index = splitIndexMap.getInt(subLevelSplits.get(position));
            groups.computeIfAbsent(index, ignored -> new ArrayList<>()).add(BlockPos.of(position));
        }
        int total = groups.values().stream().mapToInt(List::size).sum();
        // Sable retains the largest region in the original body when the entire body separates.
        // A single region covering the whole body only rebuilds its heatmap and adds no bodies.
        if (total != 0 && total == solidCount) {
            var retained = groups.int2ObjectEntrySet().stream()
                    .max(Comparator.comparingInt(entry -> entry.getValue().size())).orElseThrow();
            groups.remove(retained.getIntKey());
        }
        List<BlockPos> anchors = groups.values().stream().map(blocks -> blocks.getFirst()).toList();
        if (SableBodyDensityLimit.review(subLevel.getLevel(), anchors, null, true) != null) {
            moveearth$retrySplitAt = subLevel.getLevel().getGameTime() + 20;
            callback.cancel();
        }
    }
}
