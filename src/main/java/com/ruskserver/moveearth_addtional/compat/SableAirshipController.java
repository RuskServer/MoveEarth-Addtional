package com.ruskserver.moveearth_addtional.compat;

import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import com.ruskserver.moveearth_addtional.raid.RaidAirshipBlueprint;
import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class SableAirshipController {
    private SableAirshipController() {
    }

    public static Optional<UUID> create(ServerPlayer target, int raidId) {
        ServerLevel level = target.serverLevel();
        BlockPos anchor = findAssemblyPosition(level, target);
        if (anchor == null) return Optional.empty();

        List<BlockPos> blocks = new ArrayList<>();
        try {
            buildAirship(level, anchor, blocks);
            fillCargo(level, blocks);
            BoundingBox3i bounds = boundsOf(blocks);
            ServerSubLevel subLevel = SubLevelAssemblyHelper.assembleBlocks(level, anchor, blocks, bounds);
            subLevel.setName("MoveEarth Raid #" + raidId);
            ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
            if (container != null) container.physicsSystem().getPipeline().resetVelocity(subLevel);
            return Optional.of(subLevel.getUniqueId());
        } catch (RuntimeException exception) {
            Moveearth_addtional.LOGGER.error("Failed to assemble Sable airship for raid {}", raidId, exception);
            for (BlockPos pos : blocks) {
                level.removeBlock(pos, false);
            }
            return Optional.empty();
        }
    }

    public static boolean moveToward(ServerLevel level, UUID shipId, Vector3d destination, double maxStep) {
        ServerSubLevel subLevel = get(level, shipId);
        if (subLevel == null || subLevel.isRemoved()) return false;
        Vector3d current = new Vector3d(subLevel.logicalPose().position());
        Vector3d delta = destination.sub(current, new Vector3d());
        if (delta.lengthSquared() > maxStep * maxStep) delta.normalize(maxStep);
        Vector3d next = current.add(delta);
        ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null) return false;
        container.physicsSystem().getPipeline().resetVelocity(subLevel);
        container.physicsSystem().getPipeline().teleport(subLevel, next, subLevel.logicalPose().orientation());
        return true;
    }

    public static Vector3d dropPosition(ServerLevel level, UUID shipId) {
        ServerSubLevel subLevel = get(level, shipId);
        if (subLevel == null) return null;
        return new Vector3d(subLevel.logicalPose().position()).add(0.0D, -8.0D, 0.0D);
    }

    public static boolean beginCrash(ServerLevel level, UUID shipId, int destroyedCores) {
        ServerSubLevel subLevel = get(level, shipId);
        if (subLevel == null || subLevel.isRemoved()) return false;
        RigidBodyHandle handle = RigidBodyHandle.of(subLevel);
        if (handle == null) return false;
        double roll = destroyedCores % 2 == 0 ? 0.18D : -0.18D;
        handle.addLinearAndAngularVelocity(new Vector3d(0.0D, -5.5D, 1.0D), new Vector3d(roll, 0.04D, 0.08D));
        return true;
    }

    public static void continueCrash(ServerLevel level, UUID shipId) {
        ServerSubLevel subLevel = get(level, shipId);
        if (subLevel == null || subLevel.isRemoved()) return;
        RigidBodyHandle handle = RigidBodyHandle.of(subLevel);
        if (handle != null) handle.addLinearAndAngularVelocity(new Vector3d(0.0D, -0.12D, 0.0D), new Vector3d());
    }

    public static Vector3d position(ServerLevel level, UUID shipId) {
        ServerSubLevel subLevel = get(level, shipId);
        return subLevel == null || subLevel.isRemoved() ? null : new Vector3d(subLevel.logicalPose().position());
    }

    public static boolean exists(ServerLevel level, UUID shipId) {
        ServerSubLevel subLevel = get(level, shipId);
        return subLevel != null && !subLevel.isRemoved();
    }

    public static void remove(ServerLevel level, UUID shipId) {
        ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null) return;
        ServerSubLevel subLevel = get(level, shipId);
        if (subLevel != null && !subLevel.isRemoved()) {
            container.removeSubLevel(subLevel, SubLevelRemovalReason.REMOVED);
        }
    }

    private static ServerSubLevel get(ServerLevel level, UUID id) {
        ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null) return null;
        return container.getSubLevel(id) instanceof ServerSubLevel serverSubLevel ? serverSubLevel : null;
    }

    private static BlockPos findAssemblyPosition(ServerLevel level, ServerPlayer target) {
        for (int attempt = 0; attempt < 8; attempt++) {
            double angle = Math.PI * 2.0D * attempt / 8.0D;
            int x = (int) Math.floor(target.getX() + Math.cos(angle) * 110.0D);
            int z = (int) Math.floor(target.getZ() + Math.sin(angle) * 110.0D);
            int y = Math.min(level.getMaxBuildHeight() - 1 - RaidAirshipBlueprint.bounds().maxY(),
                    Math.max((int) target.getY() + 65, level.getSeaLevel() + 80));
            BlockPos anchor = new BlockPos(x, y, z);
            if (templateSpaceIsEmpty(level, anchor)) return anchor;
        }
        return null;
    }

    private static boolean templateSpaceIsEmpty(ServerLevel level, BlockPos anchor) {
        var bounds = RaidAirshipBlueprint.bounds();
        BlockPos min = anchor.offset(bounds.minX(), bounds.minY(), bounds.minZ());
        BlockPos max = anchor.offset(bounds.maxX(), bounds.maxY(), bounds.maxZ());
        if (min.getY() < level.getMinBuildHeight() || max.getY() >= level.getMaxBuildHeight()
                || !level.getWorldBorder().isWithinBounds(min) || !level.getWorldBorder().isWithinBounds(max)) {
            return false;
        }
        for (int x = bounds.minX(); x <= bounds.maxX(); x++) {
            for (int y = bounds.minY(); y <= bounds.maxY(); y++) {
                for (int z = bounds.minZ(); z <= bounds.maxZ(); z++) {
                    if (!level.getBlockState(anchor.offset(x, y, z)).isAir()) return false;
                }
            }
        }
        return true;
    }

    private static void buildAirship(ServerLevel level, BlockPos anchor, List<BlockPos> positions) {
        for (var block : RaidAirshipBlueprint.blocks()) {
            var pos = block.position();
            place(level, anchor.offset(pos.x(), pos.y(), pos.z()), stateFor(block), positions);
        }
        // Resolve fences/walls after all neighbors exist; assembly must not preserve disconnected posts.
        for (BlockPos pos : positions) {
            BlockState state = level.getBlockState(pos);
            level.setBlock(pos, net.minecraft.world.level.block.Block.updateFromNeighbourShapes(state, level, pos), 2);
        }
    }

    private static BlockState stateFor(RaidAirshipBlueprint.Block block) {
        var material = switch (block.part()) {
            case HULL, MAST -> Blocks.DARK_OAK_LOG;
            case DECK -> Blocks.SPRUCE_PLANKS;
            case TRIM -> Blocks.DARK_OAK_PLANKS;
            case RAIL -> Blocks.DARK_OAK_FENCE;
            case CHAIN -> Blocks.CHAIN;
            case COPPER -> Blocks.CUT_COPPER;
            case MACHINERY -> Blocks.POLISHED_BLACKSTONE_BRICKS;
            case GLASS -> Blocks.GRAY_STAINED_GLASS;
            case SMOKESTACK -> Blocks.POLISHED_BLACKSTONE_WALL;
            case BLACK_FLAG -> Blocks.BLACK_WOOL;
            case WHITE_FLAG -> Blocks.WHITE_WOOL;
            case BLACK_ENVELOPE -> aeroBlock("black_envelope", Blocks.BLACK_WOOL);
            case GRAY_ENVELOPE -> aeroBlock("gray_envelope", Blocks.GRAY_WOOL);
            case CORE -> aeroBlock("levitite", Blocks.SEA_LANTERN);
            case PROPELLER -> aeroBlock("smart_propeller", Blocks.IRON_BLOCK);
            case BURNER -> aeroBlock("hot_air_burner", Blocks.BLAST_FURNACE);
            case CANNON -> aeroBlock("mounted_potato_cannon", Blocks.DISPENSER);
            case BARREL -> Blocks.BARREL;
        };
        BlockState state = material.defaultBlockState();
        Direction direction = switch (block.facing()) {
            case NORTH -> Direction.NORTH;
            case SOUTH -> Direction.SOUTH;
            case WEST -> Direction.WEST;
            case UP -> Direction.UP;
        };
        if (state.hasProperty(BlockStateProperties.FACING)) {
            state = state.setValue(BlockStateProperties.FACING, direction);
        } else if (state.hasProperty(BlockStateProperties.HORIZONTAL_FACING) && direction != Direction.UP) {
            state = state.setValue(BlockStateProperties.HORIZONTAL_FACING, direction);
        }
        if (block.part() == RaidAirshipBlueprint.Part.MAST && state.hasProperty(BlockStateProperties.AXIS)) {
            state = state.setValue(BlockStateProperties.AXIS, direction.getAxis());
        }
        return state;
    }

    private static net.minecraft.world.level.block.Block aeroBlock(String path, net.minecraft.world.level.block.Block fallback) {
        return BuiltInRegistries.BLOCK.getOptional(ResourceLocation.fromNamespaceAndPath("aeronautics", path)).orElse(fallback);
    }

    private static void fillCargo(ServerLevel level, List<BlockPos> positions) {
        for (BlockPos pos : positions) {
            if (!(level.getBlockEntity(pos) instanceof BarrelBlockEntity barrel)) continue;
            barrel.setItem(0, new ItemStack(Items.GUNPOWDER, 24 + level.random.nextInt(25)));
            barrel.setItem(1, new ItemStack(Items.IRON_INGOT, 12 + level.random.nextInt(13)));
            barrel.setItem(2, new ItemStack(Items.GOLD_INGOT, 4 + level.random.nextInt(9)));
            barrel.setItem(3, new ItemStack(aeroBlock("andesite_propeller", Blocks.IRON_BLOCK).asItem(), 1));
            barrel.setItem(4, new ItemStack(aeroBlock("smart_propeller", Blocks.IRON_BLOCK).asItem(), 1));
            if (level.random.nextFloat() < 0.35F) barrel.setItem(5, new ItemStack(Items.GOLDEN_APPLE));
            barrel.setChanged();
        }
    }

    private static void place(ServerLevel level, BlockPos pos, BlockState state, List<BlockPos> positions) {
        if (!level.setBlock(pos, state, 2)) throw new IllegalStateException("Could not place raid airship at " + pos);
        positions.add(pos.immutable());
    }

    private static BoundingBox3i boundsOf(List<BlockPos> blocks) {
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (BlockPos pos : blocks) {
            minX = Math.min(minX, pos.getX());
            minY = Math.min(minY, pos.getY());
            minZ = Math.min(minZ, pos.getZ());
            maxX = Math.max(maxX, pos.getX());
            maxY = Math.max(maxY, pos.getY());
            maxZ = Math.max(maxZ, pos.getZ());
        }
        return new BoundingBox3i(minX, minY, minZ, maxX, maxY, maxZ);
    }
}
