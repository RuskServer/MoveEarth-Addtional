package com.ruskserver.moveearth_addtional.mixin;

import com.ruskserver.moveearth_addtional.pvp.PvpMatchManager;
import net.minecraft.network.protocol.game.ServerboundTeleportToEntityPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A PvP player waiting out the killcam or on the deploy screen is a spectator, and vanilla lets a spectator
 * teleport to any entity. Pulled back a tick later, they would still have seen the chunks and players around
 * it: a radar for every enemy. PvP participants may not use it.
 */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ServerGamePacketListenerPvpSpectateMixin {
    @Shadow public ServerPlayer player;

    /** After the packet is handed to the server thread, where the match state lives. */
    @Inject(method = "handleTeleportToEntityPacket", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/server/level/ServerLevel;)V",
            shift = At.Shift.AFTER), cancellable = true)
    private void moveearth$noSpectatorTeleportInPvp(ServerboundTeleportToEntityPacket packet, CallbackInfo ci) {
        if (this.player != null && PvpMatchManager.INSTANCE.isActive(this.player)) ci.cancel();
    }
}
