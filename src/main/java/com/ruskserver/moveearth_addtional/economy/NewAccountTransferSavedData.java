package com.ruskserver.moveearth_addtional.economy;

import com.ruskserver.moveearth_addtional.config.EconomyGuardConfig;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * TC sent today by accounts still inside the new-account period, across player payments, treasury
 * deposits, market purchases and buy-order escrow. Kept apart from the ledger journal, which is
 * trimmed and mixes in income. An account is new until it has {@code newAccountActiveHours} of active
 * play time ({@link ActivePlayTimeSavedData}).
 */
public final class NewAccountTransferSavedData extends SavedData {
    /**
     * Refusal shown when a new account's transfer would exceed today's allowance; arguments from
     * {@link #limitArguments}.
     */
    public static final String LIMIT_KEY = "message.moveearth_addtional.market.new_account_limit";
    private final Map<UUID, Sent> sent = new HashMap<>();

    private record Sent(long day, long amount) { }

    /** TC the player may still send today (Long.MAX_VALUE once the account is no longer new). */
    public long remaining(ServerPlayer player, long nowMillis) {
        Sent today = sent.get(player.getUUID());
        long day = EarningPolicy.day(nowMillis);
        return EarningPolicy.remainingTransfer(activeTicks(player),
                EconomyGuardConfig.newAccountActiveTicks(),
                today == null || today.day() != day ? 0L : today.amount(),
                EconomyGuardConfig.newAccountDailyTransfer());
    }

    /** Active play ticks still needed before the limit lifts; 0 once it no longer applies. */
    public static long activeTicksLeft(ServerPlayer player) {
        return EarningPolicy.newAccountTicksLeft(activeTicks(player), EconomyGuardConfig.newAccountActiveTicks());
    }

    private static long activeTicks(ServerPlayer player) {
        return ActivePlayTimeSavedData.get(player.server).ticks(player.getUUID());
    }

    /** Arguments of {@link #LIMIT_KEY}: daily limit, TC left today, active hours and minutes left. */
    public static Object[] limitArguments(ServerPlayer player, long remainingToday) {
        long minutes = (activeTicksLeft(player) + 1_199L) / 1_200L;
        return new Object[] {String.valueOf(EconomyGuardConfig.newAccountDailyTransfer()),
                String.valueOf(remainingToday), String.valueOf(minutes / 60L), String.valueOf(minutes % 60L)};
    }

    public static net.minecraft.network.chat.Component limitMessage(ServerPlayer player, long remainingToday) {
        return net.minecraft.network.chat.Component.translatable(LIMIT_KEY, limitArguments(player, remainingToday));
    }

    /** Call only after the ledger transfer was applied. */
    public void record(ServerPlayer player, long amount, long nowMillis) {
        if (remaining(player, nowMillis) == Long.MAX_VALUE) return;
        long day = EarningPolicy.day(nowMillis);
        Sent today = sent.get(player.getUUID());
        long previous = today == null || today.day() != day ? 0L : today.amount();
        sent.put(player.getUUID(), new Sent(day, previous + amount));
        setDirty();
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        sent.forEach((player, value) -> {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("Player", player);
            entry.putLong("Day", value.day());
            entry.putLong("Amount", value.amount());
            list.add(entry);
        });
        tag.put("Sent", list);
        return tag;
    }

    public static NewAccountTransferSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        NewAccountTransferSavedData data = new NewAccountTransferSavedData();
        ListTag list = tag.getList("Sent", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            if (!entry.hasUUID("Player")) continue;
            data.sent.put(entry.getUUID("Player"), new Sent(entry.getLong("Day"), entry.getLong("Amount")));
        }
        return data;
    }

    public static NewAccountTransferSavedData get(net.minecraft.server.MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(NewAccountTransferSavedData::new, NewAccountTransferSavedData::load, null),
                "moveearth_new_account_transfers");
    }
}
