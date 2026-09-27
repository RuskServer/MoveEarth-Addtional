package com.ruskserver.moveearth_addtional.mixin.client;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.blaze3d.vertex.PoseStack;
import com.ruskserver.moveearth_addtional.client.compat.TaczArmRenderContext;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.HumanoidArm;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;

@Pseudo
@Mixin(targets = "com.tacz.guns.util.RenderHelper", remap = false)
public abstract class TaczMekaSuitArmContextMixin {
    @WrapMethod(method = "renderFirstPersonArm", remap = false)
    private static void moveearth$renderGunArm(LocalPlayer player, HumanoidArm arm, PoseStack pose,
                                               int light, Operation<Void> original) {
        TaczArmRenderContext.render(() -> original.call(player, arm, pose, light));
    }
}
