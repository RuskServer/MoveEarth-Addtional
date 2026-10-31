package com.ruskserver.moveearth_addtional.mixin.aeronautics;

import com.ruskserver.moveearth_addtional.compat.aeronautics.BalloonLeakPolicy;
import com.ruskserver.moveearth_addtional.config.AeronauticsSwivelConfig;
import dev.eriksonn.aeronautics.content.blocks.hot_air.balloon.Balloon;
import dev.eriksonn.aeronautics.content.blocks.hot_air.lifting_gas.LiftingGasType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * A holed balloon loses the hot air it can no longer hold slowly ({@link BalloonLeakPolicy}), so its crew has time
 * to set down or patch it. Aeronautics drained it over 9 seconds, which left nothing to do mid-air.
 */
@Pseudo
@Mixin(targets = "dev.eriksonn.aeronautics.content.blocks.hot_air.balloon.ServerBalloon", remap = false)
public abstract class ServerBalloonLeakMixin {
    @Shadow private boolean leaking;
    @Shadow private double totalTargetVolume;

    @Redirect(method = "updateGasAmounts", at = @At(value = "INVOKE",
            target = "Ldev/eriksonn/aeronautics/content/blocks/hot_air/lifting_gas/LiftingGasType;getEmptyingTime()D"))
    private double moveearth$leakSlowly(LiftingGasType type) {
        return BalloonLeakPolicy.emptyingTicks(AeronauticsSwivelConfig.balloonHolesEnabled(), type.getEmptyingTime(),
                this.leaking, ((Balloon) (Object) this).getCapacity(), this.totalTargetVolume,
                AeronauticsSwivelConfig.balloonLeakSeconds());
    }
}
