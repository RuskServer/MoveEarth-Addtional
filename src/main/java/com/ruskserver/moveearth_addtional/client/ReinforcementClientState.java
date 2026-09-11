package com.ruskserver.moveearth_addtional.client;

import com.ruskserver.moveearth_addtional.network.S2C_ReinforcementSnapshotPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

import java.util.LinkedHashMap;
import java.util.Map;

public final class ReinforcementClientState {
    private static final Map<BlockPos, S2C_ReinforcementSnapshotPacket.Entry> ENTRIES = new LinkedHashMap<>();
    private static ResourceLocation dimension;
    private static boolean allowed;
    private static boolean overlayActive;

    private ReinforcementClientState() {
    }

    public static void update(S2C_ReinforcementSnapshotPacket packet) {
        dimension = packet.dimension();
        allowed = packet.allowed();
        ENTRIES.clear();
        for (var entry : packet.entries()) ENTRIES.put(entry.pos().immutable(), entry);
        if (!allowed) overlayActive = false;
    }

    public static Map<BlockPos, S2C_ReinforcementSnapshotPacket.Entry> entries() {
        return Map.copyOf(ENTRIES);
    }

    public static S2C_ReinforcementSnapshotPacket.Entry at(BlockPos pos) {
        return ENTRIES.get(pos);
    }

    public static ResourceLocation dimension() {
        return dimension;
    }

    public static boolean allowed() {
        return allowed;
    }

    public static boolean overlayActive() {
        return overlayActive;
    }

    public static void toggleOverlay() {
        overlayActive = !overlayActive;
    }

    public static void clear() {
        ENTRIES.clear();
        dimension = null;
        allowed = false;
        overlayActive = false;
    }
}
