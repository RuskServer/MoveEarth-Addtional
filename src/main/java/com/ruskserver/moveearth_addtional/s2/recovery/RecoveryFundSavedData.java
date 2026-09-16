package com.ruskserver.moveearth_addtional.s2.recovery;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class RecoveryFundSavedData extends SavedData {
    private long balance;
    private long reserved;
    private long revision;
    private final Map<UUID, Transaction> transactions = new LinkedHashMap<>();

    public long balance() { return balance; }
    public long reserved() { return reserved; }
    public long available() { return Math.max(0L, balance - reserved); }
    public long revision() { return revision; }
    public List<Transaction> transactions() { return List.copyOf(transactions.values()); }
    public Optional<Transaction> transaction(UUID id) { return Optional.ofNullable(transactions.get(id)); }
    public Optional<Transaction> transaction(Type type, UUID sourceId) {
        if (type == null || sourceId == null) return Optional.empty();
        return transactions.values().stream().filter(value -> value.type() == type
                && sourceId.equals(value.sourceId()) && value.state() != State.REFUNDED).findFirst();
    }

    public Transaction prepare(Type type, UUID nationId, UUID sourceId, long amount, long openTick,
                               String detail) {
        if (amount < 0L) return null;
        Transaction value = new Transaction(UUID.randomUUID(), type, nationId, sourceId, amount,
                State.PREPARED, Math.max(0L, openTick), detail == null ? "" : detail);
        transactions.put(value.id(), value);
        changed();
        return value;
    }

    public boolean markWithdrawn(UUID id) { return transition(id, State.PREPARED, State.WITHDRAWN); }

    public boolean abortPrepared(UUID id, String detail) {
        Transaction before = transactions.get(id);
        if (before == null || before.state() != State.PREPARED) return false;
        transactions.put(id, new Transaction(before.id(), before.type(), before.nationId(), before.sourceId(),
                before.amount(), State.REFUNDED, before.openTick(), detail == null ? "" : detail));
        changed();
        return true;
    }

    /** Completes an externally journalled movement without changing the recovery fund balance. */
    public boolean completeExternal(UUID id) { return transition(id, State.WITHDRAWN, State.PAID); }

    public boolean credit(UUID id) {
        Transaction before = transactions.get(id);
        if (before == null || before.state() != State.PREPARED && before.state() != State.WITHDRAWN) return false;
        balance = safeAdd(balance, before.amount());
        transactions.put(id, before.withState(State.PAID));
        changed();
        return true;
    }

    public boolean reserve(UUID id) {
        Transaction before = transactions.get(id);
        if (before == null || before.state() != State.PREPARED || available() < before.amount()) return false;
        reserved = safeAdd(reserved, before.amount());
        transactions.put(id, before.withState(State.RESERVED));
        changed();
        return true;
    }

    public boolean consume(UUID id, long usedAmount) {
        Transaction before = transactions.get(id);
        if (before == null || before.state() != State.RESERVED || usedAmount < 0L
                || usedAmount > before.amount()) return false;
        reserved = Math.max(0L, reserved - before.amount());
        balance = Math.max(0L, balance - usedAmount);
        transactions.put(id, before.withAmount(usedAmount).withState(State.PAID));
        changed();
        return true;
    }

    public boolean refund(UUID id) {
        Transaction before = transactions.get(id);
        if (before == null || before.state() != State.RESERVED) return false;
        reserved = Math.max(0L, reserved - before.amount());
        transactions.put(id, before.withState(State.REFUNDED));
        changed();
        return true;
    }

    public boolean reviewRequired(UUID id, String detail) {
        Transaction before = transactions.get(id);
        if (before == null || terminal(before.state())) return false;
        transactions.put(id, new Transaction(before.id(), before.type(), before.nationId(), before.sourceId(),
                before.amount(), State.REVIEW_REQUIRED, before.openTick(), detail == null ? "" : detail));
        changed();
        return true;
    }

    public long paidToNationSince(UUID nationId, long sinceOpenTick) {
        return transactions.values().stream().filter(value -> nationId.equals(value.nationId())
                && value.type().aid() && value.state() == State.PAID
                && value.openTick() >= sinceOpenTick).mapToLong(Transaction::amount).sum();
    }

    public long globallyPaidSince(long sinceOpenTick) {
        return transactions.values().stream().filter(value -> value.type().aid()
                && value.state() == State.PAID && value.openTick() >= sinceOpenTick)
                .mapToLong(Transaction::amount).sum();
    }

    private boolean transition(UUID id, State expected, State next) {
        Transaction before = transactions.get(id);
        if (before == null || before.state() != expected) return false;
        transactions.put(id, before.withState(next));
        changed();
        return true;
    }

    private void changed() { revision++; setDirty(); }
    private static boolean terminal(State state) { return state == State.PAID || state == State.REFUNDED; }
    private static long safeAdd(long left, long right) {
        return left > Long.MAX_VALUE - right ? Long.MAX_VALUE : left + right;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt("SchemaVersion", 1);
        tag.putLong("Balance", balance);
        tag.putLong("Reserved", reserved);
        tag.putLong("Revision", revision);
        ListTag list = new ListTag();
        for (Transaction value : transactions.values()) {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("Id", value.id());
            entry.putString("Type", value.type().name());
            if (value.nationId() != null) entry.putUUID("Nation", value.nationId());
            if (value.sourceId() != null) entry.putUUID("Source", value.sourceId());
            entry.putLong("Amount", value.amount());
            entry.putString("State", value.state().name());
            entry.putLong("OpenTick", value.openTick());
            entry.putString("Detail", value.detail());
            list.add(entry);
        }
        tag.put("Transactions", list);
        return tag;
    }

    public static RecoveryFundSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        RecoveryFundSavedData data = new RecoveryFundSavedData();
        data.balance = Math.max(0L, tag.getLong("Balance"));
        data.reserved = Math.min(data.balance, Math.max(0L, tag.getLong("Reserved")));
        data.revision = Math.max(0L, tag.getLong("Revision"));
        ListTag list = tag.getList("Transactions", Tag.TAG_COMPOUND);
        for (int index = 0; index < list.size(); index++) {
            CompoundTag entry = list.getCompound(index);
            if (!entry.hasUUID("Id")) continue;
            Type type;
            State state;
            try { type = Type.valueOf(entry.getString("Type")); } catch (IllegalArgumentException ignored) { continue; }
            try { state = State.valueOf(entry.getString("State")); } catch (IllegalArgumentException ignored) { state = State.REVIEW_REQUIRED; }
            if (state == State.PREPARED || state == State.WITHDRAWN) state = State.REVIEW_REQUIRED;
            Transaction value = new Transaction(entry.getUUID("Id"), type,
                    entry.hasUUID("Nation") ? entry.getUUID("Nation") : null,
                    entry.hasUUID("Source") ? entry.getUUID("Source") : null,
                    Math.max(0L, entry.getLong("Amount")), state,
                    Math.max(0L, entry.getLong("OpenTick")), state == State.REVIEW_REQUIRED
                    ? "interrupted_transaction" : entry.getString("Detail"));
            data.transactions.put(value.id(), value);
        }
        return data;
    }

    public static RecoveryFundSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(RecoveryFundSavedData::new, RecoveryFundSavedData::load, null),
                "moveearth_recovery_fund");
    }

    public enum Type {
        ADMIN_MINT, NATION_TRANSFER, AID, UPKEEP_SUBSIDY, DISPATCH_SUBSIDY, DISPATCH_EMPLOYER;
        public boolean aid() { return this == AID || this == UPKEEP_SUBSIDY || this == DISPATCH_SUBSIDY; }
    }
    public enum State { PREPARED, WITHDRAWN, RESERVED, PAID, REFUNDED, REVIEW_REQUIRED }
    public record Transaction(UUID id, Type type, UUID nationId, UUID sourceId, long amount,
                              State state, long openTick, String detail) {
        Transaction withState(State next) { return new Transaction(id, type, nationId, sourceId, amount, next, openTick, detail); }
        Transaction withAmount(long next) { return new Transaction(id, type, nationId, sourceId, Math.max(0L, next), state, openTick, detail); }
    }
}
