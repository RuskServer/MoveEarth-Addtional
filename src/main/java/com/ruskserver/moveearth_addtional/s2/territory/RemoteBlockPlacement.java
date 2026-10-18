package com.ruskserver.moveearth_addtional.s2.territory;

import com.ruskserver.moveearth_addtional.compat.vehicle.SableVehicleTopology;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.UUID;

/**
 * The Bastion rule for blocks placed at a distance by a projectile, which skips the placement event that
 * {@link BastionEvents} listens to. A player is held to what they could place by hand; a shot with no one behind
 * it, such as a redstone-fired launcher, to what a machine may carry ({@link BastionPolicy#blocksMachineMovement}).
 */
public final class RemoteBlockPlacement {
    private RemoteBlockPlacement() {
    }

    /**
     * @param shooter  the player who fired, or null for an unattended launcher
     * @param launcher where an unattended shot came from, which decides the nation it fires for
     * @param tell     whether to tell a refused shooter why, for callers that say nothing themselves
     * @return whether the block may not go at {@code pos}
     */
    public static boolean refused(ServerLevel level, @Nullable ServerPlayer shooter, @Nullable BlockPos launcher,
                                  BlockPos pos, boolean tell) {
        if (shooter != null) {
            if (!BastionService.isRestricted(shooter, level, pos)) return false;
            if (tell) BastionService.deny(shooter, BastionService.Action.BLOCK_PLACE);
            return true;
        }
        TerritorySavedData territories = TerritorySavedData.get(level.getServer());
        Optional<UUID> target = territories.controllingNation(level.getServer(), level.dimension().location(), pos);
        if (target.isEmpty()) return false;
        return BastionPolicy.blocksMachineMovement(launcherNation(level, territories, launcher), target,
                NationUpkeepService.penalty(level.getServer(), target.get()).bastionEnabled());
    }

    /** The nation whose vehicle carries the launcher, or else whose land it stands on. */
    private static Optional<UUID> launcherNation(ServerLevel level, TerritorySavedData territories,
                                                 @Nullable BlockPos launcher) {
        if (launcher == null) return Optional.empty();
        Optional<UUID> vehicleNation = SableVehicleTopology.at(level, launcher)
                .map(context -> context.vehicle().nationId());
        if (vehicleNation.isPresent()) return vehicleNation;
        return territories.controllingNation(level.getServer(), level.dimension().location(), launcher);
    }
}
