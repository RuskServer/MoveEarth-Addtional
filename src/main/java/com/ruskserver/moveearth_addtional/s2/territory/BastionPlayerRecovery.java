package com.ruskserver.moveearth_addtional.s2.territory;

import com.ruskserver.moveearth_addtional.ui.MoveEarthMessage;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

final class BastionPlayerRecovery {
    private BastionPlayerRecovery() {
    }

    static void tick(ServerPlayer player) {
        if (!(player.level() instanceof ServerLevel level)) return;
        BastionPlayerSavedData data = BastionPlayerSavedData.get(player.server);
        boolean restricted = BastionService.isRestricted(player, level, player.blockPosition());

        if (restricted) {
            if (player.isPassenger()) {
                data.requireReturn(player.getUUID());
            } else if (data.returnRequired(player.getUUID())) {
                restore(player, data);
            }
            return;
        }

        data.clearReturn(player.getUUID());
        if (player.tickCount % 20 == 0 && isSafeToRecord(player, level)) {
            data.recordSafe(player.getUUID(), new BastionPlayerSavedData.SafePosition(
                    level.dimension().location(), player.getX(), player.getY(), player.getZ(),
                    player.getYRot(), player.getXRot()));
        }
    }

    static void markMountedInRestrictedTerritory(ServerPlayer player) {
        if (player.level() instanceof ServerLevel level
                && BastionService.isRestricted(player, level, player.blockPosition())) {
            BastionPlayerSavedData.get(player.server).requireReturn(player.getUUID());
        }
    }

    private static boolean restore(ServerPlayer player, BastionPlayerSavedData data) {
        BastionPlayerSavedData.SafePosition safe = data.safePosition(player.getUUID()).orElse(null);
        ServerLevel destination = safe == null ? null : player.server.getLevel(
                ResourceKey.create(Registries.DIMENSION, safe.dimension()));
        if (destination != null) {
            BlockPos savedPos = BlockPos.containing(safe.x(), safe.y(), safe.z());
            destination.getChunk(savedPos.getX() >> 4, savedPos.getZ() >> 4);
        }
        if (destination != null && isStillSafe(player, destination, safe)) {
            player.teleportTo(destination, safe.x(), safe.y(), safe.z(), safe.yaw(), safe.pitch());
            data.clearReturn(player.getUUID());
            player.sendSystemMessage(MoveEarthMessage.warning(Component.translatable(
                    "message.moveearth_addtional.bastion.returned_safe")));
            return true;
        }

        ServerLevel fallback = player.server.overworld();
        BlockPos spawn = fallback.getSharedSpawnPos();
        if (!BastionService.isRestricted(player, fallback, spawn)) {
            player.teleportTo(fallback, spawn.getX() + 0.5D, spawn.getY(), spawn.getZ() + 0.5D,
                    fallback.getSharedSpawnAngle(), 0.0F);
            data.clearReturn(player.getUUID());
            player.sendSystemMessage(MoveEarthMessage.warning(Component.translatable(
                    "message.moveearth_addtional.bastion.returned_spawn")));
            return true;
        }

        BastionService.deny(player, BastionService.Action.NO_SAFE_RETURN);
        return false;
    }

    private static boolean isSafeToRecord(ServerPlayer player, ServerLevel level) {
        return player.onGround() && !player.isPassenger() && level.getWorldBorder().isWithinBounds(player.blockPosition());
    }

    private static boolean isStillSafe(ServerPlayer player, ServerLevel level,
                                       BastionPlayerSavedData.SafePosition safe) {
        BlockPos pos = BlockPos.containing(safe.x(), safe.y(), safe.z());
        if (!level.getWorldBorder().isWithinBounds(pos)
                || BastionService.isRestricted(player, level, pos)) return false;
        AABB movedBounds = player.getBoundingBox().move(
                safe.x() - player.getX(), safe.y() - player.getY(), safe.z() - player.getZ());
        return level.noCollision(player, movedBounds)
                && !level.getBlockState(pos.below()).getCollisionShape(level, pos.below()).isEmpty();
    }
}
