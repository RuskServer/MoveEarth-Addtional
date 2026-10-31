package com.ruskserver.moveearth_addtional.mixin;

import com.b1n_ry.yigd.block.entity.GraveBlockEntity;
import com.ruskserver.moveearth_addtional.compat.yigd.YigdTerritoryCompat;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** YIGD binds at initial generation/load and updates position after a moved block resurfaces. */
@Pseudo
@Mixin(targets = "com.b1n_ry.yigd.block.entity.GraveBlockEntity", remap = false)
public abstract class YigdGravePlacementMixin {
    @Inject(method = "setComponent", at = @At("RETURN"), remap = false)
    private void moveearth$bound(CallbackInfo callback) {
        YigdTerritoryCompat.placed((GraveBlockEntity) (Object) this);
    }

    @Inject(method = "updatePosition", at = @At("RETURN"), remap = false)
    private void moveearth$relocated(ServerLevel level, BlockPos pos, CallbackInfo callback) {
        YigdTerritoryCompat.placed((GraveBlockEntity) (Object) this);
    }
}
