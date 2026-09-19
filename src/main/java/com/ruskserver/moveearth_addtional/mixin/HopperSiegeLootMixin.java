package com.ruskserver.moveearth_addtional.mixin;

import com.ruskserver.moveearth_addtional.s2.nation.NationStorageEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.Hopper;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Hoppers have no actor identity, so contested enemy storage is never an authorized looter. */
@Mixin(HopperBlockEntity.class)
public abstract class HopperSiegeLootMixin {
    @Inject(method = "suckInItems", at = @At("HEAD"), cancellable = true)
    private static void moveearth$blockContestedExtraction(Level level, Hopper hopper,
                                                            CallbackInfoReturnable<Boolean> cir) {
        if (!(level instanceof ServerLevel serverLevel)) return;
        BlockPos source = BlockPos.containing(hopper.getLevelX(), hopper.getLevelY() + 1.0D, hopper.getLevelZ());
        if (level.getBlockState(source).is(NationStorageEvents.STORAGE_BLOCKS)
                && NationStorageEvents.automationRestricted(serverLevel, source)) cir.setReturnValue(false);
    }
}
