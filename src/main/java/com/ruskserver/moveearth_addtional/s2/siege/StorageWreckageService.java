package com.ruskserver.moveearth_addtional.s2.siege;

import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import com.ruskserver.moveearth_addtional.block.ModBlocks;
import com.ruskserver.moveearth_addtional.compat.vehicle.SableVehicleTopology;
import com.ruskserver.moveearth_addtional.s2.dispatch.AttributionSnapshotService;
import com.ruskserver.moveearth_addtional.s2.nation.NationSavedData;
import com.ruskserver.moveearth_addtional.s2.recovery.WarHistorySavedData;
import com.ruskserver.moveearth_addtional.s2.time.OpenTimeService;
import com.ruskserver.moveearth_addtional.s2.vehicle.VehicleLootSavedData;
import com.ruskserver.moveearth_addtional.ui.MoveEarthMessage;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Converts destroyed inventories into policy-gated, non-automatable recovery targets. */
@EventBusSubscriber(modid = Moveearth_addtional.MODID, bus = EventBusSubscriber.Bus.GAME)
public final class StorageWreckageService {
    private StorageWreckageService() { }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onExplosion(ExplosionEvent.Detonate event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        var direct = event.getExplosion().getDirectSourceEntity();
        SiegeService.AttackAttribution attack = AttributionSnapshotService.attribution(direct, "explosion");
        ServerPlayer player = SiegeService.attributablePlayer(direct);
        if (attack == null && player != null) attack = new SiegeService.AttackAttribution(
                NationSavedData.get(level.getServer()).nationIdFor(player.getUUID()).orElse(null),
                player.getUUID(), "explosion");
        SiegeService.AttackAttribution finalAttack = attack;
        event.getAffectedBlocks().removeIf(pos -> {
            if (level.getBlockState(pos).is(ModBlocks.STORAGE_WRECKAGE.get())) return true;
            if (level.getBlockState(pos).is(ModBlocks.MARKET_STATION.get()))
                return com.ruskserver.moveearth_addtional.economy.MarketService.wreckStation(level, pos, finalAttack);
            if (!level.getBlockState(pos).is(com.ruskserver.moveearth_addtional.s2.nation.NationStorageEvents.STORAGE_BLOCKS)) {
                return false;
            }
            return wreckStorage(level, pos, finalAttack);
        });
    }

    /** Used by optional blast bridges whose affected-block list never reaches NeoForge ExplosionEvent. */
    public static boolean wreckStorage(ServerLevel level, BlockPos pos, SiegeService.AttackAttribution attack) {
        if (level.getBlockState(pos).is(ModBlocks.STORAGE_WRECKAGE.get())) return true;
        if (level.getBlockState(pos).is(ModBlocks.MARKET_STATION.get()))
            return com.ruskserver.moveearth_addtional.economy.MarketService.wreckStation(level, pos, attack);
        if (!level.getBlockState(pos).is(com.ruskserver.moveearth_addtional.s2.nation.NationStorageEvents.STORAGE_BLOCKS)) {
            return false;
        }
        if (!(level.getBlockEntity(pos) instanceof Container container)) {
            // Unknown modded inventories are preserved rather than silently deleting contents.
            Moveearth_addtional.LOGGER.warn("Protected unsupported storage from explosion at {} in {}",
                    pos, level.dimension().location());
            return true;
        }
            List<ItemStack> contents = new ArrayList<>();
            for (int slot = 0; slot < container.getContainerSize(); slot++) {
                ItemStack stack = container.getItem(slot);
                if (!stack.isEmpty()) contents.add(stack.copy());
            }
            var ownership = com.ruskserver.moveearth_addtional.s2.nation.NationStorageOwnershipSavedData
                    .get(level.getServer());
            UUID explicitOwner = ownership.owner(level.dimension().location(), pos);
            if (contents.isEmpty()) {
                ownership.remove(level.dimension().location(), pos);
                return false;
            }
            for (int slot = 0; slot < container.getContainerSize(); slot++) container.setItem(slot, ItemStack.EMPTY);
            container.setChanged();
            var access = SiegeLootService.accessForPosition(level.getServer(), level.dimension().location(), pos);
            UUID vehicleId = null;
            UUID owner = explicitOwner != null ? explicitOwner : access.ownerNation();
            try {
                var vehicle = SableVehicleTopology.at(level, pos).orElse(null);
                if (vehicle != null) {
                    vehicleId = vehicle.vehicle().id();
                    owner = vehicle.vehicle().nationId();
                }
            } catch (RuntimeException | LinkageError ignored) { }
            UUID destroyer = attack == null ? null
                    : attack.actorId() != null ? attack.actorId() : attack.nationId();
            StorageWreckageSavedData.Wreckage wreckage = new StorageWreckageSavedData.Wreckage(
                    level.dimension().location(), pos, owner, access.siegeId(), vehicleId, destroyer,
                    attack == null ? "unknown" : attack.source(),
                    OpenTimeService.now(level.getServer()), contents);
            StorageWreckageSavedData.get(level.getServer()).put(wreckage);
            ownership.remove(level.dimension().location(), pos);
            level.setBlock(pos, ModBlocks.STORAGE_WRECKAGE.get().defaultBlockState(), 3);
            WarHistorySavedData.get(level.getServer()).append(OpenTimeService.now(level.getServer()),
                    WarHistorySavedData.Type.STORAGE_WRECKED, WarHistorySavedData.Visibility.NATION,
                    owner, attack == null ? null : attack.nationId(), access.siegeId(),
                    List.of(level.dimension().location().toString(), pos.toShortString(), wreckage.source()));
        return true;
    }

