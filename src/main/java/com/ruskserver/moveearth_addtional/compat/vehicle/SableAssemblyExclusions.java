package com.ruskserver.moveearth_addtional.compat.vehicle;

import com.ruskserver.moveearth_addtional.block.ModBlocks;
import com.ruskserver.moveearth_addtional.block.entity.VehicleCoreBlockEntity;
import com.ruskserver.moveearth_addtional.s2.reinforcement.ReinforcementSavedData;
import com.ruskserver.moveearth_addtional.s2.territory.TerritorySavedData;
import com.ruskserver.moveearth_addtional.s2.vehicle.VehicleSavedData;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Applies {@link AssemblyExclusionPolicy} to the block set Sable is about to assemble. */
public final class SableAssemblyExclusions {
    private SableAssemblyExclusions() { }

    /** Blocks that are lifted, and how many stay behind per reason. */
    public record Result(List<BlockPos> kept, VehicleAssemblyPolicy.Tally tally) {
        public boolean removedAny() { return tally.any(); }
    }

    /**
     * @param origin      where the assembly was started (the assembler); decides the assembling side
     * @param actorNation nation of the player who started it, or null; only used to tell the
     *                    nation's own blocks apart from foreign ones among those left behind
     */
    public static Result filter(ServerLevel level, BlockPos origin, Iterable<BlockPos> positions,
                                TagKey<Block> deposits, UUID actorNation) {
        Owners owners = new Owners(level);
        UUID assemblingSide = owners.at(origin).orElse(null);
        ReinforcementSavedData reinforcements = ReinforcementSavedData.get(level);
        TerritorySavedData territories = TerritorySavedData.get(level.getServer());
        VehicleSavedData vehicles = VehicleSavedData.get(level.getServer());
        ResourceLocation dimension = level.dimension().location();
        List<BlockPos> kept = new ArrayList<>();
        VehicleAssemblyPolicy.Tally tally = VehicleAssemblyPolicy.Tally.EMPTY;
        for (BlockPos raw : positions) {
            BlockPos pos = raw.immutable();
            AssemblyExclusionPolicy.Kind kind = kind(level, pos, deposits, reinforcements, territories,
                    vehicles, dimension);
            UUID owner = kind == AssemblyExclusionPolicy.Kind.NATION_PROTECTED ? owners.at(pos).orElse(null) : null;
            VehicleAssemblyPolicy.Placement placement = VehicleAssemblyPolicy.place(kind, owner, assemblingSide,
                    actorNation);
            if (placement == VehicleAssemblyPolicy.Placement.KEPT) kept.add(pos);
            else tally = tally.with(placement);
        }
        return new Result(kept, tally);
    }

    private static AssemblyExclusionPolicy.Kind kind(ServerLevel level, BlockPos pos, TagKey<Block> deposits,
                                                     ReinforcementSavedData reinforcements,
                                                     TerritorySavedData territories, VehicleSavedData vehicles,
                                                     ResourceLocation dimension) {
        BlockState state = level.getBlockState(pos);
        if (state.is(deposits) || state.is(ModBlocks.TERRITORY_CORE.get())
                || state.is(ModBlocks.MARKET_STATION.get()) || state.is(ModBlocks.STORAGE_WRECKAGE.get())
                || state.is(ModBlocks.PRISON_INTAKE.get())
                || territories.core(dimension, pos).isPresent()) {
            return AssemblyExclusionPolicy.Kind.FIXTURE;
        }
        if (reinforcements.get(pos).filter(entry -> entry.enabled()).isPresent()) {
            return AssemblyExclusionPolicy.Kind.NATION_PROTECTED;
        }
        if (state.is(ModBlocks.VEHICLE_CORE.get())
                && level.getBlockEntity(pos) instanceof VehicleCoreBlockEntity core
                && vehicles.vehicle(core.vehicleId()).isPresent()) {
            return AssemblyExclusionPolicy.Kind.NATION_PROTECTED;
        }
        return AssemblyExclusionPolicy.Kind.ORDINARY;
    }

    /**
     * Who owns a position where it stands: the governing vehicle's nation inside
     * a Sable body, the controlling territory otherwise. Cached per body and per
     * chunk because armored hulls put thousands of protected blocks in one set.
     */
    private static final class Owners {
        private final ServerLevel level;
        private final TerritorySavedData territories;
        private final Map<UUID, Optional<UUID>> bodies = new HashMap<>();
        private final Map<Long, Optional<UUID>> chunks = new HashMap<>();

        private Owners(ServerLevel level) {
            this.level = level;
            this.territories = TerritorySavedData.get(level.getServer());
        }

        private Optional<UUID> at(BlockPos pos) {
            SubLevel containing = Sable.HELPER.getContaining(level, pos);
            if (containing instanceof ServerSubLevel body) {
                Optional<UUID> cached = bodies.get(body.getUniqueId());
                if (cached != null) return cached;
                Optional<UUID> owner = SableVehicleTopology.at(level, pos)
                        .map(context -> context.vehicle().nationId());
                bodies.put(body.getUniqueId(), owner);
                return owner;
            }
            long chunk = ChunkPos.asLong(pos.getX() >> 4, pos.getZ() >> 4);
            Optional<UUID> cached = chunks.get(chunk);
            if (cached != null) return cached;
            Optional<UUID> owner = territories.controllingNation(level.getServer(), level.dimension().location(), pos);
            chunks.put(chunk, owner);
            return owner;
        }
    }
}
