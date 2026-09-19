package com.ruskserver.moveearth_addtional.s2.siege;

import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import com.ruskserver.moveearth_addtional.block.ModBlocks;
import com.ruskserver.moveearth_addtional.block.entity.VehicleCoreBlockEntity;
import com.ruskserver.moveearth_addtional.compat.vehicle.SableVehicleTopology;
import com.ruskserver.moveearth_addtional.item.ModItems;
import com.ruskserver.moveearth_addtional.s2.nation.NationSavedData;
import com.ruskserver.moveearth_addtional.s2.recovery.WarHistorySavedData;
import com.ruskserver.moveearth_addtional.s2.time.OpenTimeService;
import com.ruskserver.moveearth_addtional.s2.vehicle.VehicleSavedData;
import com.ruskserver.moveearth_addtional.ui.MoveEarthMessage;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.UUID;

/** Explicit restraints + sneak operation for carrying one escorted captive in an owned vehicle. */
@EventBusSubscriber(modid = Moveearth_addtional.MODID, bus = EventBusSubscriber.Bus.GAME)
public final class PrisonerVehicleTransportService {
    private PrisonerVehicleTransportService() { }

    public static boolean isLoaded(MinecraftServer server, UUID captiveId) {
        return PrisonerVehicleTransportSavedData.get(server).get(captiveId) != null;
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onCoreUse(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer captor) || !captor.isShiftKeyDown()
                || !captor.getMainHandItem().is(ModItems.RESTRAINTS.get())
                || !event.getLevel().getBlockState(event.getPos()).is(ModBlocks.VEHICLE_CORE.get())
                || !(event.getLevel().getBlockEntity(event.getPos()) instanceof VehicleCoreBlockEntity core)
                || core.vehicleId() == null) return;
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.CONSUME);
        PrisonerSavedData prisoners = PrisonerSavedData.get(captor.server);
        PrisonerSavedData.Custody custody = prisoners.custodyByCaptor(captor.getUUID()).orElse(null);
        if (custody == null) {
            captor.sendSystemMessage(MoveEarthMessage.error(Component.translatable(
                    "message.moveearth_addtional.prisoner.transport.no_escort")));
            return;
        }
        PrisonerVehicleTransportSavedData transports = PrisonerVehicleTransportSavedData.get(captor.server);
        PrisonerVehicleTransportSavedData.Transport existing = transports.get(custody.playerId());
        if (existing != null) {
            if (!existing.vehicleId().equals(core.vehicleId())) return;
            transports.unload(custody.playerId());
            captor.sendSystemMessage(MoveEarthMessage.success(Component.translatable(
                    "message.moveearth_addtional.prisoner.transport.unloaded")));
            return;
        }
        VehicleSavedData.VehicleRecord vehicle = VehicleSavedData.get(captor.server)
                .vehicle(core.vehicleId()).orElse(null);
        UUID nation = NationSavedData.get(captor.server).nationIdFor(captor.getUUID()).orElse(null);
        Entity captive = PrisonerService.findCustodyEntity(captor.server, custody.playerId());
        double distance = captive == null ? Double.MAX_VALUE
                : SableVehicleTopology.distanceSquared(captor.serverLevel(), captive, event.getPos());
        boolean slotFree = vehicle != null && transports.all().stream()
                .noneMatch(value -> value.vehicleId().equals(vehicle.id()));
        if (!PrisonerVehicleTransportPolicy.canLoad(true, vehicle != null && vehicle.nationId().equals(nation),
                vehicle != null && vehicle.health() > 0, captive != null,
                captive != null && captive.level() == captor.level(), distance, slotFree)
                || !transports.load(custody.playerId(), captor.getUUID(), vehicle.id())) {
            captor.sendSystemMessage(MoveEarthMessage.error(Component.translatable(
                    "message.moveearth_addtional.prisoner.transport.invalid")));
            return;
        }
        WarHistorySavedData.get(captor.server).append(OpenTimeService.now(captor.server),
                WarHistorySavedData.Type.PRISONER_TRANSPORTED, WarHistorySavedData.Visibility.NATION,
                custody.holdingNation(), custody.homeNation(), vehicle.id(),
                java.util.List.of(custody.playerId().toString(), captor.getUUID().toString()));
        captor.sendSystemMessage(MoveEarthMessage.success(Component.translatable(
                "message.moveearth_addtional.prisoner.transport.loaded")));
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if ((server.getTickCount() & 3) != 0) return;
        PrisonerVehicleTransportSavedData transports = PrisonerVehicleTransportSavedData.get(server);
        PrisonerSavedData prisoners = PrisonerSavedData.get(server);
        for (PrisonerVehicleTransportSavedData.Transport transport : transports.all()) {
            PrisonerSavedData.Custody custody = prisoners.custody(transport.captiveId()).orElse(null);
            VehicleSavedData.VehicleRecord vehicle = VehicleSavedData.get(server)
                    .vehicle(transport.vehicleId()).orElse(null);
            if (custody == null || !custody.captor().equals(transport.captorId())) {
                transports.unload(transport.captiveId());
                continue;
            }
            if (vehicle == null || vehicle.health() <= 0) {
                rescue(server, transport);
                continue;
            }
            ServerLevel level = server.getLevel(ResourceKey.create(Registries.DIMENSION, vehicle.dimension()));
            if (level == null || !level.hasChunkAt(vehicle.corePos())) continue;
            Entity captive = PrisonerService.findCustodyEntity(server, transport.captiveId());
            ServerPlayer captor = server.getPlayerList().getPlayer(transport.captorId());
            if (captive == null || captor == null || captor.level() != level) continue;
            var placement = SableVehicleTopology.placement(level, vehicle.corePos());
            if (captor.distanceToSqr(placement.worldPos().getCenter()) > 144.0D) {
                transports.unload(transport.captiveId());
                continue;
            }
            var destination = placement.worldPos().above().getCenter();
            captive.teleportTo(destination.x, destination.y, destination.z);
            prisoners.moveCustody(custody.playerId(), level.dimension().location(), placement.worldPos().above());
        }
    }

    public static void onVehicleDestroyed(MinecraftServer server, UUID vehicleId) {
        for (var transport : PrisonerVehicleTransportSavedData.get(server).all())
            if (transport.vehicleId().equals(vehicleId)) rescue(server, transport);
    }

    private static void rescue(MinecraftServer server, PrisonerVehicleTransportSavedData.Transport transport) {
        PrisonerVehicleTransportSavedData.get(server).unload(transport.captiveId());
        PrisonerSavedData.Custody custody = PrisonerSavedData.get(server)
                .releaseCustody(transport.captiveId()).orElse(null);
        if (custody == null) return;
        com.ruskserver.moveearth_addtional.s2.combat.CombatTagService.releaseBody(server,
                transport.captiveId(), true);
        com.ruskserver.moveearth_addtional.s2.combat.CombatTagSavedData.get(server).consume(transport.captiveId());
        WarHistorySavedData.get(server).append(OpenTimeService.now(server),
                WarHistorySavedData.Type.PRISONER_RESCUED, WarHistorySavedData.Visibility.PUBLIC,
                custody.homeNation(), custody.holdingNation(), transport.vehicleId(),
                java.util.List.of(transport.captiveId().toString()));
        ServerPlayer captive = server.getPlayerList().getPlayer(transport.captiveId());
        if (captive != null) captive.sendSystemMessage(MoveEarthMessage.success(Component.translatable(
                "message.moveearth_addtional.prisoner.transport.rescued")));
    }
}
