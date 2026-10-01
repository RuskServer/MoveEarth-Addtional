package com.ruskserver.moveearth_addtional.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.ruskserver.moveearth_addtional.s2.time.ClosedHoursFreezeService;
import com.ruskserver.moveearth_addtional.s2.time.ClosedHoursFreeze;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.GameRules;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Masks cycle checks without persisting temporary values into level.dat. */
@Mixin(ServerLevel.class)
public abstract class ClosedHoursWorldCycleMixin {
    @WrapOperation(method = {"tickTime", "advanceWeatherCycle", "tick"}, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/GameRules;getBoolean(Lnet/minecraft/world/level/GameRules$Key;)Z"))
    private boolean moveearth$pauseCycles(GameRules rules, GameRules.Key<GameRules.BooleanValue> key,
                                         Operation<Boolean> original) {
        boolean allowed = original.call(rules, key);
        if (key == GameRules.RULE_DAYLIGHT || key == GameRules.RULE_WEATHER_CYCLE) {
            return ClosedHoursFreeze.allowCycle(allowed,
                    ClosedHoursFreezeService.isFrozen(((ServerLevel) (Object) this).getServer()));
        }
        return allowed;
    }
}
