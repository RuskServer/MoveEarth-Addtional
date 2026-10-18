package com.ruskserver.moveearth_addtional.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.ruskserver.moveearth_addtional.compat.mekanism.CleanWaterSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/**
 * Records water a bucket places where there was none, by a player or a
 * dispenser, as clean water for washing off radiation. Water poured into
 * existing water stays as dirty as what it joined.
 */
@Mixin(BucketItem.class)
public abstract class BucketCleanWaterMixin {
    @Shadow @Final public net.minecraft.world.level.material.Fluid content;

    @WrapMethod(method = "emptyContents(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/phys/BlockHitResult;Lnet/minecraft/world/item/ItemStack;)Z")
    private boolean moveearth$markCleanWater(@Nullable Player player, Level level, BlockPos pos,
                                             @Nullable BlockHitResult hit, @Nullable ItemStack container,
                                             Operation<Boolean> original) {
        boolean tracked = level instanceof ServerLevel && content.isSame(Fluids.WATER);
        boolean wasWater = tracked && level.getFluidState(pos).is(FluidTags.WATER);
        boolean placed = original.call(player, level, pos, hit, container);
        if (placed && tracked && !wasWater) {
            FluidState now = level.getFluidState(pos);
            if (now.is(FluidTags.WATER) && now.isSource()) CleanWaterSavedData.get((ServerLevel) level).mark(pos);
        }
        return placed;
    }
}
