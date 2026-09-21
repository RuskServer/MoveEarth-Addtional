package com.ruskserver.moveearth_addtional.mixin;

import com.ruskserver.moveearth_addtional.compat.cdg.RegionOilGate;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Gives a chunk's oil to the region it lies in.
 *
 * <p>Only {@code getBaseOilAmount}, which is what Create: Diesel Generators
 * calls for a chunk nobody has pumped yet. The obvious target is
 * {@code getChunkOilAmount}, and it is the wrong one: it answers from a saved
 * record where there is one, so changing it would rewrite how much is left in
 * wells that are already in use.
 *
 * <p>Refusing at HEAD rather than returning zero at the end, because the first
 * thing the method does is walk every biome in the chunk -- sixteen by fifty by
 * sixteen lookups -- and a region that may not have oil has no use for the
 * answer. Gating makes those regions cheaper to generate than they were.
 *
 * <p>{@code @Pseudo} with a class name: Create: Diesel Generators is optional,
 * and a missing target must leave the rest of the mod running.
 */
@Pseudo
@Mixin(targets = "com.jesz.createdieselgenerators.world.OilChunksSavedData", remap = false)
public abstract class CdgOilChunksMixin {

    @Inject(method = "getBaseOilAmount", at = @At("HEAD"), cancellable = true,
            remap = false, require = 0)
    private static void moveearth$refuseOutsideItsRegion(ServerLevel level, ChunkPos chunk,
                                                         CallbackInfoReturnable<Integer> callback) {
        if (RegionOilGate.multiplierAt(level, chunk) <= 0.0) {
            callback.setReturnValue(0);
        }
    }

    /**
     * Scales what a region is allowed to have.
     *
     * <p>Never runs when the region has none: the refusal above returns before
     * the method body, so this only ever sees an amount the region may keep.
     * An untouched multiplier of one is left alone rather than round-tripped
     * through the arithmetic.
     */
    @Inject(method = "getBaseOilAmount", at = @At("RETURN"), cancellable = true,
            remap = false, require = 0)
    private static void moveearth$scaleToRegion(ServerLevel level, ChunkPos chunk,
                                                CallbackInfoReturnable<Integer> callback) {
        int amount = callback.getReturnValue();
        if (amount <= 0 || amount == Integer.MAX_VALUE) {
            return;
        }
        double multiplier = RegionOilGate.multiplierAt(level, chunk);
        if (multiplier == 1.0) {
            return;
        }
        callback.setReturnValue(RegionOilGate.scale(amount, multiplier));
    }
}
