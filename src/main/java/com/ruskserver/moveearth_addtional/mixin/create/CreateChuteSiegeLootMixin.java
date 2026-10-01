package com.ruskserver.moveearth_addtional.mixin.create;

import com.ruskserver.moveearth_addtional.s2.nation.NationStorageEvents;
import com.simibubi.create.content.logistics.chute.ChuteBlockEntity;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Stops chutes from becoming an unattended loot-right bypass. */
@Mixin(value = ChuteBlockEntity.class, remap = false)
public abstract class CreateChuteSiegeLootMixin {
    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void moveearth$blockContestedAutomation(CallbackInfo ci) {
        net.minecraft.world.level.block.entity.BlockEntity self =
                (net.minecraft.world.level.block.entity.BlockEntity) (Object) this;
        if (!(self.getLevel() instanceof ServerLevel level)) return;
        if (NationStorageEvents.adjacentAutomationRestricted(level, self.getBlockPos())) ci.cancel();
    }
}
