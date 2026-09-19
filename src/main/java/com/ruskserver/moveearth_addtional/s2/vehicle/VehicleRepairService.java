package com.ruskserver.moveearth_addtional.s2.vehicle;

import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import com.ruskserver.moveearth_addtional.CompatEventHandler;
import com.ruskserver.moveearth_addtional.block.entity.VehicleCoreBlockEntity;
import com.ruskserver.moveearth_addtional.compat.vehicle.SableVehicleTopology;
import com.ruskserver.moveearth_addtional.item.ModItems;
import com.ruskserver.moveearth_addtional.s2.S2Permission;
import com.ruskserver.moveearth_addtional.s2.nation.NationSavedData;
import com.ruskserver.moveearth_addtional.s2.siege.PrisonerService;
import com.ruskserver.moveearth_addtional.s2.territory.NationUpkeepService;
import com.ruskserver.moveearth_addtional.s2.reinforcement.ReinforcementService;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/** Explicit sneak-welder action leaves ordinary core viewing and block reinforcement available. */
@EventBusSubscriber(modid = Moveearth_addtional.MODID)
public final class VehicleRepairService {
    private VehicleRepairService() { }

    public static void recordHit(ServerLevel level, BlockPos pos) {
        var data = VehicleSavedData.get(level.getServer());
        var core = data.at(level.dimension().location(), pos).orElse(null);
        if (core == null) core = SableVehicleTopology.at(level, pos).map(value -> value.vehicle()).orElse(null);
        if (core != null) data.recordHit(core.id(), level.getServer().overworld().getGameTime());
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void repair(PlayerInteractEvent.RightClickBlock event) {
        if (event.getHand() != InteractionHand.MAIN_HAND || !event.getEntity().isShiftKeyDown()
                || !event.getItemStack().is(ModItems.WELDING_TOOL.get())
                || !(event.getLevel().getBlockEntity(event.getPos()) instanceof VehicleCoreBlockEntity)) return;
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        ServerLevel level = player.serverLevel();
        var data = VehicleSavedData.get(player.server);
        var vehicle = data.at(level.dimension().location(), event.getPos()).orElse(null);
        var nations = NationSavedData.get(player.server);
        if (vehicle == null || !player.isAlive() || player.isSpectator() || CompatEventHandler.isPlayerDown(player)
                || PrisonerService.isMovementRestricted(player)
                || !vehicle.nationId().equals(nations.nationIdFor(player.getUUID()).orElse(null))
                || !nations.can(player.getUUID(), S2Permission.MANAGE_REINFORCEMENT)
                || !NationUpkeepService.penalty(player.server, vehicle.nationId()).reinforcementProtectionEnabled()
                || SableVehicleTopology.distanceSquared(level, player, event.getPos()) > 36D) {
            message(player, "denied"); return;
        }
        if (vehicle.health() >= vehicle.maximumHealth()) { message(player, "full"); return; }
        if (!player.getOffhandItem().is(Items.IRON_INGOT) || player.getOffhandItem().isEmpty()) {
            message(player, "material"); return;
        }
        var result = data.repair(vehicle.id(), player.server.overworld().getGameTime());
        if (result == null) return;
        if (result.gain() <= 0) {
            player.displayClientMessage(Component.translatable("message.moveearth_addtional.vehicle_repair.wait",
                    (result.waitTicks() + 19) / 20), true);
            return;
        }
        if (vehicle.health() == 0) VehicleLootSavedData.get(player.server).revoke(vehicle.id());
        // All validation precedes the single server-thread mutation; rate limits belong to the vehicle, not player.
        player.getOffhandItem().shrink(1);
        player.getMainHandItem().hurtAndBreak(1, player, EquipmentSlot.MAINHAND);
        data.vehicle(vehicle.id()).ifPresent(updated -> {
            if (level.getBlockEntity(event.getPos()) instanceof VehicleCoreBlockEntity block) block.bind(updated);
            player.displayClientMessage(Component.translatable("message.moveearth_addtional.vehicle_repair."
                    + (result.emergency() ? "emergency" : "normal"), result.gain(), updated.health(), updated.maximumHealth()), true);
        });
        level.playSound(null, event.getPos(), SoundEvents.ANVIL_USE, SoundSource.BLOCKS, 2F, 1.2F);
        ReinforcementService.syncNearbyManagers(level, event.getPos());
    }

    private static void message(ServerPlayer player, String key) {
        player.displayClientMessage(Component.translatable("message.moveearth_addtional.vehicle_repair." + key), true);
    }
}
