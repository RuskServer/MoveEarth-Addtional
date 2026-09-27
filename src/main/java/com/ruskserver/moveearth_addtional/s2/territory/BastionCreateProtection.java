package com.ruskserver.moveearth_addtional.s2.territory;

import com.ruskserver.moveearth_addtional.s2.reinforcement.ReinforcementSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Server-side checks for Create actions that bypass vanilla placement events. */
public final class BastionCreateProtection {
    private BastionCreateProtection() { }

    public static boolean denyGlueSelection(ServerPlayer player, BlockPos from, BlockPos to) {
        if (!(player.level() instanceof ServerLevel level) || from == null || to == null) return false;
        // Keep the same packet bounds as Create before iterating chunks; malformed coordinates must be cheap.
        if (!player.canInteractWithBlock(to, 2.0D) || !to.closerThan(from, 25.0D)) return false;
        // Glue spans can cross a chunk boundary even when both selected blocks are outside it.
        int minX = Math.min(from.getX(), to.getX());
        int maxX = Math.max(from.getX(), to.getX()) + 1;
        int minZ = Math.min(from.getZ(), to.getZ());
        int maxZ = Math.max(from.getZ(), to.getZ()) + 1;
        for (int chunkX = minX >> 4; chunkX <= maxX >> 4; chunkX++) {
            for (int chunkZ = minZ >> 4; chunkZ <= maxZ >> 4; chunkZ++) {
                if (!BastionService.isRestricted(player, level,
                        new BlockPos(chunkX << 4, from.getY(), chunkZ << 4))) continue;
                BastionService.deny(player, BastionService.Action.GLUE);
                return true;
            }
        }
        return false;
    }

    /** Validate before Create removes captured blocks from the world. */
    public static boolean canAssemblePiston(ServerLevel level, BlockPos actuator, BlockPos anchor,
                                            Direction facing, int initialProgress, int extensionLength,
                                            Set<BlockPos> localBlocks) {
        if (localBlocks.isEmpty()) return true;
        int maxProgress = Math.max(initialProgress, extensionLength);
        // Corrupt or unexpected actuator state must not turn assembly into an unbounded scan.
        if (initialProgress < 0 || maxProgress > 256) return false;
        TerritorySavedData territories = TerritorySavedData.get(level.getServer());
        Optional<UUID> actuatorNation = territories.controllingNation(
                level.getServer(), level.dimension().location(), actuator);
        ReinforcementSavedData reinforcements = ReinforcementSavedData.get(level);
        Map<Long, Optional<UUID>> ownersByChunk = new HashMap<>();
        for (BlockPos local : localBlocks) {
            BlockPos source = anchor.offset(local).relative(facing, initialProgress);
            if (reinforcements.get(source).filter(entry -> entry.enabled()).isPresent()) return false;
            if (territories.core(level.dimension().location(), source).isPresent()) return false;
            for (int progress = 0; progress <= maxProgress; progress++) {
                BlockPos destination = anchor.offset(local).relative(facing, progress);
                long chunkKey = ChunkPos.asLong(destination.getX() >> 4, destination.getZ() >> 4);
                Optional<UUID> targetNation = ownersByChunk.computeIfAbsent(chunkKey, ignored ->
                        territories.controllingNation(level.getServer(), level.dimension().location(), destination));
                if (targetNation.isPresent() && BastionPolicy.blocksMachineMovement(actuatorNation, targetNation,
                        NationUpkeepService.penalty(level.getServer(), targetNation.get()).bastionEnabled())) {
                    return false;
                }
            }
        }
        return true;
    }
}
