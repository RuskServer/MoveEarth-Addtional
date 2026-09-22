package com.ruskserver.moveearth_addtional.warehouse;

import com.ruskserver.moveearth_addtional.region.RegionResolver;
import com.ruskserver.moveearth_addtional.s2.territory.TerritoryPreviewArea;
import com.ruskserver.moveearth_addtional.s2.territory.TerritorySavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.Container;
import net.minecraft.world.level.block.state.BlockState;

/** Validates a WorldEdit paste before it can be registered as a protected site. */
public final class WarehouseSiteService {
    private WarehouseSiteService() { }

    public static Result inspect(ServerLevel level, BlockPos min, boolean requirePaste) {
        if (!level.dimension().equals(Level.OVERWORLD)) return new Result(Status.OVERWORLD_ONLY, 0);
        if (!RegionResolver.ready()) return new Result(Status.REGION_MAP_UNAVAILABLE, 0);
        BlockPos max = min.offset(WarehouseSitePolicy.WIDTH - 1,
                WarehouseSitePolicy.HEIGHT - 1, WarehouseSitePolicy.LENGTH - 1);
        if (level.isOutsideBuildHeight(min) || level.isOutsideBuildHeight(max)
                || !level.getWorldBorder().isWithinBounds(min)
                || !level.getWorldBorder().isWithinBounds(max)) {
            return new Result(Status.OUTSIDE_WORLD, 0);
        }
        for (int x = min.getX() >> 4; x <= max.getX() >> 4; x++) {
            for (int z = min.getZ() >> 4; z <= max.getZ() >> 4; z++) {
                if (!level.hasChunk(x, z)) return new Result(Status.CHUNK_NOT_LOADED, 0);
            }
        }
        BlockPos spawn = level.getSharedSpawnPos();
        long spawnDx = (long) spawn.getX() - min.getX();
        long spawnDz = (long) spawn.getZ() - min.getZ();
        if (spawnDx * spawnDx + spawnDz * spawnDz < 512L * 512L) {
            return new Result(Status.TOO_CLOSE_TO_SPAWN, 0);
        }
        for (int x : new int[] {0, WarehouseSitePolicy.WIDTH / 2, WarehouseSitePolicy.WIDTH - 1}) {
            for (int z : new int[] {0, WarehouseSitePolicy.LENGTH / 2, WarehouseSitePolicy.LENGTH - 1}) {
                BlockPos support = min.offset(x, -1, z);
                if (!level.getFluidState(support).isEmpty()
                        || !level.getBlockState(support).isFaceSturdy(level, support,
                        net.minecraft.core.Direction.UP)) {
                    return new Result(Status.UNSUPPORTED_TERRAIN, 0);
                }
            }
        }
        int region = RegionResolver.regionAt(min.getX(), min.getZ());
        if (region <= 0) return new Result(Status.NO_REGION, 0);
        for (BlockPos corner : new BlockPos[] {
                min, min.offset(WarehouseSitePolicy.WIDTH - 1, 0, 0),
                min.offset(0, 0, WarehouseSitePolicy.LENGTH - 1),
                min.offset(WarehouseSitePolicy.WIDTH - 1, 0, WarehouseSitePolicy.LENGTH - 1) }) {
            if (RegionResolver.regionAt(corner.getX(), corner.getZ()) != region
                    || RegionResolver.continentAt(corner.getX(), corner.getZ()) <= 0) {
                return new Result(Status.REGION_EDGE_OR_WATER, region);
            }
        }
        WarehouseSites sites = WarehouseSites.get(level.getServer());
        if (sites.hasRegion(region)) return new Result(Status.REGION_OCCUPIED, region);
        if (sites.all().stream().anyMatch(site -> site.dimension().equals(level.dimension().location())
                && WarehouseSitePolicy.overlaps(site.min().getX(), site.min().getZ(),
                min.getX(), min.getZ()))) {
            return new Result(Status.TOO_CLOSE_TO_SITE, region);
        }
        for (TerritorySavedData.CoreRecord core : TerritorySavedData.get(level.getServer()).cores()) {
            if (!core.dimension().equals(level.dimension().location())) continue;
            TerritoryPreviewArea area = new TerritoryPreviewArea(core.pos().getX() >> 4,
                    core.pos().getZ() >> 4, core.radius());
            if (WarehouseSitePolicy.intersectsClaim(min.getX(), min.getZ(),
                    area.minBlockX(), area.maxBlockXExclusive(),
                    area.minBlockZ(), area.maxBlockZExclusive())) {
                return new Result(Status.TERRITORY_CONFLICT, region);
            }
        }
        for (TerritorySavedData.VaultChunk vault : TerritorySavedData.get(level.getServer()).vaultChunks()) {
            if (!vault.dimension().equals(level.dimension().location())) continue;
            TerritoryPreviewArea area = new TerritoryPreviewArea(vault.chunkX(), vault.chunkZ(), 0);
            if (WarehouseSitePolicy.intersectsClaim(min.getX(), min.getZ(),
                    area.minBlockX(), area.maxBlockXExclusive(),
                    area.minBlockZ(), area.maxBlockZExclusive())) {
                return new Result(Status.TERRITORY_CONFLICT, region);
            }
        }
        if (requirePaste && !looksLikeWarehouse(level, min)) {
            return new Result(Status.PASTE_NOT_FOUND, region);
        }
        return new Result(Status.READY, region);
    }

