package com.ruskserver.moveearth_addtional.economy;

import com.ruskserver.moveearth_addtional.network.C2S_MarketActionPacket;
import com.ruskserver.moveearth_addtional.network.S2C_MarketSnapshotPacket;
import com.ruskserver.moveearth_addtional.s2.nation.NationSavedData;
import com.tacz.guns.api.item.IGun;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.Comparator;
import java.util.UUID;

/** Bounded market snapshots and server validation for all client actions. */
public final class MarketScreenSync {
    public static final UUID NONE = new UUID(0L, 0L);
    private MarketScreenSync() { }

    public static void open(ServerPlayer player, UUID selectedStation) {
        send(player, selectedStation, "");
    }

    public static void handle(ServerPlayer player, C2S_MarketActionPacket packet) {
        if (packet == null) return;
        if ("WAYPOINT".equals(packet.action())) {
            boolean found = EconomyWaypointSync.setMarketWaypoint(player, packet.target());
            send(player, packet.target(), found ? "目的地を設定しました" : "ステーションが見つかりません");
            return;
        }
        if ("CLEAR_WAYPOINT".equals(packet.action())) {
            EconomyWaypointSync.clearWaypoint(player);
            send(player, packet.target(), "目的地を解除しました");
            return;
        }
        if (!com.ruskserver.moveearth_addtional.config.MarketConfig.enabled()
                && !"REFRESH".equals(packet.action()) && !"SELECT".equals(packet.action())
                && !"CANCEL".equals(packet.action()) && !"CLAIM".equals(packet.action())) {
            send(player, packet.target(), "市場取引はサーバー設定で一時停止中です");
            return;
        }
        EconomyLedgerSavedData ledger = EconomyLedgerSavedData.get(player.server);
        UUID target = packet.target();
        UUID selected = NONE;
        String result = "";
        switch (packet.action()) {
            case "REFRESH", "SELECT" -> selected = target;
            case "SELL" -> {
                selected = target;
                result = message(MarketService.listSell(player, target, packet.quantity(), packet.unitPrice()).status());
            }
            case "BUY_ORDER" -> {
                selected = target;
                result = message(MarketService.listBuy(player, target, packet.quantity(), packet.unitPrice()).status());
            }
            case "PURCHASE" -> {
                selected = stationForOrder(ledger, target);
                result = message(MarketService.purchase(player, target, packet.quantity()));
            }
            case "DELIVER" -> {
                selected = stationForOrder(ledger, target);
                result = message(MarketService.deliver(player, target, packet.quantity()));
            }
            case "CANCEL" -> {
                selected = stationForOrder(ledger, target);
                result = message(ledger.cancelMarketOrder(player.getUUID(), target, player.hasPermissions(2)));
            }
            case "CLAIM" -> {
                var claim = ledger.marketClaims(player.getUUID()).stream()
                        .filter(c -> c.id().equals(target)).findFirst().orElse(null);
                if (claim != null) {
                    selected = claim.stationId();
                    int received = MarketService.claim(player, selected, target);
                    result = received > 0 ? received + "個受け取りました" : "現地のステーションと持ち物の空きを確認してください";
                }
            }
            default -> { return; }
        }
        send(player, selected, result);
    }

    private static UUID stationForOrder(EconomyLedgerSavedData ledger, UUID id) {
        return ledger.marketOrders().stream().filter(order -> order.id().equals(id))
                .map(MarketOrder::stationId).findFirst().orElse(NONE);
    }

    private static String message(EconomyLedgerSavedData.MarketStatus status) {
        return status == EconomyLedgerSavedData.MarketStatus.APPLIED ? "取引を更新しました"
                : status == EconomyLedgerSavedData.MarketStatus.PAYMENT_FAILED ? "残高または決済を確認してください"
                : "注文・距離・在庫・権限を確認してください";
    }

