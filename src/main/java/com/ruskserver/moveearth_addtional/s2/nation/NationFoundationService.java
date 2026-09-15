package com.ruskserver.moveearth_addtional.s2.nation;

import com.ruskserver.moveearth_addtional.block.ModBlocks;
import com.ruskserver.moveearth_addtional.block.entity.TerritoryCoreBlockEntity;
import com.ruskserver.moveearth_addtional.s2.territory.TerritorySavedData;
import com.ruskserver.moveearth_addtional.s2.technology.NationTechnologySavedData;
import com.ruskserver.moveearth_addtional.s2.technology.TechnologyDefinition;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.core.BlockPos;
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
        ResourceLocation civicReadiness = ResourceLocation.fromNamespaceAndPath(
                "moveearth_addtional", "personal/civic_readiness");
        if (!player.hasPermissions(2) && !NationTechnologySavedData.get(player.server)
                .completedFor(player.getUUID(), null).contains(civicReadiness)) {
            return new Result(Status.TECHNOLOGY_REQUIRED, nations.revision(), null);
        }
        NationSavedData.Status nationStatus = nations.validateCreate(player.getUUID(), name, tag, expectedRevision);
        if (nationStatus != NationSavedData.Status.CREATED) {
            return new Result(map(nationStatus), nations.revision(), null);
        }
        if (!player.level().dimension().location().equals(dimension)) {
            return new Result(Status.INVALID_LOCATION, nations.revision(), null);
        }
        ServerLevel level = player.server.getLevel(player.level().dimension());
        if (level == null || !validLocation(player, level, corePos)) {
            return new Result(Status.INVALID_LOCATION, nations.revision(), null);
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
        return new Result(Status.CREATED, nations.revision(), registered.core());
    }

    private static boolean validLocation(ServerPlayer player, ServerLevel level, BlockPos pos) {
        if (!level.hasChunkAt(pos) || level.isOutsideBuildHeight(pos)
                || !level.getWorldBorder().isWithinBounds(pos)
                || player.distanceToSqr(Vec3.atCenterOf(pos)) > 100.0D) return false;
        Vec3 eye = player.getEyePosition();
        BlockHitResult sight = level.clip(new ClipContext(eye, Vec3.atCenterOf(pos.below()),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        if (sight.getType() != HitResult.Type.BLOCK || !sight.getBlockPos().equals(pos.below())) return false;
        try {
            if (Sable.HELPER.getContaining(level, pos) instanceof ServerSubLevel) return false;
        } catch (RuntimeException | LinkageError ignored) {
            // Sable is optional at runtime; normal-world validation remains authoritative.
        }
        BlockState target = level.getBlockState(pos);
        if (!target.canBeReplaced() || !level.getFluidState(pos).isEmpty()) return false;
        BlockPos support = pos.below();
        if (!level.getBlockState(support).isFaceSturdy(level, support, net.minecraft.core.Direction.UP)) return false;
        return level.getEntities((net.minecraft.world.entity.Entity) null, new AABB(pos),
                entity -> !entity.isSpectator()).isEmpty();
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

    public enum Status {
        CREATED, INVALID, DUPLICATE, ALREADY_MEMBER, STALE,
        INVALID_LOCATION, TERRITORY_CONFLICT, PLACEMENT_FAILED, TECHNOLOGY_REQUIRED
    }

    public record Result(Status status, long revision, TerritorySavedData.CoreRecord core) {
        public boolean success() { return status == Status.CREATED; }
    }
}
