package com.ruskserver.moveearth_addtional.s2.vehicle;

import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import com.ruskserver.moveearth_addtional.block.ModBlocks;
import com.ruskserver.moveearth_addtional.block.entity.VehicleCoreBlockEntity;
import com.ruskserver.moveearth_addtional.s2.S2Permission;
import com.ruskserver.moveearth_addtional.s2.nation.NationSavedData;
import com.ruskserver.moveearth_addtional.s2.siege.SiegeService;
import com.ruskserver.moveearth_addtional.ui.MoveEarthMessage;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.BlockEvent;

/** Prevents pickaxes from bypassing the vehicle-core HP and weapon damage rules. */
@EventBusSubscriber(modid = Moveearth_addtional.MODID, bus = EventBusSubscriber.Bus.GAME)
public final class VehicleCoreEvents {
    private VehicleCoreEvents() { }

    @SubscribeEvent
    public static void onBreak(BlockEvent.BreakEvent event) {
        if (!event.getState().is(ModBlocks.VEHICLE_CORE.get())
                || !(event.getPlayer() instanceof ServerPlayer player)
                || !(event.getLevel() instanceof ServerLevel level)
                || !(level.getBlockEntity(event.getPos()) instanceof VehicleCoreBlockEntity blockEntity)
                || blockEntity.vehicleId() == null) return;
        if (com.ruskserver.moveearth_addtional.s2.combat.RealPlayers.real(player) == null) {
            // Deployers and turrets act as fake players; only a person may break a vehicle core.
            event.setCanceled(true);
            return;
        }
        VehicleSavedData.VehicleRecord vehicle = VehicleSavedData.get(player.server)
                .vehicle(blockEntity.vehicleId()).orElse(null);
        if (vehicle == null) return;
        NationSavedData nations = NationSavedData.get(player.server);
        java.util.UUID playerNation = nations.nationIdFor(player.getUUID()).orElse(null);
        if (vehicle.nationId().equals(playerNation)) {
            // Breaking drops the core and deletes its record; re-placing it would restart at full HP.
            long now = player.server.overworld().getGameTime();
            long combatUntil = VehicleSavedData.get(player.server).repairState(vehicle.id()).combatUntil();
            var decision = VehicleCoreDismantlePolicy.owner(
                    nations.can(player.getUUID(), S2Permission.MANAGE_REINFORCEMENT),
                    vehicle.health(), vehicle.maximumHealth(), now, combatUntil);
            if (decision == VehicleCoreDismantlePolicy.Decision.ALLOWED) return;
            event.setCanceled(true);
            player.sendSystemMessage(MoveEarthMessage.error(switch (decision) {
                case IN_COMBAT -> Component.translatable(
                        "message.moveearth_addtional.vehicle_core.dismantle_in_combat",
                        VehicleCoreDismantlePolicy.secondsUntil(now, combatUntil));
                case DAMAGED -> Component.translatable(
                        "message.moveearth_addtional.vehicle_core.dismantle_damaged",
                        vehicle.health(), vehicle.maximumHealth());
                default -> Component.translatable(
                        "message.moveearth_addtional.vehicle_core.dismantle_no_permission");
            }));
            return;
        }
        var decision = VehicleCoreDismantlePolicy.outsider(vehicle.health(),
                VehicleLootSavedData.get(player.server).salvageHeldByOthers(vehicle.id(), player, playerNation));
        if (decision == VehicleCoreDismantlePolicy.Decision.ALLOWED) return;
        event.setCanceled(true);
        if (decision == VehicleCoreDismantlePolicy.Decision.LOOT_PROTECTED) {
            player.sendSystemMessage(MoveEarthMessage.error(Component.translatable(
                    "message.moveearth_addtional.vehicle_core.break_salvage_protected")));
            return;
        }
        if (!SiegeService.peaceTruceBlocks(player, level, event.getPos())) {
            SiegeService.recordAttack(player, level, event.getPos(), false);
        }
        player.sendSystemMessage(MoveEarthMessage.error(Component.translatable(
                "message.moveearth_addtional.vehicle_core.break_denied")));
    }
}