    private static void send(ServerPlayer player, UUID selectedStation, String result) {
        var server = player.server;
        var ledger = EconomyLedgerSavedData.get(server);
        var stations = MarketStationSavedData.get(server);
        var nations = NationSavedData.get(server);
        ledger.expireMarketOrders(System.currentTimeMillis());
        UUID ownNation = nations.nationIdFor(player.getUUID()).orElse(null);
        UUID ownStation = ownNation == null ? NONE : stations.forNation(ownNation)
                .map(MarketStationSavedData.Station::id).orElse(NONE);
        UUID selected = stations.byId(selectedStation).isPresent() ? selectedStation : ownStation;
        var stationEntries = stations.all().stream().limit(256).map(station -> {
            String name = nations.nation(station.nationId()).map(NationSavedData.Nation::name).orElse("旧国家");
            boolean local = MarketService.activeStation(server, station.id(), true) != null
                    && player.level().dimension().location().equals(station.dimension())
                    && MarketOrderRules.inReach(player.distanceToSqr(station.pos().getX() + 0.5,
                            station.pos().getY() + 0.5, station.pos().getZ() + 0.5));
            return new S2C_MarketSnapshotPacket.StationEntry(station.id(), fit(name, 64),
                    fit(station.dimension() + " " + station.pos().toShortString(), 80),
                    station.dimension(), station.pos(),
                    station.nationId().equals(ownNation), local);
        }).toList();
        var visibleOrders = java.util.stream.Stream.concat(
                ledger.marketOrders().stream().filter(order -> order.side() == MarketOrder.Side.SELL)
                        .sorted(Comparator.comparingLong(MarketOrder::unitPrice)).limit(50),
                ledger.marketOrders().stream().filter(order -> order.side() == MarketOrder.Side.BUY)
                        .sorted(Comparator.comparingLong(MarketOrder::unitPrice).reversed()).limit(50));
        var orders = visibleOrders.map(order -> {
                    var station = stations.byId(order.stationId()).orElse(null);
                    String nation = station == null ? "撤去済み" : nations.nation(station.nationId())
                            .map(NationSavedData.Nation::name).orElse("旧国家");
                    boolean local = stationEntries.stream().anyMatch(s -> s.id().equals(order.stationId()) && s.local());
                    return new S2C_MarketSnapshotPacket.OrderEntry(order.id(), order.stationId(),
                            order.side().name(), BuiltInRegistries.ITEM.getKey(order.item().getItem()),
                            displayName(order.item()), gunId(order.item()), order.remaining(),
                            order.unitPrice(), fit(nation, 64), order.owner().equals(player.getUUID()), local);
                }).toList();
        var claims = ledger.marketClaims(player.getUUID()).stream().limit(100).map(claim -> {
            var station = stations.byId(claim.stationId()).orElse(null);
            String nation = station == null ? "撤去済み" : nations.nation(station.nationId())
                    .map(NationSavedData.Nation::name).orElse("旧国家");
            boolean local = stationEntries.stream().anyMatch(s -> s.id().equals(claim.stationId()) && s.local());
            return new S2C_MarketSnapshotPacket.ClaimEntry(claim.id(), claim.stationId(),
                    displayName(claim.item()), gunId(claim.item()),
                    claim.quantity(), fit(nation, 64), local);
        }).toList();
        PacketDistributor.sendToPlayer(player, new S2C_MarketSnapshotPacket(
                ledger.balance(EconomyLedgerSavedData.Account.player(player.getUUID())), selected,
                stationEntries, orders, claims, fit(result, 160)));
    }

    private static String fit(String value, int max) {
        return value.length() > max ? value.substring(0, max) : value;
    }

    private static Component displayName(ItemStack stack) {
        Component name = stack.getHoverName();
        String expanded = name.getString();
        // Keep normal translatable components intact so every client can apply its own language.
        // Extremely long custom names are flattened to keep one snapshot bounded.
        return expanded.length() <= 128 ? name : Component.literal(expanded.substring(0, 128));
    }

    private static ResourceLocation gunId(ItemStack stack) {
        IGun gun = IGun.getIGunOrNull(stack);
        return gun == null ? null : gun.getGunId(stack);
    }
}
