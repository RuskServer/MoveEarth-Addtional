package com.ruskserver.moveearth_addtional.economy;

import com.ruskserver.moveearth_addtional.network.c2s.market.C2S_MarketActionPacket;
import com.ruskserver.moveearth_addtional.network.s2c.market.S2C_MarketSnapshotPacket;
import com.ruskserver.moveearth_addtional.s2.nation.NationSavedData;
import com.tacz.guns.api.item.IGun;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.UUID;

/** Bounded market snapshots and server validation for all client actions. */
public final class MarketScreenSync {
    public static final UUID NONE = new UUID(0L, 0L);
    /**
     * Snapshot results are plain text, or a translation key from the mod's lang files followed by
     * {@code |}-separated arguments; the market screen translates the latter on the client.
     */
    static final String NEW_ACCOUNT_LIMIT_KEY = NewAccountTransferSavedData.LIMIT_KEY;
    static final String TOO_FAST_KEY = "message.moveearth_addtional.market.too_fast";
    private static final java.util.Set<String> ACTIONS = java.util.Set.of(
            "WAYPOINT", "CLEAR_WAYPOINT", "SELL", "BUY_ORDER", "PURCHASE", "DELIVER", "CANCEL", "CLAIM");
    private static final MarketResponseThrottle THROTTLE = new MarketResponseThrottle();
    /** Latest search per player; every snapshot, including coalesced ones, is filtered by it. */
    private static final java.util.Map<UUID, MarketSearch> SEARCHES = new java.util.HashMap<>();
    private MarketScreenSync() { }

    /** Sends replies deferred by the snapshot cooldown; called every server tick. */
    public static void tick(net.minecraft.server.MinecraftServer server) {
        for (var reply : THROTTLE.due(server.getTickCount())) {
            ServerPlayer player = server.getPlayerList().getPlayer(reply.player());
            if (player != null) send(player, reply.selection(), reply.result());
        }
    }

    public static void forget(UUID player) {
        THROTTLE.forget(player);
        SEARCHES.remove(player);
    }

    public static void clear() {
        THROTTLE.clear();
        SEARCHES.clear();
    }

    /** A newly opened screen starts with an empty search box. */
    public static void open(ServerPlayer player, UUID selectedStation) {
        SEARCHES.remove(player.getUUID());
        reply(player, selectedStation, "");
    }

    public static void handle(ServerPlayer player, C2S_MarketActionPacket packet) {
        if (packet == null || packet.action() == null || packet.target() == null) return;
        String action = packet.action();
        UUID target = packet.target();
        // Every request carries the screen's current search, so the reply always matches the box.
        MarketSearch search = MarketSearch.of(packet.query(), packet.itemKeys());
        if (search.blank()) SEARCHES.remove(player.getUUID());
        else SEARCHES.put(player.getUUID(), search);
        if ("REFRESH".equals(action) || "SELECT".equals(action) || "SEARCH".equals(action)) {
            reply(player, target, "");
            return;
        }
        if (!ACTIONS.contains(action)) return;
        EconomyLedgerSavedData ledger = EconomyLedgerSavedData.get(player.server);
        // Every action, valid or not, costs a token: a flood of bogus ids stops here, before the
        // ledger, waypoints or a snapshot rebuild, and its replies are coalesced like browsing.
        if (!THROTTLE.allowAction(player.getUUID(), player.server.getTickCount())) {
            reply(player, null, TOO_FAST_KEY);
            return;
        }
        if ("WAYPOINT".equals(action)) {
            boolean found = EconomyWaypointSync.setMarketWaypoint(player, target);
            reply(player, target, found ? "目的地を設定しました" : "ステーションが見つかりません");
            return;
        }
        if ("CLEAR_WAYPOINT".equals(action)) {
            EconomyWaypointSync.clearWaypoint(player);
            reply(player, target, "目的地を解除しました");
            return;
        }
        if (!com.ruskserver.moveearth_addtional.config.MarketConfig.enabled()
                && !"CANCEL".equals(action) && !"CLAIM".equals(action)) {
            reply(player, target, "市場取引はサーバー設定で一時停止中です");
            return;
        }
        UUID selected = NONE;
        String result = "";
        switch (action) {
            case "SELL" -> {
                selected = target;
                result = message(player, MarketService.listSell(player, target, packet.quantity(), packet.unitPrice()).status());
            }
            case "BUY_ORDER" -> {
                selected = target;
                result = message(player, MarketService.listBuy(player, target, packet.quantity(), packet.unitPrice()).status());
            }
            case "PURCHASE" -> {
                selected = stationForOrder(ledger, target);
                result = message(player, MarketService.purchase(player, target, packet.quantity()));
            }
            case "DELIVER" -> {
                selected = stationForOrder(ledger, target);
                result = message(player, MarketService.deliver(player, target, packet.quantity()));
            }
            case "CANCEL" -> {
                selected = stationForOrder(ledger, target);
                result = message(player, ledger.cancelMarketOrder(player.getUUID(), target, player.hasPermissions(2)));
            }
            case "CLAIM" -> {
                var claim = ledger.marketClaim(player.getUUID(), target);
                if (claim != null) {
                    selected = claim.stationId();
                    int received = MarketService.claim(player, selected, target);
                    result = received > 0 ? received + "個受け取りました" : "現地のステーションと持ち物の空きを確認してください";
                }
            }
            default -> { return; }
        }
        reply(player, selected, result);
    }

