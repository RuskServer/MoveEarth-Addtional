package com.ruskserver.moveearth_addtional.s2.vehicle;

import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import com.ruskserver.moveearth_addtional.s2.nation.NationSavedData;
import com.ruskserver.moveearth_addtional.s2.notification.NationNotificationSavedData;
import com.ruskserver.moveearth_addtional.s2.notification.NationNotificationService;
import com.ruskserver.moveearth_addtional.s2.siege.SiegeService;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Tells a nation its vehicle is under fire: territory raises Siege and intrusion notices, but a vehicle in plot
 * space raised nothing, so a parked craft could be shot apart while its owners were away. One notice per vehicle
 * and attacker every few minutes, in game and through the nation's Discord settings.
 */
@EventBusSubscriber(modid = Moveearth_addtional.MODID, bus = EventBusSubscriber.Bus.GAME)
public final class VehicleHitNotifier {
    private static final UUID UNKNOWN_ATTACKER = new UUID(0L, 0L);
    private static final int MAX_ENTRIES = 4_096;
    private static final Map<Key, Long> LAST_NOTICE = new HashMap<>();

    private record Key(UUID vehicle, UUID attacker) { }

    private VehicleHitNotifier() { }

    /** A hit that really wore down a vehicle block at {@code pos} (armour or core). */
    public static void hit(ServerLevel level, BlockPos pos, SiegeService.AttackAttribution attack) {
        VehicleSavedData.VehicleRecord vehicle = VehicleProtection.vehicleAt(level, pos);
        if (vehicle != null) hit(level, vehicle, attack);
    }

    public static void hit(ServerLevel level, VehicleSavedData.VehicleRecord vehicle,
                           SiegeService.AttackAttribution attack) {
        MinecraftServer server = level.getServer();
        NationSavedData nations = NationSavedData.get(server);
        UUID side = VehicleProtection.attackerSide(server, attack);
        boolean individual = side == null && attack != null && attack.actorId() != null;
        UUID attacker = side != null ? side : individual ? attack.actorId() : UNKNOWN_ATTACKER;
        if (vehicle.nationId().equals(attacker)) return;
        long now = System.currentTimeMillis();
        Key key = new Key(vehicle.id(), attacker);
        Long last = LAST_NOTICE.get(key);
        if (!VehicleProtectionPolicy.hitNoticeDue(last == null ? 0L : last, now)) return;
        if (LAST_NOTICE.size() >= MAX_ENTRIES) {
            LAST_NOTICE.values().removeIf(at -> VehicleProtectionPolicy.hitNoticeDue(at, now));
            if (LAST_NOTICE.size() >= MAX_ENTRIES) LAST_NOTICE.clear();
        }
        LAST_NOTICE.put(key, now);
        String attackerName = attacker.equals(UNKNOWN_ATTACKER) ? "?"
                : SiegeService.attackerName(server, nations, attacker, individual);
        // The owner may always know where its own vehicle is; the core's physical position stands for the craft.
        BlockPos where = VehicleProtection.worldPos(level, vehicle.corePos());
        NationNotificationService.publish(server, List.of(vehicle.nationId()),
                NationNotificationSavedData.EventType.VEHICLE_ATTACKED, vehicle.dimension(), where,
                Component.translatable("message.moveearth_addtional.vehicle.attacked", attackerName,
                        vehicle.health(), vehicle.maximumHealth(), where.getX(), where.getY(), where.getZ()),
                List.of(attackerName, Integer.toString(vehicle.health()), Integer.toString(vehicle.maximumHealth())));
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        LAST_NOTICE.clear();
    }
}
