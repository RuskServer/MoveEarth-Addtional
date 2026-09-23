package com.ruskserver.moveearth_addtional.network;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/** Cheap checks that must run before a C2S packet dereferences a client-provided position. */
final class ServerPacketGuards {
    private ServerPacketGuards() {
    }

    static boolean canAccessLoadedBlock(ServerPlayer player, BlockPos pos, double maxDistanceSqr) {
        if (player == null || pos == null) return false;
        ServerLevel level = player.serverLevel();
        return !level.isOutsideBuildHeight(pos)
                && level.getWorldBorder().isWithinBounds(pos)
                && level.hasChunkAt(pos)
                && player.position().distanceToSqr(Vec3.atCenterOf(pos)) <= maxDistanceSqr;
    }
}
