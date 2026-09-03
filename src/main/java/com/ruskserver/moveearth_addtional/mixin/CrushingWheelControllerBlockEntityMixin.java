package com.ruskserver.moveearth_addtional.mixin;

import com.ruskserver.moveearth_addtional.jobs.GunDisassemblyAttribution;
import com.ruskserver.moveearth_addtional.jobs.GunSmithJobService;
import com.simibubi.create.content.kinetics.crusher.CrushingWheelControllerBlockEntity;
import com.simibubi.create.content.processing.recipe.ProcessingInventory;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.UUID;

@Mixin(value = CrushingWheelControllerBlockEntity.class, remap = false)
public abstract class CrushingWheelControllerBlockEntityMixin {
    @Shadow public ProcessingInventory inventory;
    @Shadow public abstract net.minecraft.world.level.Level getLevel();
    @Shadow public abstract BlockPos getBlockPos();

    @Inject(method = "intakeItem", at = @At("HEAD"), remap = false)
    private void moveearth$recordOwner(ItemEntity entity, CallbackInfo ci) {
        if (getLevel() instanceof ServerLevel level) {
            GunDisassemblyAttribution.record(level, getBlockPos(), entity, entity.getItem());
        }
    }

    @Inject(method = "applyRecipe", at = @At("HEAD"), remap = false)
    private void moveearth$awardDisassembly(CallbackInfo ci) {
        if (!(getLevel() instanceof ServerLevel level) || inventory == null) return;
        ItemStack input = inventory.getStackInSlot(0);
        if (com.tacz.guns.api.item.IGun.getIGunOrNull(input) == null) return;
        UUID playerId = GunDisassemblyAttribution.consume(level, getBlockPos());
        if (playerId == null) return;
        ServerPlayer player = level.getServer().getPlayerList().getPlayer(playerId);
        if (player != null) GunSmithJobService.awardDisassembly(player, input);
    }
}
