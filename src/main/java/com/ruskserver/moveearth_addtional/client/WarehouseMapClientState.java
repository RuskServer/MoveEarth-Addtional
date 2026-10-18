package com.ruskserver.moveearth_addtional.client;

import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import com.ruskserver.moveearth_addtional.network.s2c.other.S2C_WarehouseZonesPacket;
import com.ruskserver.moveearth_addtional.warehouse.WarehouseZoneView;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Warehouses for the map layer: every one this client has been near on the current server, saved per
 * server under the game directory, plus the state of those the server reports right now. The server
 * sends only Warehouses within a short range, so the map shows what the player has found and no more.
 */
@EventBusSubscriber(modid = Moveearth_addtional.MODID, value = Dist.CLIENT)
public final class WarehouseMapClientState {
    /** Well inside the server's 256-block reporting range, so a reported site is never forgotten at its edge. */
    private static final double FORGET_RADIUS = 200.0D;
    private static final int RECONCILE_INTERVAL_TICKS = 20;

    private static WarehouseMapMemory memory = new WarehouseMapMemory();
    private static String serverKey;
    private static Map<WarehouseMapMemory.Site, WarehouseZoneView.State> live = Map.of();
    private static List<WarehouseMapMemory.Site> reported = List.of();
    /** The dimension {@link #reported} describes; null until the server's first list this session. */
    private static String reportedDimension;
    private static int ticks;

    private WarehouseMapClientState() { }

    public static void update(S2C_WarehouseZonesPacket packet) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) return;
        loadFor(currentServerKey(minecraft));
        Map<WarehouseMapMemory.Site, WarehouseZoneView.State> states = new HashMap<>();
        List<WarehouseMapMemory.Site> sites = new ArrayList<>();
        for (S2C_WarehouseZonesPacket.Zone zone : packet.zones()) {
            WarehouseMapMemory.Site site = new WarehouseMapMemory.Site(zone.dimension().toString(),
                    zone.min().getX(), zone.min().getY(), zone.min().getZ());
            sites.add(site);
            states.put(site, zone.state());
        }
        live = Map.copyOf(states);
        reported = List.copyOf(sites);
        reportedDimension = minecraft.player.level().dimension().location().toString();
        reconcile(minecraft);
    }

    /** Remembered Warehouses in {@code dimension}. */
    public static List<WarehouseMapMemory.Site> sites(ResourceLocation dimension) {
        return memory.in(dimension.toString());
    }

    /** The state the server reports for a site now, or null when it is out of range. */
    public static WarehouseZoneView.State liveState(WarehouseMapMemory.Site site) {
        return live.get(site);
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        // The server sends a new list only when it changes, so a site that has gone is noticed here as the
        // player walks up to where it was.
        if (++ticks % RECONCILE_INTERVAL_TICKS != 0) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null && reportedDimension != null) reconcile(minecraft);
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        memory = new WarehouseMapMemory();
        serverKey = null;
        live = Map.of();
        reported = List.of();
        reportedDimension = null;
    }

    private static void reconcile(Minecraft minecraft) {
        String dimension = minecraft.player.level().dimension().location().toString();
        // Just after a dimension change the list still describes the old one.
        if (!dimension.equals(reportedDimension)) return;
        if (memory.reconcile(dimension, reported, minecraft.player.getX(), minecraft.player.getZ(), FORGET_RADIUS)) {
            save();
        }
    }

    private static void loadFor(String key) {
        if (key.equals(serverKey)) return;
        serverKey = key;
        memory = new WarehouseMapMemory();
        Path file = file(key);
        if (!Files.isRegularFile(file)) return;
        try {
            memory = WarehouseMapMemory.parse(Files.readString(file, StandardCharsets.UTF_8));
        } catch (IOException | RuntimeException failure) {
            Moveearth_addtional.LOGGER.warn("Could not read remembered Warehouses from {}", file, failure);
        }
    }

    private static void save() {
        if (serverKey == null) return;
        Path file = file(serverKey);
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, memory.serialize(), StandardCharsets.UTF_8);
        } catch (IOException | RuntimeException failure) {
            Moveearth_addtional.LOGGER.warn("Could not save remembered Warehouses to {}", file, failure);
        }
    }

    private static Path file(String key) {
        return Minecraft.getInstance().gameDirectory.toPath().resolve(Moveearth_addtional.MODID)
                .resolve("warehouse_map").resolve(key + ".txt");
    }

    /** The server's address, or the world's folder name in singleplayer, safe as a file name. */
    private static String currentServerKey(Minecraft minecraft) {
        ServerData server = minecraft.getCurrentServer();
        String key;
        if (server != null) key = "server-" + server.ip;
        else if (minecraft.getSingleplayerServer() != null)
            key = "local-" + minecraft.getSingleplayerServer().getWorldData().getLevelName();
        else key = "unknown";
        return key.replaceAll("[^A-Za-z0-9._-]", "_");
    }
}