    public static void recover(ServerPlayer player, BlockPos pos) {
        if (com.ruskserver.moveearth_addtional.economy.MarketService.recoverWreckage(player, pos)) return;
        StorageWreckageSavedData data = StorageWreckageSavedData.get(player.server);
        StorageWreckageSavedData.Wreckage wreckage = data.get(player.level().dimension().location(), pos);
        if (wreckage == null) return;
        if (!canAccess(player, pos, wreckage)) {
            player.sendSystemMessage(MoveEarthMessage.error(Component.translatable(
                    "message.moveearth_addtional.wreckage.denied")));
            return;
        }
        List<ItemStack> remaining = new ArrayList<>();
        int recovered = 0;
        for (ItemStack stored : wreckage.items()) {
            ItemStack stack = stored.copy();
            int before = stack.getCount();
            player.getInventory().add(stack);
            recovered += before - stack.getCount();
            if (!stack.isEmpty()) remaining.add(stack);
        }
        data.updateItems(wreckage.dimension(), wreckage.pos(), remaining);
        if (remaining.isEmpty()) {
            data.remove(wreckage.dimension(), wreckage.pos());
            player.level().removeBlock(pos, false);
            WarHistorySavedData.get(player.server).append(OpenTimeService.now(player.server),
                    WarHistorySavedData.Type.STORAGE_RECOVERED, WarHistorySavedData.Visibility.NATION,
                    wreckage.ownerNation(), NationSavedData.get(player.server)
                    .nationIdFor(player.getUUID()).orElse(null), wreckage.siegeId(),
                    List.of(player.getUUID().toString(), wreckage.dimension().toString(), pos.toShortString()));
        }
        player.sendSystemMessage(MoveEarthMessage.success(Component.translatable(
                "message.moveearth_addtional.wreckage.recovered", recovered, remaining.size())));
    }

    private static boolean canAccess(ServerPlayer player, BlockPos pos,
                                     StorageWreckageSavedData.Wreckage wreckage) {
        UUID nation = NationSavedData.get(player.server).nationIdFor(player.getUUID()).orElse(null);
        return StorageWreckagePolicy.mayRecover(
                player.hasPermissions(2),
                wreckage.ownerNation() != null,
                nation != null && nation.equals(wreckage.ownerNation()),
                wreckage.vehicleId() != null && VehicleLootSavedData.get(player.server)
                        .canLoot(player, player.serverLevel(), pos),
                SiegeLootService.access(player, pos).allowed());
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onBreak(BlockEvent.BreakEvent event) {
        if (event.getLevel() instanceof ServerLevel marketLevel
                && event.getState().is(ModBlocks.MARKET_STATION.get())) {
            var entity = marketLevel.getBlockEntity(event.getPos());
            if (entity instanceof com.ruskserver.moveearth_addtional.block.entity.MarketStationBlockEntity station
                    && station.stationId() != null) {
                var ledger = com.ruskserver.moveearth_addtional.economy.EconomyLedgerSavedData
                        .get(marketLevel.getServer());
                boolean pending = ledger.outstanding(station.stationId()) > 0
                        || ledger.marketOrders().stream().anyMatch(order -> order.stationId().equals(station.stationId()));
                if (pending && event.getPlayer() instanceof ServerPlayer player
                        && !SiegeLootService.access(player, event.getPos()).allowed()) {
                    event.setCanceled(true);
                    player.sendSystemMessage(MoveEarthMessage.warning(Component.literal(
                            "市場在庫または注文が残っています。取消・受取を済ませてください")));
                    return;
                }
                if (pending && com.ruskserver.moveearth_addtional.economy.MarketService
                        .wreckStation(marketLevel, event.getPos())) {
                    event.setCanceled(true);
                    return;
                }
            }
        }
        if (!(event.getLevel() instanceof ServerLevel level)
                || !event.getState().is(ModBlocks.STORAGE_WRECKAGE.get())) return;
        StorageWreckageSavedData.Wreckage wreckage = StorageWreckageSavedData.get(level.getServer())
                .get(level.dimension().location(), event.getPos());
        if (wreckage != null && !wreckage.items().isEmpty()
                || com.ruskserver.moveearth_addtional.economy.EconomyLedgerSavedData.get(level.getServer())
                .marketWreckage(level.dimension().location(), event.getPos()) != null) event.setCanceled(true);
    }

    public static void removed(Level level, BlockPos pos) {
        if (level instanceof ServerLevel serverLevel) StorageWreckageSavedData.get(serverLevel.getServer())
                .remove(level.dimension().location(), pos);
    }
}
