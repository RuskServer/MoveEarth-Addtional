package com.ruskserver.moveearth_addtional.pvp;

import net.minecraft.server.level.ServerPlayer;

import java.util.*;

public final class PvpReplayTracker {
    public static final PvpReplayTracker INSTANCE = new PvpReplayTracker();
    public static final int MAX_FRAMES = 60; // 3秒間 (20 ticks/sec * 3)

    private final Map<UUID, FrameRingBuffer> history = new HashMap<>();

    private PvpReplayTracker() {}

    private static final class FrameRingBuffer {
        private final PvpReplayFrame[] frames = new PvpReplayFrame[MAX_FRAMES];
        private int head = 0;
        private int count = 0;

        FrameRingBuffer() {
            for (int i = 0; i < MAX_FRAMES; i++) {
                frames[i] = new PvpReplayFrame();
            }
        }

        void record(ServerPlayer player) {
            frames[head].set(
                    player.getX(),
                    player.getY(),
                    player.getZ(),
                    player.getYRot(),
                    player.getXRot(),
                    player.isCrouching(),
                    false,
                    false
            );
            head = (head + 1) % MAX_FRAMES;
            if (count < MAX_FRAMES) count++;
        }

        List<PvpReplayFrame> getHistory() {
            List<PvpReplayFrame> list = new ArrayList<>(count);
            int start = (count < MAX_FRAMES) ? 0 : head;
            for (int i = 0; i < count; i++) {
                int index = (start + i) % MAX_FRAMES;
                list.add(frames[index].copy());
            }
            return list;
        }
    }

    public void record(ServerPlayer player) {
        if (player == null) return;
        UUID id = player.getUUID();
        FrameRingBuffer buffer = history.computeIfAbsent(id, ignored -> new FrameRingBuffer());
        buffer.record(player);
    }

    public List<PvpReplayFrame> getHistory(UUID playerId) {
        FrameRingBuffer buffer = history.get(playerId);
        if (buffer == null) return List.of();
        return buffer.getHistory();
    }

    public void remove(UUID playerId) {
        history.remove(playerId);
    }

    public void clear() {
        history.clear();
    }
}
