package com.ruskserver.moveearth_addtional.compat.vehicle;

import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import com.ruskserver.moveearth_addtional.config.S2TerritoryConfig;
import com.ruskserver.moveearth_addtional.ui.MoveEarthMessage;
import dev.ryanhcode.sable.api.SubLevelHelper;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.BoundingBox3dc;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.joml.Quaterniond;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Last-resort server-side recovery for Sable bodies that tunnel through the world floor. */
@EventBusSubscriber(modid = Moveearth_addtional.MODID, bus = EventBusSubscriber.Bus.GAME)
public final class SableVoidFailsafe {
    private static final int TERRAIN_SAMPLE_SPACING = 16;
    private static final int MAX_AXIS_SAMPLES = 65;

    private SableVoidFailsafe() { }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (!S2TerritoryConfig.sableVoidFailsafeEnabled()) return;
        for (ServerLevel level : event.getServer().getAllLevels()) rescueInvalidBodies(level);
    }

    private static void rescueInvalidBodies(ServerLevel level) {
        ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null) return;
        Set<UUID> handled = new HashSet<>();
        for (ServerSubLevel body : List.copyOf(container.getAllSubLevels())) {
            if (body.isRemoved() || handled.contains(body.getUniqueId())) continue;
            BoundingBox3dc bounds = body.boundingBox();
            if (!SableVoidFailsafePolicy.shouldRescue(bounds.minY(), level.getMinBuildHeight(),
                    S2TerritoryConfig.sableVoidTriggerDepth())) continue;
            rescueConnectedChain(level, container, body, handled);
        }
    }

    private static void rescueConnectedChain(ServerLevel level, ServerSubLevelContainer container,
                                             ServerSubLevel trigger, Set<UUID> handled) {
        List<ServerSubLevel> bodies = new ArrayList<>();
        double minimumY = Double.POSITIVE_INFINITY;
        double minimumX = Double.POSITIVE_INFINITY;
        double minimumZ = Double.POSITIVE_INFINITY;
        double maximumX = Double.NEGATIVE_INFINITY;
        double maximumZ = Double.NEGATIVE_INFINITY;
        for (SubLevel connected : SubLevelHelper.getConnectedChain(trigger)) {
            if (!(connected instanceof ServerSubLevel body) || body.isRemoved()) continue;
            bodies.add(body);
            handled.add(body.getUniqueId());
            BoundingBox3dc bounds = body.boundingBox();
            minimumY = Math.min(minimumY, bounds.minY());
            minimumX = Math.min(minimumX, bounds.minX());
            minimumZ = Math.min(minimumZ, bounds.minZ());
            maximumX = Math.max(maximumX, bounds.maxX());
            maximumZ = Math.max(maximumZ, bounds.maxZ());
        }
        if (bodies.isEmpty() || !Double.isFinite(minimumY)) return;

        Vector3d triggerPosition = new Vector3d(trigger.logicalPose().position());
        if (!validHorizontalBounds(minimumX, minimumZ, maximumX, maximumZ)) {
            minimumX = maximumX = triggerPosition.x;
            minimumZ = maximumZ = triggerPosition.z;
        }
        int terrainTop = highestLoadedTerrain(level, minimumX, minimumZ, maximumX, maximumZ);
        double lift = SableVoidFailsafePolicy.verticalDisplacement(
                minimumY, terrainTop, S2TerritoryConfig.sableRescueClearance());
        if (!(lift > 0.0D) || !Double.isFinite(lift)) return;

        Set<UUID> watchers = new HashSet<>();
        for (ServerSubLevel body : bodies) {
            Vector3d destination = new Vector3d(body.logicalPose().position()).add(0.0D, lift, 0.0D);
            Quaterniond orientation = new Quaterniond(body.logicalPose().orientation());
            container.physicsSystem().getPipeline().teleport(body, destination, orientation);
            container.physicsSystem().getPipeline().resetVelocity(body);
            watchers.addAll(body.getTrackingPlayers());
        }
        for (UUID playerId : watchers) {
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(playerId);
            if (player != null) player.sendSystemMessage(MoveEarthMessage.warning(Component.translatable(
                    "message.moveearth_addtional.sable_void_rescued")));
        }
        Moveearth_addtional.LOGGER.warn(
                "Recovered {} connected Sable body/bodies from below {} in {} by lifting {} blocks",
                bodies.size(), level.getMinBuildHeight(), level.dimension().location(), Math.ceil(lift));
    }

    private static boolean validHorizontalBounds(double minX, double minZ, double maxX, double maxZ) {
        return Double.isFinite(minX) && Double.isFinite(minZ) && Double.isFinite(maxX) && Double.isFinite(maxZ)
                && minX <= maxX && minZ <= maxZ;
    }

    private static int highestLoadedTerrain(ServerLevel level, double minimumX, double minimumZ,
                                            double maximumX, double maximumZ) {
        int minX = floorToInt(minimumX);
        int minZ = floorToInt(minimumZ);
        int maxX = ceilToInt(maximumX);
        int maxZ = ceilToInt(maximumZ);
        int highest = level.getSeaLevel();
        boolean sampled = false;
        for (int x : sampleAxis(minX, maxX)) {
            for (int z : sampleAxis(minZ, maxZ)) {
                BlockPos probe = new BlockPos(x, level.getMinBuildHeight(), z);
                if (!level.hasChunkAt(probe)) continue;
                highest = Math.max(highest,
                        level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z));
                sampled = true;
            }
        }
        return sampled ? highest : Math.max(level.getSeaLevel(), level.getSharedSpawnPos().getY());
    }

    private static List<Integer> sampleAxis(int minimum, int maximum) {
        long span = Math.max(0L, (long) maximum - minimum);
        int spacing = (int) Math.max(TERRAIN_SAMPLE_SPACING,
                (span + MAX_AXIS_SAMPLES - 2L) / (MAX_AXIS_SAMPLES - 1L));
        List<Integer> samples = new ArrayList<>();
        for (long value = minimum; value < maximum; value += spacing) samples.add((int) value);
        if (samples.isEmpty() || samples.getLast() != maximum) samples.add(maximum);
        return samples;
    }

    private static int floorToInt(double value) {
        return (int) Math.max(Integer.MIN_VALUE, Math.min(Integer.MAX_VALUE, Math.floor(value)));
    }

    private static int ceilToInt(double value) {
        return (int) Math.max(Integer.MIN_VALUE, Math.min(Integer.MAX_VALUE, Math.ceil(value)));
    }
}
