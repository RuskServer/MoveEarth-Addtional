package com.ruskserver.moveearth_addtional.mixin.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/** Replaces vanilla's narrator-only first-run page with MoveEarth's complete setup flow. */
@Mixin(Minecraft.class)
public abstract class MinecraftAccessibilityOnboardingMixin {
    @Shadow @Final public Options options;

    @Inject(method = "addInitialScreens", at = @At("HEAD"))
    private void moveearth$useCustomFirstRunSetup(List<?> initialScreens, CallbackInfo callback) {
        if (options.onboardAccessibility) options.onboardingAccessibilityFinished();
    }
}
