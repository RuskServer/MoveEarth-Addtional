package com.ruskserver.moveearth_addtional.economy;

import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import com.ruskserver.moveearth_addtional.advancement.ModCriteria;
import com.ruskserver.moveearth_addtional.block.ModBlocks;
import com.ruskserver.moveearth_addtional.block.entity.MarketStationBlockEntity;
import com.ruskserver.moveearth_addtional.s2.nation.NationSavedData;
import com.ruskserver.moveearth_addtional.s2.siege.SiegeLootService;
import com.ruskserver.moveearth_addtional.s2.siege.SiegeService;
import com.ruskserver.moveearth_addtional.s2.territory.TerritorySavedData;
import com.ruskserver.moveearth_addtional.ui.MoveEarthMessage;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.UUID;

/** Validates physical hand-offs independently of remotely browsable orders. */
@EventBusSubscriber(modid = Moveearth_addtional.MODID, bus = EventBusSubscriber.Bus.GAME)
public final class MarketService {
    private MarketService() { }

    public static MarketStationSavedData.Station activeStation(MinecraftServer server, UUID stationId,
                                                                 boolean requireLoaded) {
        if (stationId == null) return null;
        MarketStationSavedData.Station station = MarketStationSavedData.get(server).byId(stationId).orElse(null);
        if (station == null || NationSavedData.get(server).nation(station.nationId()).isEmpty()
                || !TerritorySavedData.get(server).controlsChunk(server, station.nationId(),
                        station.dimension(), station.pos())) return null;
        if (!requireLoaded) return station;
        ServerLevel level = server.getLevel(ResourceKey.create(Registries.DIMENSION, station.dimension()));
        if (level == null || !level.hasChunkAt(station.pos())
                || !level.getBlockState(station.pos()).is(ModBlocks.MARKET_STATION.get())
                || !(level.getBlockEntity(station.pos()) instanceof MarketStationBlockEntity entity)
                || !station.id().equals(entity.stationId())) return null;
        return station;
    }

    private static boolean local(ServerPlayer player, UUID stationId) {
        MarketStationSavedData.Station station = activeStation(player.server, stationId, true);
        return station != null && player.level().dimension().location().equals(station.dimension())
                && MarketOrderRules.inReach(player.distanceToSqr(station.pos().getX() + 0.5,
                        station.pos().getY() + 0.5, station.pos().getZ() + 0.5));
    }

    public static EconomyLedgerSavedData.MarketResult listSell(ServerPlayer player, UUID stationId,
                                                                int quantity, long price) {
        MarketStationSavedData.Station station = activeStation(player.server, stationId, true);
        UUID nation = NationSavedData.get(player.server).nationIdFor(player.getUUID()).orElse(null);
        ItemStack held = player.getMainHandItem();
        if (station == null || !station.nationId().equals(nation) || !local(player, stationId)
                || held.isEmpty() || quantity < 1 || held.getCount() < quantity)
            return new EconomyLedgerSavedData.MarketResult(EconomyLedgerSavedData.MarketStatus.INVALID, null);
        EconomyLedgerSavedData.MarketResult result = EconomyLedgerSavedData.get(player.server)
                .createSellOrder(player.getUUID(), stationId, held, quantity, price,
                        System.currentTimeMillis() + MarketOrderRules.MAX_LIFETIME_MILLIS,
                        System.currentTimeMillis());
        if (result.status() == EconomyLedgerSavedData.MarketStatus.APPLIED) held.shrink(quantity);
        return result;
    }

    public static EconomyLedgerSavedData.MarketResult listBuy(ServerPlayer player, UUID stationId,
                                                               int quantity, long price) {
        MarketStationSavedData.Station station = activeStation(player.server, stationId, true);
        UUID nation = NationSavedData.get(player.server).nationIdFor(player.getUUID()).orElse(null);
        ItemStack held = player.getMainHandItem();
        if (station == null || !station.nationId().equals(nation) || held.isEmpty())
            return new EconomyLedgerSavedData.MarketResult(EconomyLedgerSavedData.MarketStatus.INVALID, null);
        return EconomyLedgerSavedData.get(player.server).createBuyOrder(player.getUUID(), stationId,
                held, quantity, price, System.currentTimeMillis() + MarketOrderRules.MAX_LIFETIME_MILLIS,
                System.currentTimeMillis());
    }

