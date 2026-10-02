package com.ruskserver.moveearth_addtional.compat.vehicle;

import com.ruskserver.moveearth_addtional.block.entity.VehicleCoreBlockEntity;
import com.ruskserver.moveearth_addtional.s2.reinforcement.ReinforcementSavedData;
import com.ruskserver.moveearth_addtional.s2.reinforcement.ReinforcementService;
import com.ruskserver.moveearth_addtional.s2.vehicle.VehicleSavedData;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import java.util.ArrayDeque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Carries MoveEarth's per-block metadata along with every block Sable moves.
 *
 * <p>Hooked on {@code SubLevelAssemblyHelper.moveBlocks}, the one routine that
 * relocates blocks for assembly ({@code assembleBlocks}) and for every
 * disassembly in Simulated ({@code SimAssemblyHelper.disassembleSubLevel}:
 * Physics Assembler, swivel bearing, merging glue). Each source maps to its
 * destination through Sable's own transform, rotation included, so the
 * reinforcement, battle damage and vehicle core record land exactly where the
 * block does -- in a plot, back in the world, or in a partner body.
 */
@net.neoforged.fml.common.EventBusSubscriber(modid = com.ruskserver.moveearth_addtional.Moveearth_addtional.MODID)
public final class SableBlockRelocation {
    private static final int STALE_STACK_LIMIT = 16;
    private static final ThreadLocal<ArrayDeque<Pending>> MOVES = ThreadLocal.withInitial(ArrayDeque::new);

    private SableBlockRelocation() { }

    private record CoreMove(BlockPos from, BlockPos to, UUID vehicleId) { }

    private record Pending(SubLevelAssemblyHelper.AssemblyTransform transform, ServerLevel source,
                           ServerLevel destination, Map<BlockPos, BlockPos> moves, List<CoreMove> cores) { }

    /** Head of {@code moveBlocks}: snapshot the moves and guard the cores being lifted. */
    public static void begin(ServerLevel level, SubLevelAssemblyHelper.AssemblyTransform transform,
                             Iterable<BlockPos> positions) {
        ServerLevel destination = transform.getLevel() == null ? level : transform.getLevel();
        Map<BlockPos, BlockPos> moves = new LinkedHashMap<>();
        for (BlockPos raw : positions) {
            BlockPos source = raw.immutable();
            moves.putIfAbsent(source, transform.apply(source).immutable());
        }
        List<CoreMove> cores = BlockRelocationPlan.<BlockPos, UUID>of(moves.keySet(), moves::get,
                        source -> level.getBlockEntity(source) instanceof VehicleCoreBlockEntity core
                                ? core.vehicleId() : null)
                .moves().stream().map(move -> new CoreMove(move.from(), move.to(), move.value())).toList();
        ArrayDeque<Pending> stack = MOVES.get();
        if (stack.size() >= STALE_STACK_LIMIT) discard(stack, stack.size());
        VehicleAssemblyGuard.begin(moves.keySet());
        stack.push(new Pending(transform, level, destination, moves, cores));
    }

    /** Return of {@code moveBlocks}: the blocks are in place, move what belongs to them. */
    public static void finish(SubLevelAssemblyHelper.AssemblyTransform transform) {
        ArrayDeque<Pending> stack = MOVES.get();
        Pending pending = null;
        // Entries above the matching one belong to moves that threw before returning.
        if (stack.stream().anyMatch(candidate -> candidate.transform() == transform)) {
            while (pending == null || pending.transform() != transform) {
                pending = stack.pop();
                VehicleAssemblyGuard.end();
            }
        }
        if (stack.isEmpty()) MOVES.remove();
        if (pending == null) return;
        long now = pending.source().getGameTime();
        List<BlockPos> changed = ReinforcementSavedData.get(pending.source()).transfer(pending.moves(),
                ReinforcementSavedData.get(pending.destination()), now);
        if (!changed.isEmpty()) {
            ReinforcementService.syncChangedNearbyManagers(pending.destination(), changed);
        }
        moveCoreRecords(pending);
    }

    private static void moveCoreRecords(Pending pending) {
        if (pending.cores().isEmpty()) return;
        ServerLevel level = pending.destination();
        VehicleSavedData vehicles = VehicleSavedData.get(level.getServer());
        for (CoreMove move : pending.cores()) {
            if (vehicles.vehicle(move.vehicleId()).isEmpty()
                    || !(level.getBlockEntity(move.to()) instanceof VehicleCoreBlockEntity core)
                    || !move.vehicleId().equals(core.vehicleId())) continue;
            ServerSubLevel body = Sable.HELPER.getContaining(level, move.to()) instanceof ServerSubLevel serverBody
                    && !serverBody.isRemoved() ? serverBody : null;
            VehicleSavedData.VehicleRecord moved = vehicles.move(move.vehicleId(), level.dimension().location(),
                    move.to(), body == null ? null : body.getUniqueId());
            if (moved == null) continue;
            core.bind(moved);
            // Assembly binds its new body itself (SableAssemblyCoordinator.finish); a merge into a partner
            // body must not leave the core on a body nothing identifies as this vehicle.
            if (body != null && !SableAssemblyCoordinator.assembling()) {
                SableVehicleTopology.bindIfUnbound(level, body, move.vehicleId());
            }
        }
    }

    /**
     * A move never spans ticks, so anything still pending at the end of one belongs to a
     * move that threw. Left there, its cores would stay guarded and breaking one would
     * skip removing its record.
     */
    @net.neoforged.bus.api.SubscribeEvent
    public static void onServerTick(net.neoforged.neoforge.event.tick.ServerTickEvent.Post event) {
        ArrayDeque<Pending> stack = MOVES.get();
        if (stack.isEmpty()) {
            MOVES.remove();
            return;
        }
        discard(stack, stack.size());
        MOVES.remove();
    }

    private static void discard(ArrayDeque<Pending> stack, int count) {
        for (int i = 0; i < count && !stack.isEmpty(); i++) {
            stack.pop();
            VehicleAssemblyGuard.end();
        }
    }
}
