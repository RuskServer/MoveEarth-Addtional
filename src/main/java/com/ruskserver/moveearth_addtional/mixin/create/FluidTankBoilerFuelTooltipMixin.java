package com.ruskserver.moveearth_addtional.mixin.create;

import com.ruskserver.moveearth_addtional.compat.create.SteamBoilerFuel;
import com.simibubi.create.content.fluids.tank.FluidTankBlockEntity;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/** Appends the steam boiler's fuel use to Create's boiler goggle readout. */
@Mixin(value = FluidTankBlockEntity.class, remap = false)
public abstract class FluidTankBoilerFuelTooltipMixin {
    @Inject(method = "addToGoggleTooltip", at = @At("RETURN"))
    private void moveearth$appendBoilerFuel(List<Component> tooltip, boolean isPlayerSneaking,
                                            CallbackInfoReturnable<Boolean> callback) {
        if (!callback.getReturnValueZ()) return;
        FluidTankBlockEntity boiler = ((FluidTankBlockEntity) (Object) this).getControllerBE();
        if (boiler != null && boiler.boiler.isActive()) SteamBoilerFuel.appendGoggleTooltip(boiler, tooltip);
    }
}
