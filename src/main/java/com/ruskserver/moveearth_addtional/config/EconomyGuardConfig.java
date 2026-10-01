package com.ruskserver.moveearth_addtional.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Server-owned limits that keep idle or throwaway accounts from generating and funnelling TC. */
public final class EconomyGuardConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();
    private static final ModConfigSpec.IntValue IDLE_SECONDS = BUILDER
            .comment("A player who has given no input for this many seconds earns no Jobs income",
                    "and no event points. Input is turning the view (being pushed does not turn it) or",
                    "taking a crafting, furnace or gun smith table result; breaking blocks and attacking do not count.")
            .defineInRange("idleSeconds", 300, 60, 3600);
    // Replaces newAccountPlayHours (vanilla per-world play time, which made every veteran "new" on a
    // fresh season world and counted idling). The old key is dropped from the file on load.
    private static final ModConfigSpec.IntValue NEW_ACCOUNT_ACTIVE_HOURS = BUILDER
            .comment("Accounts with less ACTIVE play time than this count as new. Active time is counted on this",
                    "server while the player is online and not idle by the idleSeconds rule above.",
                    "0 disables the transfer limit.")
            .defineInRange("newAccountActiveHours", 4, 0, 1000);
    private static final ModConfigSpec.LongValue NEW_ACCOUNT_DAILY_TRANSFER = BUILDER
            .comment("TC a new account may send per day: player payments, treasury deposits, market purchases",
                    "and buy-order escrow combined.")
            .defineInRange("newAccountDailyTransfer", 20L, 0L, 1_000_000L);
    public static final ModConfigSpec SPEC = BUILDER.build();

    private EconomyGuardConfig() { }

    public static long idleMillis() { return IDLE_SECONDS.getAsInt() * 1000L; }
    public static long newAccountActiveTicks() { return NEW_ACCOUNT_ACTIVE_HOURS.getAsInt() * 72_000L; }
    public static long newAccountDailyTransfer() { return NEW_ACCOUNT_DAILY_TRANSFER.getAsLong(); }
}
