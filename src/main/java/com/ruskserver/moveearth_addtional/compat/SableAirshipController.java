package com.ruskserver.moveearth_addtional.compat;

import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
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
            int y = Math.min(level.getMaxBuildHeight() - 14, Math.max((int) target.getY() + 65, level.getSeaLevel() + 80));
            BlockPos anchor = new BlockPos(x, y, z);
            if (templateSpaceIsEmpty(level, anchor)) return anchor;
        }
        return null;
    }

    private static boolean templateSpaceIsEmpty(ServerLevel level, BlockPos anchor) {
        for (int x = -15; x <= 15; x++) {
            for (int y = -2; y <= 17; y++) {
                for (int z = -7; z <= 7; z++) {
                    if (!level.getBlockState(anchor.offset(x, y, z)).isAir()) return false;
                }
            }
        }
        return true;
    }

    private static void buildAirship(ServerLevel level, BlockPos anchor, List<BlockPos> positions) {
        BlockState grayEnvelope = aeroBlock("gray_envelope", Blocks.GRAY_WOOL).defaultBlockState();
        BlockState blackEnvelope = aeroBlock("black_envelope", Blocks.BLACK_WOOL).defaultBlockState();
        BlockState levitite = aeroBlock("levitite", Blocks.SEA_LANTERN).defaultBlockState();
        BlockState smartPropeller = aeroBlock("smart_propeller", Blocks.IRON_BLOCK).defaultBlockState();
        BlockState gyroBearing = aeroBlock("gyroscopic_propeller_bearing", Blocks.IRON_BLOCK).defaultBlockState();
        BlockState burner = aeroBlock("hot_air_burner", Blocks.BLAST_FURNACE).defaultBlockState();
        BlockState cannon = aeroBlock("mounted_potato_cannon", Blocks.DISPENSER).defaultBlockState();

        for (int x = -10; x <= 10; x++) {
            int halfWidth = Math.max(2, 5 - Math.abs(x) / 3);
            for (int z = -halfWidth; z <= halfWidth; z++) {
                place(level, anchor.offset(x, 0, z), Blocks.DARK_OAK_PLANKS.defaultBlockState(), positions);
            }
        }
        for (int x = -9; x <= 9; x++) {
            place(level, anchor.offset(x, 1, -4), Blocks.DARK_OAK_FENCE.defaultBlockState(), positions);
            place(level, anchor.offset(x, 1, 4), Blocks.DARK_OAK_FENCE.defaultBlockState(), positions);
        }
        for (int x : new int[]{-8, 0, 8}) {
            for (int z : new int[]{-4, 4}) {
                for (int y = 2; y <= 6; y++) {
                    place(level, anchor.offset(x, y, z), Blocks.CHAIN.defaultBlockState(), positions);
                }
            }
        }
        for (int y = 6; y <= 15; y++) {
            double vertical = (y - 10.5D) / 5.0D;
            for (int x = -14; x <= 14; x++) {
                for (int z = -6; z <= 6; z++) {
                    double shape = x * x / 196.0D + z * z / 36.0D + vertical * vertical;
                    if (shape <= 1.0D && shape >= 0.67D) {
                        BlockState state = ((x + y + z) & 4) == 0 ? blackEnvelope : grayEnvelope;
                        place(level, anchor.offset(x, y, z), state, positions);
                    }
                }
            }
        }
        for (int x : new int[]{-8, 0, 8}) {
            for (int z : new int[]{-2, 2}) place(level, anchor.offset(x, 10, z), levitite, positions);
        }
        place(level, anchor.offset(-11, 1, -5), smartPropeller, positions);
        place(level, anchor.offset(-11, 1, 5), smartPropeller, positions);
        place(level, anchor.offset(-10, 1, 0), gyroBearing, positions);
        place(level, anchor.offset(0, 5, 0), burner, positions);
        place(level, anchor.offset(8, 1, -4), cannon, positions);
        place(level, anchor.offset(8, 1, 4), cannon, positions);
        for (int x : new int[]{-3, 3}) {
            place(level, anchor.offset(x, 1, 0), Blocks.BARREL.defaultBlockState(), positions);
        }
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
        level.setBlock(pos, state, 2);
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
