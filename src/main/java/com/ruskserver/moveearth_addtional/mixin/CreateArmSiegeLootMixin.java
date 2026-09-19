package com.ruskserver.moveearth_addtional.mixin;

import com.ruskserver.moveearth_addtional.s2.nation.NationStorageEvents;
import com.simibubi.create.content.kinetics.mechanicalArm.ArmBlockEntity;
import com.simibubi.create.content.kinetics.mechanicalArm.ArmInteractionPoint;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Mechanical arms have no player identity and cannot claim an attacker's loot entitlement. */
@Mixin(value = ArmInteractionPoint.class, remap = false)
public abstract class CreateArmSiegeLootMixin {
    @Shadow protected Level level;
    @Shadow protected BlockPos pos;

    @Inject(method = "extract(Lcom/simibubi/create/content/kinetics/mechanicalArm/ArmBlockEntity;IIZ)Lnet/minecraft/world/item/ItemStack;",
            at = @At("HEAD"), cancellable = true)
    private void moveearth$blockContestedExtraction(ArmBlockEntity arm, int slot, int amount,
                                                     boolean simulate,
                                                     CallbackInfoReturnable<ItemStack> cir) {
        if (level instanceof ServerLevel serverLevel && level.getBlockState(pos).is(NationStorageEvents.STORAGE_BLOCKS)
                && NationStorageEvents.automationRestricted(serverLevel, pos)) cir.setReturnValue(ItemStack.EMPTY);
    }
}
