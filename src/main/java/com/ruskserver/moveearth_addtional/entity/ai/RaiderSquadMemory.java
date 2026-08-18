package com.ruskserver.moveearth_addtional.entity.ai;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class RaiderSquadMemory {
    private static final Map<Integer, SquadState> SQUADS = new HashMap<>();

    private RaiderSquadMemory() {
    }

    public static void reportTarget(int raidId, Vec3 position, long gameTime) {
        if (raidId <= 0) return;
        SquadState state = SQUADS.computeIfAbsent(raidId, ignored -> new SquadState());
        state.lastSeen = position;
        state.lastSeenAt = gameTime;
    }

    public static Vec3 getRecentTarget(int raidId, long gameTime) {
        SquadState state = SQUADS.get(raidId);
        if (state == null || state.lastSeen == null || gameTime - state.lastSeenAt > 200L) return null;
        return state.lastSeen;
    }

    public static boolean reserveCover(int raidId, BlockPos pos, UUID raiderId, long gameTime) {
        SquadState state = SQUADS.computeIfAbsent(raidId, ignored -> new SquadState());
        CoverReservation current = state.coverReservations.get(pos);
        if (current != null && !current.owner.equals(raiderId) && current.expiresAt >= gameTime) return false;
        state.coverReservations.put(pos.immutable(), new CoverReservation(raiderId, gameTime + 100L));
        return true;
    }

    public static void releaseCover(int raidId, BlockPos pos, UUID raiderId) {
        SquadState state = SQUADS.get(raidId);
        if (state == null || pos == null) return;
        CoverReservation current = state.coverReservations.get(pos);
        if (current != null && current.owner.equals(raiderId)) state.coverReservations.remove(pos);
    }

    public static void removeRaid(int raidId) {
        SQUADS.remove(raidId);
    }

    private static final class SquadState {
        private Vec3 lastSeen;
        private long lastSeenAt;
        private final Map<BlockPos, CoverReservation> coverReservations = new HashMap<>();
    }

    private record CoverReservation(UUID owner, long expiresAt) {
    }
}
