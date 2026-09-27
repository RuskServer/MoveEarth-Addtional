package com.ruskserver.moveearth_addtional.mixin.create;

import com.ruskserver.moveearth_addtional.compat.create.SteamBoilerFuel;
import com.ruskserver.moveearth_addtional.config.CreateIndustryConfig;
import com.simibubi.create.content.fluids.tank.FluidTankBlockEntity;
import com.simibubi.create.content.kinetics.steamEngine.PoweredShaftBlockEntity;
import com.simibubi.create.content.kinetics.steamEngine.SteamEngineBlockEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Reports how much of its network's stress capacity each steam engine is
 * delivering, so the boiler's burners can burn fuel in proportion
 * ({@link SteamBoilerFuel}). Read-only: the engine's own tick is untouched.
 */
@Mixin(value = SteamEngineBlockEntity.class, remap = false)
public abstract class SteamEngineFuelLoadMixin {
    @Unique private static final int REPORT_INTERVAL = 5;

    @Shadow public abstract FluidTankBlockEntity getTank();
    @Shadow public abstract PoweredShaftBlockEntity getShaft();
    @Shadow public abstract boolean isValid();

    @Inject(method = "tick", at = @At("HEAD"))
    private void moveearth$reportBoilerLoad(CallbackInfo callback) {
        BlockEntity self = (BlockEntity) (Object) this;
        Level level = self.getLevel();
        if (level == null || level.isClientSide() || !CreateIndustryConfig.steamFuelEnabled()) return;
        long gameTime = level.getGameTime();
        if (Math.floorMod(gameTime + self.getBlockPos().hashCode(), REPORT_INTERVAL) != 0) return;
        if (!isValid()) return;
        FluidTankBlockEntity boiler = getTank();
        if (boiler == null) return;
        PoweredShaftBlockEntity shaft = getShaft();
        if (shaft == null || !shaft.isPoweredBy(self.getBlockPos())) {
            // An engine with nothing to turn is an idle load on its boiler.
            SteamBoilerFuel.reportEngine(boiler, 0.0F, 0.0F, gameTime);
            return;
        }
        KineticStressAccess network = (KineticStressAccess) shaft;
        SteamBoilerFuel.reportEngine(boiler,
                network.moveearth$getNetworkStress(), network.moveearth$getNetworkCapacity(), gameTime);
    }
}
