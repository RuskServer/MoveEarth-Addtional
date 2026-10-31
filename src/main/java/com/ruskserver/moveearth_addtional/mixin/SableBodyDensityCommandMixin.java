package com.ruskserver.moveearth_addtional.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.ruskserver.moveearth_addtional.compat.vehicle.SableBodyDensityLimit;
import com.ruskserver.moveearth_addtional.ui.MoveEarthMessage;
import dev.ryanhcode.sable.companion.math.BoundingBox3ic;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Sable's admin assembly/shatter commands must not bypass the ordinary assembler budget. */
@Pseudo
@Mixin(targets = "dev.ryanhcode.sable.command.SableAssembleCommands", remap = false)
public abstract class SableBodyDensityCommandMixin {
    @WrapOperation(method = {"executeAssembleAreaCommand", "executeAssembleCubeCommand",
            "executeAssembleConnectedCommand", "executeAssembleSphereCommand"},
            at = @At(value = "INVOKE", target = "Ldev/ryanhcode/sable/api/SubLevelAssemblyHelper;assembleBlocks(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/BlockPos;Ljava/lang/Iterable;Ldev/ryanhcode/sable/companion/math/BoundingBox3ic;)Ldev/ryanhcode/sable/sublevel/ServerSubLevel;"),
            remap = false)
    private static ServerSubLevel moveearth$reviewCommand(ServerLevel level, BlockPos anchor,
            Iterable<BlockPos> blocks, BoundingBox3ic bounds, Operation<ServerSubLevel> original,
            @Local(argsOnly = true) CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        Component refusal = SableBodyDensityLimit.review(level, anchor, context.getSource().getPlayer());
        if (refusal != null) throw new SimpleCommandExceptionType(MoveEarthMessage.warning(refusal)).create();
        return original.call(level, anchor, blocks, bounds);
    }

    @Inject(method = "shatterBlockToSubLevel", at = @At("HEAD"), cancellable = true, remap = false)
    private static void moveearth$limitShatter(ServerLevel level, BlockPos anchor,
                                               CallbackInfoReturnable<Boolean> callback) {
        if (SableBodyDensityLimit.review(level, anchor, null) != null) callback.setReturnValue(false);
    }
}