    public static EconomyLedgerSavedData.MarketStatus purchase(ServerPlayer player, UUID orderId, int quantity) {
        EconomyLedgerSavedData ledger = EconomyLedgerSavedData.get(player.server);
        MarketOrder order = ledger.marketOrders().stream().filter(o -> o.id().equals(orderId)).findFirst().orElse(null);
        // A remote purchase is settled in the ledger; only physical hand-off needs the chunk loaded.
        if (order == null || activeStation(player.server, order.stationId(), false) == null)
            return EconomyLedgerSavedData.MarketStatus.INVALID;
        EconomyLedgerSavedData.MarketStatus result = ledger.purchase(player.getUUID(), orderId,
                quantity, System.currentTimeMillis());
        if (result == EconomyLedgerSavedData.MarketStatus.APPLIED) {
            ModCriteria.trigger(player, ModCriteria.MARKET_TRADE_COMPLETED);
            ServerPlayer seller = player.server.getPlayerList().getPlayer(order.owner());
            if (seller != null) ModCriteria.trigger(seller, ModCriteria.MARKET_TRADE_COMPLETED);
        }
        return result;
    }

    public static EconomyLedgerSavedData.MarketStatus deliver(ServerPlayer player, UUID orderId, int quantity) {
        EconomyLedgerSavedData ledger = EconomyLedgerSavedData.get(player.server);
        MarketOrder order = ledger.marketOrders().stream().filter(o -> o.id().equals(orderId)).findFirst().orElse(null);
        ItemStack held = player.getMainHandItem();
        if (order == null || !local(player, order.stationId()) || held.isEmpty() || held.getCount() < quantity)
            return EconomyLedgerSavedData.MarketStatus.INVALID;
        EconomyLedgerSavedData.MarketStatus result = ledger.deliver(player.getUUID(), orderId, held,
                quantity, System.currentTimeMillis());
        if (result == EconomyLedgerSavedData.MarketStatus.APPLIED) {
            boolean farmGoods = held.is(net.minecraft.tags.TagKey.create(
                    net.minecraft.core.registries.Registries.ITEM,
                    net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(
                            Moveearth_addtional.MODID, "market_crops")));
            held.shrink(quantity);
            ModCriteria.trigger(player, ModCriteria.MARKET_TRADE_COMPLETED);
            if (farmGoods) ModCriteria.trigger(player, ModCriteria.FARM_GOODS_DELIVERED);
            ServerPlayer buyer = player.server.getPlayerList().getPlayer(order.owner());
            if (buyer != null) ModCriteria.trigger(buyer, ModCriteria.MARKET_TRADE_COMPLETED);
        }
        return result;
    }

    public static int claim(ServerPlayer player, UUID stationId, UUID claimId) {
        if (!local(player, stationId)) return 0;
        EconomyLedgerSavedData ledger = EconomyLedgerSavedData.get(player.server);
        EconomyLedgerSavedData.MarketClaim claim = ledger.claimsAt(player.getUUID(), stationId).stream()
                .filter(c -> c.id().equals(claimId)).findFirst().orElse(null);
        if (claim == null) return 0;
        int requested = Math.min(claim.quantity(), claim.item().getMaxStackSize());
        ItemStack stack = claim.item().copyWithCount(requested);
        player.getInventory().add(stack);
        int received = requested - stack.getCount();
        if (received > 0) ledger.reduceClaim(player.getUUID(), claimId, received);
        return received;
    }