    public static Result register(ServerLevel level, BlockPos min) {
        Result inspected = inspect(level, min, true);
        if (!inspected.success()) return inspected;
        WarehouseSites.Site pending = WarehouseSites.get(level.getServer()).pendingPlacement(inspected.regionId());
        if (pending != null && (!pending.dimension().equals(level.dimension().location())
                || !pending.min().equals(min))) {
            return new Result(Status.PLACEMENT_RECOVERY_LOCATION_MISMATCH, inspected.regionId());
        }
        boolean added = WarehouseSites.get(level.getServer()).add(new WarehouseSites.Site(
                inspected.regionId(), level.dimension().location(), min.immutable()));
        return added ? inspected : new Result(Status.REGION_OCCUPIED, inspected.regionId());
    }

    /** A cheap paste witness, not a claim that every decorative block matches the original. */
    private static boolean looksLikeWarehouse(ServerLevel level, BlockPos min) {
        int copycats = 0;
        int containers = 0;
        int girders = 0;
        for (int x = 0; x < WarehouseSitePolicy.WIDTH; x++) {
            for (int y = 0; y < WarehouseSitePolicy.HEIGHT; y++) {
                for (int z = 0; z < WarehouseSitePolicy.LENGTH; z++) {
                    BlockPos pos = min.offset(x, y, z);
                    BlockState state = level.getBlockState(pos);
                    if (level.getBlockEntity(pos) instanceof Container container && !container.isEmpty()) {
                        return false;
                    }
                    ResourceLocation id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
                    if (id == null) continue;
                    if (id.getNamespace().equals("copycats") && level.getBlockEntity(pos) != null) copycats++;
                    if (id.getNamespace().equals("createdeco") && id.getPath().contains("shipping_container")) containers++;
                    if (id.getNamespace().equals("dndecor") && id.getPath().contains("girder")) girders++;
                }
            }
        }
        return copycats >= 100 && containers >= 5 && girders >= 1;
    }

    public enum Status {
        READY, OVERWORLD_ONLY, REGION_MAP_UNAVAILABLE, OUTSIDE_WORLD, CHUNK_NOT_LOADED,
        TOO_CLOSE_TO_SPAWN, UNSUPPORTED_TERRAIN, NO_REGION, REGION_EDGE_OR_WATER,
        REGION_OCCUPIED, TOO_CLOSE_TO_SITE,
        TERRITORY_CONFLICT, PASTE_NOT_FOUND, PLACEMENT_RECOVERY_LOCATION_MISMATCH
    }

    public record Result(Status status, int regionId) {
        public boolean success() { return status == Status.READY; }
    }
}
