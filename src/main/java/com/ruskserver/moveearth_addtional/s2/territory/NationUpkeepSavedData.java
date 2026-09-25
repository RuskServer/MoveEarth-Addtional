package com.ruskserver.moveearth_addtional.s2.territory;

import com.ruskserver.moveearth_addtional.config.S2TerritoryConfig;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public final class NationUpkeepSavedData extends SavedData {
    private final Map<UUID, AccountState> accounts = new LinkedHashMap<>();

    public AccountState state(UUID nationId) {
        return accounts.computeIfAbsent(nationId, ignored -> new AccountState());
    }

    public Map<UUID, AccountState> accounts() {
        return Map.copyOf(accounts);
    }

    public void removeNation(UUID nationId) {
        if (accounts.remove(nationId) != null) setDirty();
    }

    public void ensureScheduled(UUID nationId, long now) {
        AccountState state = state(nationId);
        if (state.nextDueAt > 0L) return;
        state.nextDueAt = now + S2TerritoryConfig.upkeepCycleMillis();
        setDirty();
    }

    public void paymentSucceeded(UUID nationId, long now) {
        AccountState state = state(nationId);
        state.nextDueAt = now + S2TerritoryConfig.upkeepCycleMillis();
        state.nextAttemptAt = 0L;
        state.failedPayments = 0;
        state.overdueSince = 0L;
        setDirty();
    }

    public void paymentFailed(UUID nationId, long now) {
        AccountState state = state(nationId);
        state.failedPayments = Math.min(1_000_000, state.failedPayments + 1);
        if (state.overdueSince <= 0L) state.overdueSince = now;
        state.nextAttemptAt = now + S2TerritoryConfig.upkeepRetryMillis();
        setDirty();
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (Map.Entry<UUID, AccountState> entry : accounts.entrySet()) {
            AccountState state = entry.getValue();
            CompoundTag value = new CompoundTag();
            value.putUUID("Nation", entry.getKey());
            value.putLong("NextDueAt", state.nextDueAt);
            value.putLong("NextAttemptAt", state.nextAttemptAt);
            value.putInt("FailedPayments", state.failedPayments);
            value.putLong("OverdueSince", state.overdueSince);
            list.add(value);
        }
        tag.put("Accounts", list);
        return tag;
    }

    public static NationUpkeepSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        NationUpkeepSavedData data = new NationUpkeepSavedData();
        ListTag list = tag.getList("Accounts", Tag.TAG_COMPOUND);
        for (int index = 0; index < list.size(); index++) {
            CompoundTag value = list.getCompound(index);
            if (!value.hasUUID("Nation")) continue;
            AccountState state = new AccountState();
            state.nextDueAt = Math.max(0L, value.getLong("NextDueAt"));
            state.nextAttemptAt = Math.max(0L, value.getLong("NextAttemptAt"));
            state.failedPayments = Math.max(0, value.getInt("FailedPayments"));
            state.overdueSince = Math.max(0L, value.getLong("OverdueSince"));
            if (state.failedPayments > 0 && state.overdueSince <= 0L) {
                state.overdueSince = state.nextDueAt > 0L ? state.nextDueAt : System.currentTimeMillis();
            }
            data.accounts.put(value.getUUID("Nation"), state);
        }
        return data;
    }

    public static NationUpkeepSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(NationUpkeepSavedData::new, NationUpkeepSavedData::load, null),
                "moveearth_nation_upkeep");
    }

    public static final class AccountState {
        private long nextDueAt;
        private long nextAttemptAt;
        private int failedPayments;
        private long overdueSince;

        public long nextDueAt() { return nextDueAt; }
        public long nextAttemptAt() { return nextAttemptAt; }
        public int failedPayments() { return failedPayments; }
        public long overdueSince() { return overdueSince; }
    }
}