    /** Sends the snapshot now, or folds it into the next one when the player was just sent one. */
    private static void reply(ServerPlayer player, UUID selection, String result) {
        var now = THROTTLE.respond(player.getUUID(), selection, result, player.server.getTickCount());
        if (now != null) send(player, now.selection(), now.result());
    }

    private static UUID stationForOrder(EconomyLedgerSavedData ledger, UUID id) {
        MarketOrder order = ledger.marketOrder(id);
        return order == null ? NONE : order.stationId();
    }

    private static String message(ServerPlayer player, EconomyLedgerSavedData.MarketStatus status) {
        if (status == EconomyLedgerSavedData.MarketStatus.NEW_ACCOUNT_LIMIT) {
            long remaining = NewAccountTransferSavedData.get(player.server)
                    .remaining(player, System.currentTimeMillis());
            StringBuilder result = new StringBuilder(NEW_ACCOUNT_LIMIT_KEY);
            for (Object argument : NewAccountTransferSavedData.limitArguments(player, remaining))
                result.append('|').append(argument);
            return result.toString();
        }
        return status == EconomyLedgerSavedData.MarketStatus.APPLIED ? "取引を更新しました"
                : status == EconomyLedgerSavedData.MarketStatus.PAYMENT_FAILED ? "残高または決済を確認してください"
                : "注文・距離・在庫・権限を確認してください";
    }

    private static void send(ServerPlayer player, UUID selectedStation, String result) {
        if (selectedStation == null) selectedStation = NONE;
        var server = player.server;
        var ledger = EconomyLedgerSavedData.get(server);
        var stations = MarketStationSavedData.get(server);
        var nations = NationSavedData.get(server);
        ledger.expireMarketOrders(System.currentTimeMillis());
        UUID ownNation = nations.nationIdFor(player.getUUID()).orElse(null);
        UUID ownStation = ownNation == null ? NONE : stations.forNation(ownNation)
                .map(MarketStationSavedData.Station::id).orElse(NONE);
        UUID selected = stations.byId(selectedStation).isPresent() ? selectedStation : ownStation;
        var playerDimension = player.level().dimension().location();
        var stationEntries = stations.all().stream().limit(256).map(station -> {
            String name = nations.nation(station.nationId()).map(NationSavedData.Nation::name).orElse("旧国家");
            // Cheap reach test first: the full station check (territory, chunk, block entity) only
            // runs for the one or two stations the player stands next to.
            boolean local = playerDimension.equals(station.dimension())
                    && MarketOrderRules.inReach(player.distanceToSqr(station.pos().getX() + 0.5,
                            station.pos().getY() + 0.5, station.pos().getZ() + 0.5))
                    && MarketService.activeStation(server, station.id(), true) != null;
            return new S2C_MarketSnapshotPacket.StationEntry(station.id(), fit(name, 64),
                    fit(station.dimension() + " " + station.pos().toShortString(), 80),
                    station.dimension(), station.pos(),
                    station.nationId().equals(ownNation), local);
        }).toList();
        java.util.Set<UUID> localStations = new java.util.HashSet<>();
        stationEntries.forEach(entry -> { if (entry.local()) localStations.add(entry.id()); });
        MarketSearch search = SEARCHES.getOrDefault(player.getUUID(), MarketSearch.NONE);
        java.util.Map<UUID, MarketOrder> byId = new java.util.HashMap<>();
        java.util.List<MarketOrderSelection.Candidate> candidates = new java.util.ArrayList<>();
        for (MarketOrder order : ledger.marketOrders()) {
            byId.put(order.id(), order);
            String itemId = BuiltInRegistries.ITEM.getKey(order.item().getItem()).toString();
            ResourceLocation gun = gunId(order.item());
            String gunId = gun == null ? null : gun.toString();
            String itemKey = MarketSearch.itemKey(itemId, gunId);
            boolean own = order.owner().equals(player.getUUID());
            // Own orders are always sent, so their names need no matching.
            boolean matches = own || search.blank()
                    || search.matches(itemKey, itemId, gunId, order.item().getHoverName().getString());
            candidates.add(new MarketOrderSelection.Candidate(order.id(), order.side() == MarketOrder.Side.BUY,
                    own, itemKey, order.unitPrice(), matches));
        }
        var orders = MarketOrderSelection.select(candidates).stream().map(candidate -> {
                    MarketOrder order = byId.get(candidate.id());
                    var station = stations.byId(order.stationId()).orElse(null);
                    String nation = station == null ? "撤去済み" : nations.nation(station.nationId())
                            .map(NationSavedData.Nation::name).orElse("旧国家");
                    boolean local = localStations.contains(order.stationId());
                    return new S2C_MarketSnapshotPacket.OrderEntry(order.id(), order.stationId(),
                            order.side().name(), BuiltInRegistries.ITEM.getKey(order.item().getItem()),
                            displayName(order.item()), gunId(order.item()), order.remaining(),
                            order.unitPrice(), fit(nation, 64), candidate.own(), local);
                }).toList();
        var claims = ledger.marketClaims(player.getUUID()).stream().limit(100).map(claim -> {
            var station = stations.byId(claim.stationId()).orElse(null);
            String nation = station == null ? "撤去済み" : nations.nation(station.nationId())
                    .map(NationSavedData.Nation::name).orElse("旧国家");
            boolean local = localStations.contains(claim.stationId());
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
