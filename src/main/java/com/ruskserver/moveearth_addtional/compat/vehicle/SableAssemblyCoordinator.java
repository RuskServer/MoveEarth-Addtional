package com.ruskserver.moveearth_addtional.compat.vehicle;

import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import com.ruskserver.moveearth_addtional.block.entity.VehicleCoreBlockEntity;
import com.ruskserver.moveearth_addtional.s2.nation.NationSavedData;
import com.ruskserver.moveearth_addtional.s2.reinforcement.ReinforcementSavedData;
import com.ruskserver.moveearth_addtional.s2.vehicle.VehicleSavedData;
import com.ruskserver.moveearth_addtional.ui.MoveEarthMessage;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;

import java.util.ArrayDeque;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Decides and reports what one Sable assembly lifts; called from
 * {@code SableVehicleAssemblyMixin} (every assembly) and
 * {@code SimulatedAssemblyHelperMixin} (Physics Assembler and bearings, which
 * can still refuse).
 *
 * <p>Block metadata (reinforcement, battle damage, core records) is not moved
 * here but by {@link SableBlockRelocation}, on Sable's {@code moveBlocks},
 * which assembly and every kind of disassembly share.
 */
public final class SableAssemblyCoordinator {
    /** Ore deposits stay in the ground; see {@code SableVehicleAssemblyMixin}. */
    public static final TagKey<Block> DEPOSIT_BLOCKS = TagKey.create(Registries.BLOCK,
            ResourceLocation.fromNamespaceAndPath("create_rns", "deposit_blocks"));
    /** Players this close to the assembler hear why it refused or what it left behind. */
    private static final double NOTIFY_RADIUS = 48.0D;
    private static final int STALE_STACK_LIMIT = 16;
    private static final ThreadLocal<ArrayDeque<AssemblyState>> ASSEMBLIES = ThreadLocal.withInitial(ArrayDeque::new);

    private SableAssemblyCoordinator() { }

    private record Inspection(SableAssemblyExclusions.Result filtered, VehicleAssemblyPolicy.Binding binding,
                              int liftedCores, int liftedReinforced, boolean fromWorld) {
        VehicleAssemblyPolicy.Refusal refusal() {
            return VehicleAssemblyPolicy.refusal(filtered.tally(), binding);
        }
    }

    private static Inspection inspect(ServerLevel level, BlockPos origin, Iterable<BlockPos> positions,
                                      ServerPlayer actor) {
        UUID actorNation = actor == null ? null
                : NationSavedData.get(level.getServer()).nationIdFor(actor.getUUID()).orElse(null);
        SableAssemblyExclusions.Result filtered = SableAssemblyExclusions.filter(level, origin, positions,
                DEPOSIT_BLOCKS, actorNation);
        ReinforcementSavedData reinforcements = ReinforcementSavedData.get(level);
        VehicleSavedData vehicles = VehicleSavedData.get(level.getServer());
        int reinforced = 0;
        Set<UUID> cores = new LinkedHashSet<>();
        for (BlockPos pos : filtered.kept()) {
            if (reinforcements.get(pos).isPresent()) reinforced++;
            if (level.getBlockEntity(pos) instanceof VehicleCoreBlockEntity core && core.vehicleId() != null
                    && vehicles.vehicle(core.vehicleId()).isPresent()) {
                cores.add(core.vehicleId());
            }
        }
        boolean fromWorld = !(Sable.HELPER.getContaining(level, origin) instanceof ServerSubLevel);
        UUID inherited = fromWorld ? null
                : SableVehicleTopology.at(level, origin).map(context -> context.vehicle().id()).orElse(null);
        return new Inspection(filtered, VehicleAssemblyPolicy.binding(cores, inherited), cores.size(), reinforced,
                fromWorld);
    }

    /**
     * Simulated's assembler, before anything has changed in the world. Returns the reason the
     * assembly must not happen, already told to the players around it, or null to go ahead.
     */
    public static Component review(ServerLevel level, BlockPos origin, Iterable<BlockPos> blocks, ServerPlayer actor) {
        Inspection inspection = inspect(level, origin, blocks, actor);
        Component reason = switch (inspection.refusal()) {
            case NONE -> null;
            case OWN_BLOCKS_LEFT_BEHIND -> Component.translatable(
                    "message.moveearth_addtional.vehicle_assembly.refused_own_blocks",
                    inspection.filtered().tally().own());
            case MULTIPLE_CORES -> Component.translatable(
                    "message.moveearth_addtional.vehicle_assembly.refused_multiple_cores", inspection.liftedCores());
            case CORE_CONFLICT -> Component.translatable(
                    "message.moveearth_addtional.vehicle_assembly.refused_core_conflict");
        };
        if (reason != null) notifyNearby(level, origin, actor, MoveEarthMessage.error(reason));
        return reason;
    }

