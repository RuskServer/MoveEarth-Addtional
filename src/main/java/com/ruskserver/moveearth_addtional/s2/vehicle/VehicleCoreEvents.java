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
        VehicleSavedData.VehicleRecord vehicle = VehicleSavedData.get(player.server)
                .vehicle(blockEntity.vehicleId()).orElse(null);
        if (vehicle == null) return;
        NationSavedData nations = NationSavedData.get(player.server);
        java.util.UUID playerNation = nations.nationIdFor(player.getUUID()).orElse(null);
        boolean manager = vehicle.nationId().equals(playerNation)
                && nations.can(player.getUUID(), S2Permission.MANAGE_REINFORCEMENT);
        if (manager || vehicle.health() <= 0) return;
        event.setCanceled(true);
        if (!SiegeService.peaceTruceBlocks(player, level, event.getPos())) {
            SiegeService.recordAttack(player, level, event.getPos(), false);
        }
        player.sendSystemMessage(MoveEarthMessage.error(Component.translatable(
                "message.moveearth_addtional.vehicle_core.break_denied")));
    }
}
