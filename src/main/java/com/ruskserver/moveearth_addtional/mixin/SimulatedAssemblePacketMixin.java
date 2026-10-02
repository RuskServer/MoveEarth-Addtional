package com.ruskserver.moveearth_addtional.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.ruskserver.moveearth_addtional.compat.vehicle.AssemblyRequests;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Remembers which player pulled a Physics Assembler's lever while Simulated
 * handles it, so an assembly that would tear that player's nation's own hull
 * can be told apart from one that leaves a neighbour's wall behind.
 *
 * <p>{@code AssemblePacket.handle(ServerPacketContext)} reads the sender with
 * {@code context.player().level()} before calling
 * {@code PhysicsAssemblerBlockEntity.assembleOrDisassemble()} synchronously
 * (Simulated 1.3.1 and 1.3.2). The context type belongs to Veil, which is not on
 * this mod's compile classpath, so the player is taken from that {@code level()}
 * call instead. Optional: without it the assembly is only ever filtered, never
 * refused for the nation's own blocks.
 */
@Pseudo
@Mixin(targets = "dev.simulated_team.simulated.network.packets.AssemblePacket", remap = false)
public abstract class SimulatedAssemblePacketMixin {
    @WrapOperation(method = "handle", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/level/ServerPlayer;level()Lnet/minecraft/world/level/Level;"),
            require = 0, remap = false)
    private Level moveearth$rememberActor(ServerPlayer player, Operation<Level> original) {
        AssemblyRequests.actor(player);
        return original.call(player);
    }

    @Inject(method = "handle", at = @At("RETURN"), require = 0, remap = false)
    private void moveearth$forgetActor(CallbackInfo callback) {
        AssemblyRequests.clearActor();
    }
}
