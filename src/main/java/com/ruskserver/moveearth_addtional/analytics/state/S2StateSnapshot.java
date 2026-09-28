package com.ruskserver.moveearth_addtional.analytics.state;

import java.util.List;

/**
 * The Season 2 world as the operators' dashboard shows it: nations, the money
 * supply and every war in progress, captured on the server thread.
 */
public record S2StateSnapshot(long capturedAt, List<NationRow> nations, EconomyRow economy,
                              List<SiegeRow> sieges, List<FallenRow> fallen) {

    public static final S2StateSnapshot EMPTY = new S2StateSnapshot(0L, List.of(),
            new EconomyRow(0L, 0L, 0L, 0, 0, 0, 0), List.of(), List.of());

    public record NationRow(String id, String name, String tag, String ownerName, int members, int online,
                            long treasury, int chunks, int cores, int outposts, int fallenCores, int vehicles,
                            String upkeep, int activeSieges) { }

    /** Balances summed by account kind; together they are all TC in circulation. */
    public record EconomyRow(long playerBalances, long nationBalances, long escrowBalances, int nations,
                             int playersInNations, int onlinePlayers, int openOrders) {
        public long moneySupply() { return playerBalances + nationBalances + escrowBalances; }
    }

    public record SiegeRow(String id, String attacker, String attackerName, boolean individual, String defender,
                           String defenderName, String phase, long remainingSeconds, String dimension,
                           int x, int y, int z) { }

    public record FallenRow(String siegeId, String attacker, String attackerName, boolean individual,
                            String defender, String defenderName, String coreType, int stage,
                            long remainingSeconds, long captureSeconds, String dimension, int x, int y, int z) { }
}
