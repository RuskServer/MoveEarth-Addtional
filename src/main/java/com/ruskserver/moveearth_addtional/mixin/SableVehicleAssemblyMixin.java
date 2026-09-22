package com.ruskserver.moveearth_addtional.mixin;

import com.ruskserver.moveearth_addtional.block.entity.VehicleCoreBlockEntity;
import com.ruskserver.moveearth_addtional.compat.vehicle.SableVehicleTopology;
import com.ruskserver.moveearth_addtional.compat.vehicle.VehicleAssemblyGuard;
import com.ruskserver.moveearth_addtional.compat.vehicle.AssemblyState;
import com.ruskserver.moveearth_addtional.s2.reinforcement.ReinforcementEntry;
import com.ruskserver.moveearth_addtional.s2.reinforcement.ReinforcementSavedData;
import com.ruskserver.moveearth_addtional.s2.vehicle.VehicleSavedData;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.companion.math.BoundingBox3ic;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
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
    @Unique
    private static final TagKey<Block> MOVEARTH$DEPOSIT_BLOCKS = TagKey.create(
            Registries.BLOCK,
            ResourceLocation.fromNamespaceAndPath("create_rns", "deposit_blocks"));

    @Unique
    private static final ThreadLocal<ArrayDeque<AssemblyState>> MOVEARTH$ASSEMBLIES =
            ThreadLocal.withInitial(ArrayDeque::new);

    /**
     * Leaves ore deposits in the ground.
     *
     * <p>A deposit is an ordinary block -- no block entity, so none of Create's
     * protections for blocks that carry data apply to it, and it is not tagged
     * immovable. The resource <em>is</em> those blocks, which the miner counts
     * as it works. Glue a few to a hull, assemble, fly home, set it down, and
     * the deposit has moved: whichever region was given that resource no longer
     * has it, and whichever one flew there does.
     *
     * <p>That would empty the whole regional allocation of meaning, so they are
     * dropped from the block set before anything is built. Excluded rather than
     * refused: the ship assembles without them and the deposit stays where it
     * was, which needs no error to explain and leaves nothing half-built.
     *
     * <p>Guarded whether or not the glue can currently reach one. Finding out
     * by experiment would cost a test; finding out by being wrong costs the
     * resource map.
     */
    @ModifyVariable(method = "assembleBlocks", at = @At("HEAD"), argsOnly = true, remap = false)
    private static Iterable<BlockPos> moveearth$leaveDepositsBehind(Iterable<BlockPos> positions,
                                                                    ServerLevel level) {
        List<BlockPos> kept = new ArrayList<>();
        boolean removedAny = false;
        for (BlockPos raw : positions) {
            if (level.getBlockState(raw).is(MOVEARTH$DEPOSIT_BLOCKS)) {
                removedAny = true;
                continue;
            }
            kept.add(raw.immutable());
        }
        if (!removedAny) {
            return positions;
        }
        Moveearth_addtional.LOGGER.debug("Left {} deposit block(s) in the ground during assembly",
                kept.size());
        return kept;
    }

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
        if (state == null || subLevel == null || state.vehicleId() == null) return;
        BlockPos destinationAnchor = subLevel.getPlot().getCenterBlock();
        ReinforcementSavedData data = ReinforcementSavedData.get(level);
        for (Map.Entry<BlockPos, ReinforcementEntry> value : state.reinforcements().entrySet()) {
            BlockPos destination = destinationAnchor.offset(value.getKey().subtract(state.anchor()));
            data.remove(value.getKey());
            data.put(destination, value.getValue());
        }
        for (BlockPos source : state.positions()) {
            data.copyRepairDelay(source, destinationAnchor.offset(source.subtract(state.anchor())), level.getGameTime());
        }
        SableVehicleTopology.bind(subLevel, state.vehicleId());
        if (state.containsCore()) {
            for (BlockPos source : state.positions()) {
                BlockPos destination = destinationAnchor.offset(source.subtract(state.anchor()));
                if (!(level.getBlockEntity(destination) instanceof VehicleCoreBlockEntity core)
                        || !state.vehicleId().equals(core.vehicleId())) continue;
                VehicleSavedData.VehicleRecord moved = VehicleSavedData.get(level.getServer()).move(
                        state.vehicleId(), level.dimension().location(), destination, subLevel.getUniqueId());
                if (moved != null) core.bind(moved);
                break;
            }
        }
    }

}
