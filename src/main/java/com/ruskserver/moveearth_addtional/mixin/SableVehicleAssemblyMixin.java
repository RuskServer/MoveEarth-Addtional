package com.ruskserver.moveearth_addtional.mixin;

import com.ruskserver.moveearth_addtional.block.entity.VehicleCoreBlockEntity;
import com.ruskserver.moveearth_addtional.compat.vehicle.SableVehicleTopology;
import com.ruskserver.moveearth_addtional.compat.vehicle.VehicleAssemblyGuard;
import com.ruskserver.moveearth_addtional.s2.reinforcement.ReinforcementEntry;
import com.ruskserver.moveearth_addtional.s2.reinforcement.ReinforcementSavedData;
import com.ruskserver.moveearth_addtional.s2.vehicle.VehicleSavedData;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.companion.math.BoundingBox3ic;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import com.ruskserver.moveearth_addtional.Moveearth_addtional;

/** Atomically moves reinforcement metadata with Sable's exact assembly block set. */
@Pseudo
@Mixin(value = SubLevelAssemblyHelper.class, remap = false)
public abstract class SableVehicleAssemblyMixin {
    private static final ThreadLocal<ArrayDeque<AssemblyState>> MOVEARTH$ASSEMBLIES =
            ThreadLocal.withInitial(ArrayDeque::new);

    @Inject(method = "assembleBlocks", at = @At("HEAD"))
    private static void moveearth$captureVehicle(ServerLevel level, BlockPos anchor,
                                                 Iterable<BlockPos> positions, BoundingBox3ic bounds,
                                                 CallbackInfoReturnable<ServerSubLevel> callback) {
        List<BlockPos> moved = new ArrayList<>();
        Map<BlockPos, ReinforcementEntry> reinforcements = new LinkedHashMap<>();
        List<VehicleCoreBlockEntity> cores = new ArrayList<>();
        ReinforcementSavedData data = ReinforcementSavedData.get(level);
        for (BlockPos raw : positions) {
            BlockPos pos = raw.immutable();
            moved.add(pos);
            data.get(pos).ifPresent(entry -> reinforcements.put(pos, entry));
            if (level.getBlockEntity(pos) instanceof VehicleCoreBlockEntity core && core.vehicleId() != null) {
                cores.add(core);
            }
        }
        UUID inherited = Sable.HELPER.getContaining(level, anchor) instanceof ServerSubLevel
                ? SableVehicleTopology.at(level, anchor).map(context -> context.vehicle().id()).orElse(null)
                : null;
        boolean conflictingCore = cores.size() > 1 || inherited != null && !cores.isEmpty()
                && !inherited.equals(cores.getFirst().vehicleId());
        UUID vehicleId = conflictingCore ? null : cores.isEmpty() ? inherited : cores.getFirst().vehicleId();
        if (conflictingCore) {
            Moveearth_addtional.LOGGER.warn("Sable assembly at {} contains conflicting vehicle cores; mobile reinforcement was not attached", anchor);
        } else if (!reinforcements.isEmpty() && vehicleId == null) {
            Moveearth_addtional.LOGGER.warn("Sable assembly at {} moved reinforced blocks without a vehicle core; reinforcement remains disabled", anchor);
        }
        VehicleAssemblyGuard.begin(new java.util.LinkedHashSet<>(moved));
        ArrayDeque<AssemblyState> stack = MOVEARTH$ASSEMBLIES.get();
        stack.clear();
        stack.push(new AssemblyState(anchor.immutable(), moved,
                reinforcements, vehicleId, !cores.isEmpty()));
    }

    @Inject(method = "assembleBlocks", at = @At("RETURN"))
    private static void moveearth$finishVehicle(ServerLevel level, BlockPos anchor,
                                                Iterable<BlockPos> positions, BoundingBox3ic bounds,
                                                CallbackInfoReturnable<ServerSubLevel> callback) {
        ArrayDeque<AssemblyState> stack = MOVEARTH$ASSEMBLIES.get();
        AssemblyState state = stack.isEmpty() ? null : stack.pop();
        if (stack.isEmpty()) MOVEARTH$ASSEMBLIES.remove();
        VehicleAssemblyGuard.end();
        ServerSubLevel subLevel = callback.getReturnValue();
        if (state == null || subLevel == null || state.vehicleId == null) return;
        BlockPos destinationAnchor = subLevel.getPlot().getCenterBlock();
        ReinforcementSavedData data = ReinforcementSavedData.get(level);
        for (Map.Entry<BlockPos, ReinforcementEntry> value : state.reinforcements.entrySet()) {
            BlockPos destination = destinationAnchor.offset(value.getKey().subtract(state.anchor));
            data.remove(value.getKey());
            data.put(destination, value.getValue());
        }
        SableVehicleTopology.bind(subLevel, state.vehicleId);
        if (state.containsCore) {
            for (BlockPos source : state.positions) {
                BlockPos destination = destinationAnchor.offset(source.subtract(state.anchor));
                if (!(level.getBlockEntity(destination) instanceof VehicleCoreBlockEntity core)
                        || !state.vehicleId.equals(core.vehicleId())) continue;
                VehicleSavedData.VehicleRecord moved = VehicleSavedData.get(level.getServer()).move(
                        state.vehicleId, level.dimension().location(), destination, subLevel.getUniqueId());
                if (moved != null) core.bind(moved);
                break;
            }
        }
    }

    private record AssemblyState(BlockPos anchor, List<BlockPos> positions,
                                 Map<BlockPos, ReinforcementEntry> reinforcements,
                                 UUID vehicleId, boolean containsCore) { }
}
