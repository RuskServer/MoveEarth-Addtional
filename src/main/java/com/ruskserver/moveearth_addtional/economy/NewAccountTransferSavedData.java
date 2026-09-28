package com.ruskserver.moveearth_addtional.economy;

import com.ruskserver.moveearth_addtional.beginner.BeginnerKitService;
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
 * TC sent today by accounts still inside the new-account period, across player payments and
 * treasury deposits. Kept apart from the ledger journal, which is trimmed and mixes in income.
 */
public final class NewAccountTransferSavedData extends SavedData {
    private final Map<UUID, Sent> sent = new HashMap<>();

    private record Sent(long day, long amount) { }

    /** TC the player may still send today (Long.MAX_VALUE once the account is no longer new). */
    public long remaining(ServerPlayer player, long nowMillis) {
        Sent today = sent.get(player.getUUID());
        long day = EarningPolicy.day(nowMillis);
        return EarningPolicy.remainingTransfer(BeginnerKitService.playTimeTicks(player),
                EconomyGuardConfig.newAccountPlayTicks(),
                today == null || today.day() != day ? 0L : today.amount(),
                EconomyGuardConfig.newAccountDailyTransfer());
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
