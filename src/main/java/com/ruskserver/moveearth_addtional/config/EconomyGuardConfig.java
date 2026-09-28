package com.ruskserver.moveearth_addtional.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Server-owned limits that keep idle or throwaway accounts from generating and funnelling TC. */
public final class EconomyGuardConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();
    private static final ModConfigSpec.IntValue IDLE_SECONDS = BUILDER
            .comment("A player who has not turned their view for this many seconds earns no Jobs income",
                    "and no event points. Only player input turns the view; being pushed does not.")
            .defineInRange("idleSeconds", 300, 60, 3600);
    private static final ModConfigSpec.IntValue NEW_ACCOUNT_HOURS = BUILDER
            .comment("Accounts with less total play time than this count as new. 0 disables the transfer limit.")
            .defineInRange("newAccountPlayHours", 8, 0, 1000);
    private static final ModConfigSpec.LongValue NEW_ACCOUNT_DAILY_TRANSFER = BUILDER
            .comment("TC a new account may send per day, player payments and treasury deposits combined.")
            .defineInRange("newAccountDailyTransfer", 20L, 0L, 1_000_000L);
    public static final ModConfigSpec SPEC = BUILDER.build();

    private EconomyGuardConfig() { }

    public static long idleMillis() { return IDLE_SECONDS.getAsInt() * 1000L; }
    public static int newAccountPlayTicks() { return NEW_ACCOUNT_HOURS.getAsInt() * 72_000; }
    public static long newAccountDailyTransfer() { return NEW_ACCOUNT_DAILY_TRANSFER.getAsLong(); }
}
