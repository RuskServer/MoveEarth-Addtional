package com.ruskserver.moveearth_addtional.compat.vehicle;

import com.ruskserver.moveearth_addtional.s2.vehicle.VehicleSavedData;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.SubLevelHelper;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.server.level.ServerPlayer;
import com.ruskserver.moveearth_addtional.s2.reinforcement.ReinforcementSavedData;

import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Resolves one vehicle core across Sable bodies connected by hinges, bearings and swivels. */
public final class SableVehicleTopology {
    private static final String VEHICLE_ID = "MoveEarthVehicleId";
    private SableVehicleTopology() { }

    public static Optional<VehicleContext> at(ServerLevel level, BlockPos plotPos) {
        SubLevel containing = Sable.HELPER.getContaining(level, plotPos);
        if (!(containing instanceof ServerSubLevel serverSubLevel)) return Optional.empty();
        Set<UUID> bodies = new LinkedHashSet<>();
        Set<UUID> candidates = new LinkedHashSet<>();
        for (SubLevel body : SubLevelHelper.getConnectedChain(serverSubLevel)) {
            if (!(body instanceof ServerSubLevel serverBody) || body.isRemoved()) continue;
            bodies.add(body.getUniqueId());
            CompoundTag userData = serverBody.getUserDataTag();
            if (userData == null) continue;
            if (userData.hasUUID(VEHICLE_ID)) candidates.add(userData.getUUID(VEHICLE_ID));
        }
        if (candidates.size() != 1) return Optional.empty();
        UUID vehicleId = candidates.iterator().next();
        VehicleSavedData.VehicleRecord record = VehicleSavedData.get(level.getServer())
                .vehicle(vehicleId).orElse(null);
        // Detached armored fragments retain metadata, but not protection: the chain must still contain the core body.
        if (record == null || record.subLevelId() == null || !bodies.contains(record.subLevelId())) {
            return Optional.empty();
        }
        return Optional.of(new VehicleContext(record, Set.copyOf(bodies)));
    }

    public static void bind(ServerSubLevel subLevel, UUID vehicleId) {
        CompoundTag current = subLevel.getUserDataTag();
        CompoundTag tag = current == null ? new CompoundTag() : current.copy();
        tag.putUUID(VEHICLE_ID, vehicleId);
        subLevel.setUserDataTag(tag);
    }

    public static double distanceSquared(ServerLevel level, Entity entity, BlockPos pos) {
        return Sable.HELPER.distanceSquaredWithSubLevels(level, entity.position(),
                pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D);
    }

    public static java.util.List<ReinforcementSavedData.LocatedEntry> entriesForPlayer(
            ServerPlayer player, ReinforcementSavedData data) {
        SubLevel tracked = Sable.HELPER.getTrackingOrVehicleSubLevel(player);
        if (!(tracked instanceof ServerSubLevel serverTracked)) return java.util.List.of();
        VehicleContext context = at(player.serverLevel(), serverTracked.getPlot().getCenterBlock()).orElse(null);
        if (context == null) return java.util.List.of();
        java.util.List<ReinforcementSavedData.LocatedEntry> result = new java.util.ArrayList<>();
        for (SubLevel body : SubLevelHelper.getConnectedChain(serverTracked)) {
            if (!(body instanceof ServerSubLevel serverBody) || !context.connectedBodies().contains(body.getUniqueId())) continue;
            var bounds = serverBody.getPlot().getBoundingBox();
            result.addAll(data.inside(player.serverLevel(), bounds.minX(), bounds.minY(), bounds.minZ(),
                    bounds.maxX(), bounds.maxY(), bounds.maxZ()));
            if (result.size() >= 8192) break;
        }
        return result.size() <= 8192 ? java.util.List.copyOf(result) : java.util.List.copyOf(result.subList(0, 8192));
    }

    public static VehicleStatistics statistics(ServerLevel level, BlockPos plotPos) {
        SubLevel containing = Sable.HELPER.getContaining(level, plotPos);
        VehicleContext context = at(level, plotPos).orElse(null);
        if (!(containing instanceof ServerSubLevel serverContaining) || context == null) {
            return new VehicleStatistics(1, 0);
        }
        ReinforcementSavedData data = ReinforcementSavedData.get(level);
        int reinforced = 0;
        for (SubLevel body : SubLevelHelper.getConnectedChain(serverContaining)) {
            if (!(body instanceof ServerSubLevel serverBody)
                    || !context.connectedBodies().contains(body.getUniqueId())) continue;
            var bounds = serverBody.getPlot().getBoundingBox();
            reinforced += data.inside(level, bounds.minX(), bounds.minY(), bounds.minZ(),
                    bounds.maxX(), bounds.maxY(), bounds.maxZ()).size();
        }
        return new VehicleStatistics(context.connectedBodies().size(), reinforced);
    }

    public record VehicleContext(VehicleSavedData.VehicleRecord vehicle, Set<UUID> connectedBodies) { }
    public record VehicleStatistics(int connectedBodies, int reinforcedBlocks) { }
}