    /** Called before ordinary break/explosion handling so virtual inventory has a physical recovery point. */
    public static boolean wreckStation(ServerLevel level, BlockPos pos) {
        if (!level.getBlockState(pos).is(ModBlocks.MARKET_STATION.get())
                || !(level.getBlockEntity(pos) instanceof MarketStationBlockEntity entity)
                || entity.stationId() == null) return false;
        EconomyLedgerSavedData ledger = EconomyLedgerSavedData.get(level.getServer());
        if (ledger.outstanding(entity.stationId()) == 0
                && ledger.marketOrders().stream().noneMatch(o -> o.stationId().equals(entity.stationId())))
            return false;
        var result = ledger.wreckMarketStation(entity.stationId(), entity.nationId(),
                level.dimension().location(), pos);
        if (result != EconomyLedgerSavedData.MarketStatus.APPLIED) return true;
        if (ledger.marketWreckage(level.dimension().location(), pos) == null) return false;
        level.setBlock(pos, ModBlocks.STORAGE_WRECKAGE.get().defaultBlockState(), 3);
        return true;
    }

    /** An unattributed or friendly blast cannot manufacture a protected storage wreck. */
    public static boolean wreckStation(ServerLevel level, BlockPos pos, SiegeService.AttackAttribution attack) {
        if (!(level.getBlockEntity(pos) instanceof MarketStationBlockEntity entity)
                || entity.stationId() == null) return false;
        EconomyLedgerSavedData ledger = EconomyLedgerSavedData.get(level.getServer());
        boolean pending = ledger.outstanding(entity.stationId()) > 0
                || ledger.marketOrders().stream().anyMatch(o -> o.stationId().equals(entity.stationId()));
        if (!pending) return false;
        UUID actorNation = attack == null || attack.actorId() == null ? null
                : NationSavedData.get(level.getServer()).nationIdFor(attack.actorId()).orElse(null);
        if (attack == null || entity.nationId() != null
                && (entity.nationId().equals(attack.nationId()) || entity.nationId().equals(actorNation)))
            return true;
        return wreckStation(level, pos);
    }

    /** A wreck remains local and non-automatable; the original claimant or a valid Siege looter may recover. */
    public static boolean recoverWreckage(ServerPlayer player, BlockPos pos) {
        EconomyLedgerSavedData ledger = EconomyLedgerSavedData.get(player.server);
        var wreckage = ledger.marketWreckage(player.level().dimension().location(), pos);
        if (wreckage == null) return false;
        boolean siegeLoot = SiegeLootService.access(player, pos).allowed();
        int recovered = 0;
        for (var claim : wreckage.claims()) {
            if (!claim.owner().equals(player.getUUID()) && !siegeLoot && !player.hasPermissions(2)) continue;
            int count = Math.min(claim.quantity(), claim.item().getMaxStackSize());
            ItemStack stack = claim.item().copyWithCount(count);
            player.getInventory().add(stack);
            int received = count - stack.getCount();
            if (received > 0 && ledger.reduceWreckageClaim(player.level().dimension().location(), pos,
                    claim.id(), received)) recovered += received;
        }
        if (ledger.marketWreckage(player.level().dimension().location(), pos) == null)
            player.level().removeBlock(pos, false);
        player.sendSystemMessage(MoveEarthMessage.success("残骸から " + recovered + "個回収しました"));
        return true;
    }

    public static void report(ServerPlayer player, String action, EconomyLedgerSavedData.MarketStatus status) {
        player.sendSystemMessage(MoveEarthMessage.info(action + ": "
                + (status == EconomyLedgerSavedData.MarketStatus.APPLIED ? "完了"
                : status == EconomyLedgerSavedData.MarketStatus.PAYMENT_FAILED ? "残高または決済を確認してください"
                : "条件を満たしていません")));
    }

    @SubscribeEvent public static void onTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (server.getTickCount() % 1200 == 0)
            EconomyLedgerSavedData.get(server).expireMarketOrders(System.currentTimeMillis());
    }
}
