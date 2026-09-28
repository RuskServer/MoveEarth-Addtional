package com.ruskserver.moveearth_addtional.analytics.event;

import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import com.ruskserver.moveearth_addtional.analytics.queue.AnalyticsEventQueue;
import com.ruskserver.moveearth_addtional.analytics.storage.AnalyticsStorageService;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

/**
 * Where game code reports Season 2 events for the operators' analytics. Every
 * call is fire-and-forget: nothing is recorded while analytics is not running,
 * and no failure here may ever reach the gameplay code that called it.
 */
public final class GameEvents {
    private GameEvents() { }

    public static void record(GameEventType type, UUID player, UUID nation,
                              ResourceLocation dimension, BlockPos pos, long value, String detail) {
        if (!AnalyticsStorageService.INSTANCE.isRunning()) return;
        try {
            AnalyticsEventQueue.INSTANCE.enqueue(new AnalyticsEventQueue.GameEventLogEvent(new GameEventRecord(
                    System.currentTimeMillis() / 1000L, type.id(), player, nation,
                    dimension == null ? null : dimension.toString(),
                    pos == null ? null : pos.getX(), pos == null ? null : pos.getY(), pos == null ? null : pos.getZ(),
                    value, detail)));
        } catch (RuntimeException exception) {
            Moveearth_addtional.LOGGER.warn("Analytics event {} was not recorded", type.id(), exception);
        }
    }

    /** An event about a player, located where they stand and tagged with their current nation. */
    public static void player(GameEventType type, ServerPlayer player, long value, String detail) {
        if (player == null || !AnalyticsStorageService.INSTANCE.isRunning()) return;
        try {
            UUID nation = com.ruskserver.moveearth_addtional.s2.nation.NationSavedData.get(player.server)
                    .nationIdFor(player.getUUID()).orElse(null);
            record(type, player.getUUID(), nation, player.level().dimension().location(),
                    player.blockPosition(), value, detail);
        } catch (RuntimeException exception) {
            Moveearth_addtional.LOGGER.warn("Analytics event {} was not recorded", type.id(), exception);
        }
    }

    /** An event about a player where they stand, tagged with a nation other than their own. */
    public static void player(GameEventType type, ServerPlayer player, UUID nation, long value, String detail) {
        if (player == null) return;
        record(type, player.getUUID(), nation, player.level().dimension().location(), player.blockPosition(),
                value, detail);
    }

    /** An event about a player UUID whose entity may be offline. */
    public static void player(GameEventType type, UUID player, UUID nation, long value, String detail) {
        record(type, player, nation, null, null, value, detail);
    }

    /**
     * One ledger movement. The player and nation columns hold whichever sides are
     * player and nation accounts; the detail is the reason and the direction, such
     * as {@code market_purchase|PLAYER>ESCROW}, where MINT and BURN mark money
     * entering or leaving the economy.
     */
    public static void ledger(com.ruskserver.moveearth_addtional.economy.EconomyLedgerSavedData.Account from,
                              com.ruskserver.moveearth_addtional.economy.EconomyLedgerSavedData.Account to,
                              long amount, String reason) {
        if (!AnalyticsStorageService.INSTANCE.isRunning()) return;
        UUID player = side(from, to, com.ruskserver.moveearth_addtional.economy.EconomyLedgerSavedData.Kind.PLAYER);
        UUID nation = side(from, to, com.ruskserver.moveearth_addtional.economy.EconomyLedgerSavedData.Kind.NATION);
        String direction = (from == null ? "MINT" : from.kind().name()) + ">" + (to == null ? "BURN" : to.kind().name());
        record(GameEventType.LEDGER, player, nation, null, null, amount, reason + "|" + direction);
    }

    private static UUID side(com.ruskserver.moveearth_addtional.economy.EconomyLedgerSavedData.Account from,
                             com.ruskserver.moveearth_addtional.economy.EconomyLedgerSavedData.Account to,
                             com.ruskserver.moveearth_addtional.economy.EconomyLedgerSavedData.Kind kind) {
        if (from != null && from.kind() == kind) return from.id();
        if (to != null && to.kind() == kind) return to.id();
        return null;
    }

    /** An event about a place, such as a territory core. */
    public static void place(GameEventType type, UUID nation, ResourceLocation dimension, BlockPos pos,
                             long value, String detail) {
        record(type, null, nation, dimension, pos, value, detail);
    }
}