    /**
     * Sable's {@code assembleBlocks}, at its head: filters the set and remembers the binding.
     * Returns the block set Sable must lift instead of {@code positions}.
     */
    public static Iterable<BlockPos> begin(ServerLevel level, BlockPos anchor, Iterable<BlockPos> positions) {
        AssemblyRequests.Request request = AssemblyRequests.forBlocks(positions);
        BlockPos origin = request == null ? anchor.immutable() : request.origin();
        ServerPlayer actor = request == null ? null : request.actor();
        Inspection inspection = inspect(level, origin, positions, actor);
        VehicleAssemblyPolicy.Tally tally = inspection.filtered().tally();
        if (tally.any()) {
            Moveearth_addtional.LOGGER.info("Sable assembly at {} left {} fixed, {} foreign and {} own protected block(s) in place",
                    origin, tally.fixtures(), tally.foreign(), tally.own());
            notifyNearby(level, origin, actor, MoveEarthMessage.warning(Component.translatable(
                    "message.moveearth_addtional.vehicle_assembly.left_behind",
                    tally.total(), tally.fixtures(), tally.foreign() + tally.own())));
        }
        if (inspection.binding().conflicting()) {
            // Only reachable where nobody could refuse (Sable commands and splits, or cores that
            // reached the set after Simulated's review): bind neither, so neither record governs.
            Moveearth_addtional.LOGGER.warn("Sable assembly at {} contains conflicting vehicle cores ({}); the body was not bound",
                    origin, inspection.binding().conflict());
            notifyNearby(level, origin, actor, MoveEarthMessage.warning(Component.translatable(
                    "message.moveearth_addtional.vehicle_assembly.cores_unbound")));
        }
        Iterable<BlockPos> lifted = inspection.filtered().removedAny() ? inspection.filtered().kept() : positions;
        ArrayDeque<AssemblyState> stack = ASSEMBLIES.get();
        if (stack.size() >= STALE_STACK_LIMIT) stack.clear();
        stack.push(new AssemblyState(lifted, origin, inspection.binding(), inspection.liftedReinforced(),
                inspection.fromWorld(), request));
        return lifted;
    }

    /** Sable's {@code assembleBlocks}, on return. */
    public static void finish(ServerLevel level, Iterable<BlockPos> lifted, ServerSubLevel subLevel) {
        AssemblyState state = pop(lifted);
        if (state == null || subLevel == null) return;
        UUID vehicleId = state.binding().vehicleId();
        if (vehicleId != null) SableVehicleTopology.bind(subLevel, vehicleId);
        if (state.fromWorld() && VehicleAssemblyPolicy.reinforcementInactive(state.liftedReinforced(), vehicleId)) {
            notifyNearby(level, state.origin(), state.request() == null ? null : state.request().actor(),
                    MoveEarthMessage.warning(Component.translatable(
                            "message.moveearth_addtional.vehicle_assembly.reinforcement_inactive",
                            state.liftedReinforced())));
        }
    }

    /** Whether an {@code assembleBlocks} call is in progress on this thread. */
    public static boolean assembling() {
        return !ASSEMBLIES.get().isEmpty();
    }

    private static AssemblyState pop(Iterable<BlockPos> lifted) {
        ArrayDeque<AssemblyState> stack = ASSEMBLIES.get();
        boolean matched = stack.stream().anyMatch(state -> state.lifted() == lifted);
        AssemblyState found = stack.poll();
        // States above the matching one belong to calls that threw before returning.
        while (matched && found != null && found.lifted() != lifted) found = stack.poll();
        if (stack.isEmpty()) ASSEMBLIES.remove();
        return found;
    }

    private static void notifyNearby(ServerLevel level, BlockPos origin, ServerPlayer actor, Component message) {
        Set<ServerPlayer> recipients = new LinkedHashSet<>();
        if (actor != null && !actor.hasDisconnected()) recipients.add(actor);
        for (ServerPlayer player : level.players()) {
            if (SableVehicleTopology.distanceSquared(level, player, origin) <= NOTIFY_RADIUS * NOTIFY_RADIUS) {
                recipients.add(player);
            }
        }
        for (ServerPlayer player : recipients) player.sendSystemMessage(message);
    }
}
