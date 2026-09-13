package com.ruskserver.moveearth_addtional.client;

import com.ruskserver.moveearth_addtional.network.C2S_RequestTerritoryMapPacket;
import com.ruskserver.moveearth_addtional.network.S2C_TerritoryMapPacket;
import net.minecraft.Util;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Client cache for the public territory layer. Requests only while a map is actually rendered. */
public final class TerritoryMapClientState {
    private static final long REFRESH_MILLIS = 5_000L;
    private static Map<ResourceLocation, List<S2C_TerritoryMapPacket.CoreEntry>> coresByDimension = Map.of();
    private static Map<UUID, S2C_TerritoryMapPacket.NationEntry> nations = Map.of();
    private static long lastRequestAt = Long.MIN_VALUE;
    private static boolean enabled = true;
    private static long version;

    private TerritoryMapClientState() { }

    public static void update(S2C_TerritoryMapPacket packet) {
        Map<ResourceLocation, List<S2C_TerritoryMapPacket.CoreEntry>> grouped = new HashMap<>();
        for (S2C_TerritoryMapPacket.CoreEntry core : packet.cores()) {
            grouped.computeIfAbsent(core.dimension(), ignored -> new java.util.ArrayList<>()).add(core);
        }
        Map<ResourceLocation, List<S2C_TerritoryMapPacket.CoreEntry>> immutableGroups = new HashMap<>();
        grouped.forEach((dimension, entries) -> immutableGroups.put(dimension, List.copyOf(entries)));
        coresByDimension = Map.copyOf(immutableGroups);
        Map<UUID, S2C_TerritoryMapPacket.NationEntry> indexed = new HashMap<>();
        packet.nations().forEach(nation -> indexed.put(nation.nationId(), nation));
        nations = Map.copyOf(indexed);
        version++;
    }

    public static void requestIfStale() {
        if (!enabled || net.minecraft.client.Minecraft.getInstance().getConnection() == null) return;
        long now = Util.getMillis();
        if (lastRequestAt != Long.MIN_VALUE && now - lastRequestAt < REFRESH_MILLIS) return;
        lastRequestAt = now;
        PacketDistributor.sendToServer(new C2S_RequestTerritoryMapPacket());
    }

    public static List<S2C_TerritoryMapPacket.CoreEntry> cores(ResourceLocation dimension) {
        return coresByDimension.getOrDefault(dimension, List.of());
    }

    public static long version() { return version; }

    public static S2C_TerritoryMapPacket.NationEntry nation(UUID id) { return nations.get(id); }

    public static boolean enabled() { return enabled; }

    public static boolean toggle() {
        enabled = !enabled;
        if (enabled) lastRequestAt = Long.MIN_VALUE;
        return enabled;
    }

    public static void clear() {
        coresByDimension = Map.of();
        nations = Map.of();
        lastRequestAt = Long.MIN_VALUE;
        version++;
    }
}
