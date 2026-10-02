package com.ruskserver.moveearth_addtional.s2.vehicle;

import com.ruskserver.moveearth_addtional.block.entity.VehicleCoreBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import com.ruskserver.moveearth_addtional.s2.siege.SiegeService;
import com.ruskserver.moveearth_addtional.s2.recovery.WarHistorySavedData;
import com.ruskserver.moveearth_addtional.s2.time.OpenTimeService;

/** Applies and presents server-authoritative damage to mobile vehicle cores. */
public final class VehicleCoreHealthService {
    private VehicleCoreHealthService() { }

    public static VehicleSavedData.VehicleRecord damage(ServerLevel level, BlockPos pos, int amount) {
        return damage(level, pos, amount, null);
    }

    public static VehicleSavedData.VehicleRecord damage(ServerLevel level, BlockPos pos, int amount,
                                                         SiegeService.AttackAttribution attack) {
        VehicleSavedData data = VehicleSavedData.get(level.getServer());
        VehicleSavedData.VehicleRecord before = data.at(level.dimension().location(), pos).orElse(null);
        if (before == null || amount <= 0) return before;
        // Own and allied fire never wears a vehicle core down, like reinforcement friendly fire.
        if (attack != null && VehicleProtection.friendly(level.getServer(), attack, before.nationId())) return before;
        int applied = com.ruskserver.moveearth_addtional.s2.siege.OfflineDefenseService
                .scale(level, pos, amount).appliedDamage();
        if (applied <= 0) return before;
        data.recordHit(before.id(), level.getServer().overworld().getGameTime());
        VehicleSavedData.VehicleRecord after = data.damage(before.id(), applied);
        if (after == null || after.health() == before.health()) return after;
        VehicleHitNotifier.hit(level, after, attack);
        if (level.getBlockEntity(pos) instanceof VehicleCoreBlockEntity blockEntity) blockEntity.bind(after);
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, pos.getX() + 0.5D, pos.getY() + 0.7D,
                pos.getZ() + 0.5D, Math.min(28, 6 + amount / 4), 0.3D, 0.25D, 0.3D, 0.08D);
        level.playSound(null, pos, SoundEvents.ANVIL_LAND, SoundSource.BLOCKS, 3.5F,
                after.health() == 0 ? 0.48F : 0.82F);
        if (VehicleDestructionPolicy.transitionedToDestroyed(before.health(), after.health()))
            recordDestroyed(level, after, attack);
        return after;
    }

    private static void recordDestroyed(ServerLevel level, VehicleSavedData.VehicleRecord vehicle,
                                        SiegeService.AttackAttribution attack) {
        java.util.UUID attackerNation = attack == null ? null : attack.nationId();
        java.util.UUID actor = attack == null ? null : attack.actorId();
        java.util.UUID attackerId = attackerNation != null ? attackerNation : actor;
        boolean individual = attackerNation == null;
        // Salvage rights go to an enemy only: never to the owner's own or an allied nation.
        boolean friendly = attack != null && (VehicleProtection.friendly(level.getServer(), attack, vehicle.nationId())
                || attackerNation != null && com.ruskserver.moveearth_addtional.s2.nation.NationSavedData
                .get(level.getServer()).isAllied(attackerNation, vehicle.nationId()));
        if (attackerId != null && !friendly) VehicleLootSavedData.get(level.getServer()).open(vehicle.id(),
                vehicle.nationId(), attackerId, individual, OpenTimeService.now(level.getServer()));
        WarHistorySavedData.get(level.getServer()).append(OpenTimeService.now(level.getServer()),
                WarHistorySavedData.Type.VEHICLE_DESTROYED, WarHistorySavedData.Visibility.PUBLIC,
                vehicle.nationId(), attackerNation, vehicle.id(), java.util.List.of(
                        actor == null ? "" : actor.toString(), attack == null ? "unknown" : attack.source(),
                        vehicle.dimension().toString(), vehicle.corePos().toShortString()));
        com.ruskserver.moveearth_addtional.s2.siege.PrisonerVehicleTransportService
                .onVehicleDestroyed(level.getServer(), vehicle.id());
    }
}
