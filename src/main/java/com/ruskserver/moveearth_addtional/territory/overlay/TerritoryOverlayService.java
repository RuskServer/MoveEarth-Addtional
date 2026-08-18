package com.ruskserver.moveearth_addtional.territory.overlay;

import com.ruskserver.moveearth_addtional.network.S2C_TerritoryInfluenceOverlayPacket;
import com.ruskserver.moveearth_addtional.territory.domain.InfluenceResult;
import com.ruskserver.moveearth_addtional.territory.domain.TerritoryOwnerId;
import com.ruskserver.moveearth_addtional.territory.service.TerritoryInfluenceService;
import com.ruskserver.moveearth_addtional.territory.service.TerritoryMembershipService;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.Map;
import java.util.HashMap;
import java.util.Optional;
import java.util.WeakHashMap;

public final class TerritoryOverlayService {
    public static final int RADIUS = 128;
    public static final int STEP = 16;
    public static final int WIDTH = RADIUS * 2 / STEP + 1;
    private static final long MIN_REQUEST_INTERVAL_TICKS = 20L;
    private static final Map<ServerPlayer, Long> LAST_REQUEST = new WeakHashMap<>();

    private TerritoryOverlayService() {
    }

    public static void sendTo(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        long gameTime = level.getGameTime();
        synchronized (LAST_REQUEST) {
            long lastRequest = LAST_REQUEST.getOrDefault(player, Long.MIN_VALUE / 2L);
            if (gameTime - lastRequest < MIN_REQUEST_INTERVAL_TICKS) {
                return;
            }
            LAST_REQUEST.put(player, gameTime);
        }

        int centerX = Math.floorDiv(player.getBlockX(), STEP) * STEP;
        int centerZ = Math.floorDiv(player.getBlockZ(), STEP) * STEP;
        int originX = centerX - RADIUS - STEP / 2;
        int originZ = centerZ - RADIUS - STEP / 2;
        int[] cells = new int[WIDTH * WIDTH];
        short[] heights = new short[cells.length];
        Map<TerritoryOwnerId, Boolean> membershipCache = new HashMap<>();

        for (int zIndex = 0; zIndex < WIDTH; zIndex++) {
            for (int xIndex = 0; xIndex < WIDTH; xIndex++) {
                int index = zIndex * WIDTH + xIndex;
                int sampleX = originX + xIndex * STEP + STEP / 2;
                int sampleZ = originZ + zIndex * STEP + STEP / 2;
                var chunk = level.getChunkSource().getChunkNow(sampleX >> 4, sampleZ >> 4);
                if (chunk == null) {
                    heights[index] = Short.MIN_VALUE;
                    cells[index] = TerritoryOverlayCell.pack(TerritoryOverlayCell.RELATION_NONE, 0, 0.0D);
                    continue;
                }

                int surfaceY = chunk.getHeight(
                        Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                        sampleX & 15,
                        sampleZ & 15
                );
                heights[index] = (short) surfaceY;
                InfluenceResult influence = TerritoryInfluenceService.evaluate(
                        level,
                        new BlockPos(sampleX, surfaceY, sampleZ)
                );
                cells[index] = packFor(player, level, influence, membershipCache);
            }
        }

        PacketDistributor.sendToPlayer(player, new S2C_TerritoryInfluenceOverlayPacket(
                originX, originZ, STEP, WIDTH, cells, heights
        ));
    }

    private static int packFor(ServerPlayer player, ServerLevel level, InfluenceResult influence,
                               Map<TerritoryOwnerId, Boolean> membershipCache) {
        if (influence.leadingOwner().isEmpty() || influence.leadingInfluence() <= 0.0D) {
            return TerritoryOverlayCell.pack(TerritoryOverlayCell.RELATION_NONE, 0, 0.0D);
        }

        int relation;
        if (influence.contested()) {
            relation = TerritoryOverlayCell.RELATION_CONTESTED;
        } else {
            Optional<TerritoryOwnerId> controller = influence.controllingOwner();
            relation = controller.isPresent()
                    && membershipCache.computeIfAbsent(controller.orElseThrow(),
                    ownerId -> TerritoryMembershipService.isMember(level, player, ownerId))
                    ? TerritoryOverlayCell.RELATION_FRIENDLY
                    : TerritoryOverlayCell.RELATION_HOSTILE;
        }
        return TerritoryOverlayCell.pack(
                relation,
                influence.protectedActions().size(),
                influence.leadingInfluence()
        );
    }
}
