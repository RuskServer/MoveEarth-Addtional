package com.ruskserver.moveearth_addtional.analytics.state;

import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import com.ruskserver.moveearth_addtional.analytics.queue.AnalyticsEventQueue;
import com.ruskserver.moveearth_addtional.analytics.storage.AnalyticsStorageService;
import com.ruskserver.moveearth_addtional.economy.EconomyLedgerSavedData;
import com.ruskserver.moveearth_addtional.s2.nation.NationSavedData;
import com.ruskserver.moveearth_addtional.s2.siege.SiegeSavedData;
import com.ruskserver.moveearth_addtional.s2.territory.NationUpkeepService;
import com.ruskserver.moveearth_addtional.s2.territory.TerritorySavedData;
import com.ruskserver.moveearth_addtional.s2.vehicle.VehicleSavedData;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.*;

/**
 * Captures {@link S2StateSnapshot} once a minute for the web dashboard, which
 * cannot read SavedData from its own threads, and stores every tenth capture so
 * the dashboard can chart nations and the money supply over time.
 */
@EventBusSubscriber(modid = Moveearth_addtional.MODID)
public final class S2StateSnapshotService {
    private static final long CAPTURE_INTERVAL_TICKS = 20L * 60L;
    private static final int STORE_EVERY_CAPTURES = 10;

    private static int captures;

    private S2StateSnapshotService() { }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (server.overworld().getGameTime() % CAPTURE_INTERVAL_TICKS != 41L) return;
        if (!AnalyticsStorageService.INSTANCE.isRunning()) return;
        try {
            S2StateSnapshot snapshot = capture(server);
            S2StateSnapshotStore.publish(snapshot);
            if (captures++ % STORE_EVERY_CAPTURES == 0) {
                AnalyticsEventQueue.INSTANCE.enqueue(new AnalyticsEventQueue.StateSnapshotEvent(snapshot));
            }
        } catch (RuntimeException exception) {
            Moveearth_addtional.LOGGER.warn("Analytics state snapshot failed", exception);
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        S2StateSnapshotStore.publish(null);
        captures = 0;
    }

    static S2StateSnapshot capture(MinecraftServer server) {
        NationSavedData nations = NationSavedData.get(server);
        TerritorySavedData territories = TerritorySavedData.get(server);
        SiegeSavedData sieges = SiegeSavedData.get(server);
        VehicleSavedData vehicles = VehicleSavedData.get(server);
        EconomyLedgerSavedData ledger = EconomyLedgerSavedData.get(server);

        Map<UUID, Integer> online = new HashMap<>();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            nations.nationIdFor(player.getUUID()).ifPresent(id -> online.merge(id, 1, Integer::sum));
        }
        Map<UUID, int[]> coreCounts = new HashMap<>();
        for (TerritorySavedData.CoreRecord core : territories.cores()) {
            if (core.nationId() == null) continue;
            int[] counts = coreCounts.computeIfAbsent(core.nationId(), id -> new int[3]);
            if (core.state() == TerritorySavedData.CoreState.FALLEN
                    || core.state() == TerritorySavedData.CoreState.DEFEATED) counts[2]++;
            else if (core.state() != TerritorySavedData.CoreState.CONFIGURING) {
                counts[0]++;
                if (core.type() == TerritorySavedData.CoreType.OUTPOST) counts[1]++;
            }
        }

        List<S2StateSnapshot.NationRow> nationRows = new ArrayList<>();
        Map<UUID, S2StateSnapshot.SiegeRow> siegeRows = new LinkedHashMap<>();
        Map<UUID, S2StateSnapshot.FallenRow> fallenRows = new LinkedHashMap<>();
        int playersInNations = 0;
        for (NationSavedData.Nation nation : nations.nations().values()) {
            UUID id = nation.id();
            int[] counts = coreCounts.getOrDefault(id, new int[3]);
            NationSavedData.Member owner = nation.members().get(nation.ownerId());
            List<SiegeSavedData.SiegeRecord> active = sieges.activeFor(id);
            playersInNations += nation.members().size();
            nationRows.add(new S2StateSnapshot.NationRow(id.toString(), nation.name(), nation.tag(),
                    owner == null ? "" : owner.lastKnownName(), nation.members().size(),
                    online.getOrDefault(id, 0), ledger.balance(EconomyLedgerSavedData.Account.nation(id)),
                    territories.controlledChunkCount(id), counts[0], counts[1], counts[2], vehicles.count(id),
                    NationUpkeepService.penalty(server, id).name(), active.size()));
            for (SiegeSavedData.SiegeRecord siege : active) {
                siegeRows.computeIfAbsent(siege.id(), key -> new S2StateSnapshot.SiegeRow(key.toString(),
                        String.valueOf(siege.attackerNation()),
                        partyName(server, nations, siege.attackerNation(), siege.individualAttacker()),
                        siege.individualAttacker(), String.valueOf(siege.defenderNation()),
                        partyName(server, nations, siege.defenderNation(), false), siege.phase().name(),
                        siege.remainingTicks() / 20L, siege.dimension().toString(),
                        siege.corePos().getX(), siege.corePos().getY(), siege.corePos().getZ()));
            }
            for (SiegeSavedData.FallenRecord fallen : sieges.fallenFor(id)) {
                fallenRows.computeIfAbsent(fallen.siegeId(), key -> new S2StateSnapshot.FallenRow(key.toString(),
                        String.valueOf(fallen.attackerNation()),
                        partyName(server, nations, fallen.attackerNation(), fallen.individualAttacker()),
                        fallen.individualAttacker(), String.valueOf(fallen.defenderNation()),
                        partyName(server, nations, fallen.defenderNation(), false),
                        String.valueOf(fallen.coreType()), fallen.stage(), fallen.remainingTicks() / 20L,
                        fallen.captureTicks() / 20L, fallen.dimension().toString(),
                        fallen.corePos().getX(), fallen.corePos().getY(), fallen.corePos().getZ()));
            }
        }
        nationRows.sort(Comparator.comparingInt(S2StateSnapshot.NationRow::chunks).reversed());

        long playerBalances = 0L;
        long nationBalances = 0L;
        long escrowBalances = 0L;
        for (Map.Entry<EconomyLedgerSavedData.Account, Long> entry : ledger.balances().entrySet()) {
            switch (entry.getKey().kind()) {
                case PLAYER -> playerBalances += entry.getValue();
                case NATION -> nationBalances += entry.getValue();
                case ESCROW -> escrowBalances += entry.getValue();
            }
        }
        S2StateSnapshot.EconomyRow economy = new S2StateSnapshot.EconomyRow(playerBalances, nationBalances,
                escrowBalances, nationRows.size(), playersInNations, server.getPlayerList().getPlayerCount(),
                ledger.marketOrders().size());
        return new S2StateSnapshot(System.currentTimeMillis() / 1000L, List.copyOf(nationRows), economy,
                List.copyOf(siegeRows.values()), List.copyOf(fallenRows.values()));
    }

    private static String partyName(MinecraftServer server, NationSavedData nations, UUID id, boolean individual) {
        if (id == null) return "?";
        if (individual) {
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            if (player != null) return player.getGameProfile().getName();
            String known = EconomyLedgerSavedData.get(server).knownPlayerName(id);
            return known == null ? id.toString().substring(0, 8) : known;
        }
        return nations.nation(id).map(NationSavedData.Nation::name).orElse("?");
    }
}
