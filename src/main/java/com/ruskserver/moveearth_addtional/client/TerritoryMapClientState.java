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
    private static Map<ResourceLocation, Map<UUID, S2C_TerritoryMapPacket.NationEntry>> nationsByDimension = Map.of();
    private static final Map<ResourceLocation, Long> lastRequests = new HashMap<>();
    private static boolean enabled = true;
    private static long version;

    private TerritoryMapClientState() { }

    public static void update(S2C_TerritoryMapPacket packet) {
        Map<ResourceLocation, List<S2C_TerritoryMapPacket.CoreEntry>> updatedCores = new HashMap<>(coresByDimension);
        updatedCores.put(packet.dimension(), packet.cores());
        coresByDimension = Map.copyOf(updatedCores);
        Map<UUID, S2C_TerritoryMapPacket.NationEntry> indexed = new HashMap<>();
        packet.nations().forEach(nation -> indexed.put(nation.nationId(), nation));
        Map<ResourceLocation, Map<UUID, S2C_TerritoryMapPacket.NationEntry>> updatedNations =
                new HashMap<>(nationsByDimension);
        updatedNations.put(packet.dimension(), Map.copyOf(indexed));
        nationsByDimension = Map.copyOf(updatedNations);
        version++;
    }

    public static void requestIfStale(ResourceLocation dimension) {
        if (!enabled || net.minecraft.client.Minecraft.getInstance().getConnection() == null) return;
        long now = Util.getMillis();
        long previous = lastRequests.getOrDefault(dimension, Long.MIN_VALUE);
        if (previous != Long.MIN_VALUE && now - previous < REFRESH_MILLIS) return;
        lastRequests.put(dimension, now);
        PacketDistributor.sendToServer(new C2S_RequestTerritoryMapPacket(dimension));
    }

    public static List<S2C_TerritoryMapPacket.CoreEntry> cores(ResourceLocation dimension) {
        return coresByDimension.getOrDefault(dimension, List.of());
    }

    public static long version() { return version; }

    public static S2C_TerritoryMapPacket.NationEntry nation(ResourceLocation dimension, UUID id) {
        return nationsByDimension.getOrDefault(dimension, Map.of()).get(id);
    }

    public static boolean enabled() { return enabled; }

    public static boolean toggle() {
        enabled = !enabled;
        if (enabled) lastRequests.clear();
        return enabled;
    }

    public static void clear() {
        coresByDimension = Map.of();
        nationsByDimension = Map.of();
        lastRequests.clear();
        version++;
    }
}
