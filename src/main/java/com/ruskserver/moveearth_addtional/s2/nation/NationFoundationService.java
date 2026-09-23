package com.ruskserver.moveearth_addtional.s2.nation;

import com.ruskserver.moveearth_addtional.block.ModBlocks;
import com.ruskserver.moveearth_addtional.block.entity.TerritoryCoreBlockEntity;
import com.ruskserver.moveearth_addtional.s2.territory.TerritorySavedData;
import com.ruskserver.moveearth_addtional.s2.technology.NationTechnologySavedData;
import com.ruskserver.moveearth_addtional.s2.technology.TechnologyDefinition;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

/** Creates a nation and its reserved capital as one server-authoritative operation. */
public final class NationFoundationService {
    public static final int INITIAL_RADIUS = 1;

    private NationFoundationService() { }

    public static Result establish(ServerPlayer player, String name, String tag, long expectedRevision,
                                   ResourceLocation dimension, BlockPos corePos) {
        NationSavedData nations = NationSavedData.get(player.server);
        NationSavedData.Status nationStatus = nations.validateCreate(player.getUUID(), name, tag, expectedRevision);
        if (nationStatus != NationSavedData.Status.CREATED) {
            return new Result(map(nationStatus), nations.revision(), null);
        }
        ServerLevel level = player.server.getLevel(player.level().dimension());
        if (!player.level().dimension().location().equals(dimension) || level == null) {
            // The client named a dimension the player is not standing in, which
            // no verdict describes: it is a stale screen, not a bad site.
            return new Result(Status.INVALID_LOCATION, nations.revision(), null);
        }
        NationFoundationSite.Verdict verdict = NationFoundationSite.judge(read(player, level, corePos));
        if (!verdict.allowed()) {
            return rejected(verdict, nations.revision());
        }
        TerritorySavedData territories = TerritorySavedData.get(player.server);
        if (territories.validateRegistration(UUID.randomUUID(), dimension, corePos, INITIAL_RADIUS)
                != TerritorySavedData.Status.REGISTERED) {
            return new Result(Status.TERRITORY_CONFLICT, nations.revision(), null);
        }

        NationSavedData.CreateResult created = nations.create(player.getUUID(),
                player.getGameProfile().getName(), name, tag, expectedRevision);
        if (created.status() != NationSavedData.Status.CREATED || created.nation() == null) {
            return new Result(map(created.status()), created.revision(), null);
        }
        UUID nationId = created.nation().id();
        if (!level.setBlock(corePos, ModBlocks.TERRITORY_CORE.get().defaultBlockState(), Block.UPDATE_ALL)) {
            nations.rollbackFreshCreation(nationId, player.getUUID());
            return new Result(Status.PLACEMENT_FAILED, nations.revision(), null);
        }
        TerritorySavedData.RegistrationResult registered = territories.register(nationId, player.getUUID(),
                dimension, corePos, INITIAL_RADIUS);
        if (!registered.success()) {
            level.removeBlock(corePos, false);
            nations.rollbackFreshCreation(nationId, player.getUUID());
            return new Result(Status.TERRITORY_CONFLICT, nations.revision(), null);
        }
        if (!(level.getBlockEntity(corePos) instanceof TerritoryCoreBlockEntity core)) {
            territories.remove(dimension, corePos, registered.core().id());
            level.removeBlock(corePos, false);
            nations.rollbackFreshCreation(nationId, player.getUUID());
            return new Result(Status.PLACEMENT_FAILED, nations.revision(), null);
        }
        core.bind(registered.core());
        NationTechnologySavedData technology = NationTechnologySavedData.get(player.server);
        technology.recordObjective(player, TechnologyDefinition.ObjectiveType.JOIN_OR_FOUND_NATION,
                null, 1L, corePos);
        technology.recordObjective(player, TechnologyDefinition.ObjectiveType.TERRITORY_ACTION,
                ResourceLocation.fromNamespaceAndPath("moveearth_addtional", "found_capital"), 1L, corePos);
        com.ruskserver.moveearth_addtional.advancement.ModCriteria.trigger(player,
                com.ruskserver.moveearth_addtional.advancement.ModCriteria.NATION_CITIZEN);
        com.ruskserver.moveearth_addtional.advancement.ModCriteria.trigger(player,
                com.ruskserver.moveearth_addtional.advancement.ModCriteria.NATION_FOUNDED);
        return new Result(Status.CREATED, nations.revision(), registered.core());
    }

    /**
     * Reads the world at a candidate site. The sight trace uses
     * {@link ClipContext.Block#COLLIDER} on purpose: grass, flowers and a single
     * snow layer have an outline but no collision, and a trace that stopped on
     * them would report the plant as the ground.
     */
    private static NationFoundationSite.Reading read(ServerPlayer player, ServerLevel level, BlockPos pos) {
        if (!level.hasChunkAt(pos)) return NationFoundationSite.Reading.unloaded();
        boolean withinWorld = !level.isOutsideBuildHeight(pos)
                && level.getWorldBorder().isWithinBounds(pos);
        boolean withinReach = player.distanceToSqr(Vec3.atCenterOf(pos))
                <= NationFoundationSite.REACH_SQR;
        BlockPos support = pos.below();
        BlockHitResult sight = level.clip(new ClipContext(player.getEyePosition(),
                Vec3.atCenterOf(support), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        boolean sightClear = sight.getType() == HitResult.Type.BLOCK
                && sight.getBlockPos().equals(support);
        boolean onVehicle = false;
        try {
            onVehicle = Sable.HELPER.getContaining(level, pos) instanceof ServerSubLevel;
        } catch (RuntimeException | LinkageError ignored) {
            // Sable is optional at runtime; normal-world validation remains authoritative.
        }
        BlockState target = level.getBlockState(pos);
        boolean occupiedByEntity = !level.getEntities((net.minecraft.world.entity.Entity) null,
                new AABB(pos), entity -> !entity.isSpectator()).isEmpty();
        return new NationFoundationSite.Reading(true, withinWorld, withinReach, sightClear, onVehicle,
                target.canBeReplaced(), !level.getFluidState(pos).isEmpty(),
                level.getBlockState(support).isFaceSturdy(level, support, Direction.UP),
                occupiedByEntity);
    }

    private static Status map(NationSavedData.Status status) {
        return switch (status) {
            case CREATED -> Status.CREATED;
            case INVALID -> Status.INVALID;
            case DUPLICATE -> Status.DUPLICATE;
            case ALREADY_MEMBER -> Status.ALREADY_MEMBER;
            case STALE -> Status.STALE;
        };
    }

    private static Result rejected(NationFoundationSite.Verdict verdict, long revision) {
        return new Result(Status.INVALID_LOCATION, revision, null, verdict);
    }

    public enum Status {
        CREATED, INVALID, DUPLICATE, ALREADY_MEMBER, STALE,
        INVALID_LOCATION, TERRITORY_CONFLICT, PLACEMENT_FAILED
    }

    /**
     * @param verdict why a site was refused; null unless the status is
     *                {@link Status#INVALID_LOCATION}. It is carried rather than
     *                folded into the status so the player is told the one thing
     *                that is wrong instead of a list of what might be.
     */
    public record Result(Status status, long revision, TerritorySavedData.CoreRecord core,
                         NationFoundationSite.Verdict verdict) {
        public Result(Status status, long revision, TerritorySavedData.CoreRecord core) {
            this(status, revision, core, null);
        }

        public boolean success() { return status == Status.CREATED; }
    }
}
