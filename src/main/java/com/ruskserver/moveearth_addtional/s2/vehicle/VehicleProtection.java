package com.ruskserver.moveearth_addtional.s2.vehicle;

import com.ruskserver.moveearth_addtional.compat.vehicle.SableVehicleTopology;
import com.ruskserver.moveearth_addtional.s2.nation.NationSavedData;
import com.ruskserver.moveearth_addtional.s2.siege.LongAbsenceService;
import com.ruskserver.moveearth_addtional.s2.siege.OfflineDefenseService;
import com.ruskserver.moveearth_addtional.s2.siege.SiegeService;
import com.ruskserver.moveearth_addtional.s2.territory.TerritorySavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

import java.util.UUID;

/** World lookups behind {@link VehicleProtectionPolicy}. */
public final class VehicleProtection {
    private VehicleProtection() { }

    /** The live vehicle a block belongs to: its core block, or any block of its connected Sable bodies. */
    public static VehicleSavedData.VehicleRecord vehicleAt(ServerLevel level, BlockPos pos) {
        VehicleSavedData.VehicleRecord core = VehicleSavedData.get(level.getServer())
                .at(level.dimension().location(), pos).orElse(null);
        if (core != null) return core;
        try {
            return SableVehicleTopology.at(level, pos).map(SableVehicleTopology.VehicleContext::vehicle).orElse(null);
        } catch (RuntimeException | LinkageError ignored) {
            return null;
        }
    }

    /** The physical world position of a vehicle block (plot positions are mapped through the body's pose). */
    public static BlockPos worldPos(ServerLevel level, BlockPos pos) {
        return SableVehicleTopology.placement(level, pos).worldPos();
    }

    /** The territory core controlling the vehicle's physical position, if any. */
    public static TerritorySavedData.CoreRecord territoryAt(ServerLevel level, BlockPos pos) {
        return TerritorySavedData.get(level.getServer())
                .controllingCore(level.getServer(), level.dimension().location(), worldPos(level, pos)).orElse(null);
    }

    /** Whether the owner's rebuilding truce shields this vehicle where it physically stands now. */
    public static boolean settlementTruceApplies(ServerLevel level, VehicleSavedData.VehicleRecord vehicle, BlockPos pos) {
        boolean truce = com.ruskserver.moveearth_addtional.s2.siege.SiegeSavedData.get(level.getServer())
                .isNationSettlementProtected(vehicle.nationId());
        if (!truce) return false;
        TerritorySavedData.CoreRecord core = territoryAt(level, pos);
        return VehicleProtectionPolicy.settlementTruceApplies(true, vehicle.nationId(),
                core == null ? null : core.nationId());
    }

    /**
     * Offline-defense divisor for a hit on this vehicle at {@code pos}: the owner's territory divisor when the
     * vehicle physically stands in that nation's own territory, otherwise 1.
     */
    public static int offlineDivisor(ServerLevel level, VehicleSavedData.VehicleRecord vehicle, BlockPos pos) {
        TerritorySavedData.CoreRecord core = territoryAt(level, pos);
        boolean applies = VehicleProtectionPolicy.offlineDefenseApplies(vehicle.nationId(),
                core == null ? null : core.nationId());
        if (!applies) return 1;
        return VehicleProtectionPolicy.offlineDivisor(true, LongAbsenceService.tier(level, core).weakened(),
                OfflineDefenseService.divisor(level, core));
    }

    /**
     * The side an attack fights for, as reinforcement friendly fire sees it: the actor's own nation, or the
     * attributed nation when no player (or a nationless one) stands behind it.
     */
    public static UUID attackerSide(MinecraftServer server, SiegeService.AttackAttribution attack) {
        if (attack == null) return null;
        UUID actorNation = attack.actorId() == null ? null
                : NationSavedData.get(server).nationIdFor(attack.actorId()).orElse(null);
        return actorNation != null ? actorNation : attack.nationId();
    }

    public static boolean friendly(MinecraftServer server, SiegeService.AttackAttribution attack, UUID vehicleNation) {
        UUID side = attackerSide(server, attack);
        return VehicleProtectionPolicy.friendly(side, vehicleNation,
                side != null && NationSavedData.get(server).isAllied(side, vehicleNation));
    }
}
