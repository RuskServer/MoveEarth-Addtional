package com.ruskserver.moveearth_addtional.s2.dispatch;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class DispatchContractSavedData extends SavedData {
    private final Map<UUID, Contract> contracts = new LinkedHashMap<>();

    public Contract create(UUID employer, UUID provider, UUID targetCore, UUID opponent, Side side,
                           Set<UUID> participants, long pricePerOpenMinute, long maximumOpenTicks,
                           long requestedSubsidy, long createdAt) {
        Contract value = new Contract(UUID.randomUUID(), 1L, employer, provider, targetCore, opponent, side,
                Set.copyOf(participants), Set.of(), false, false, false,
                Math.max(0L, pricePerOpenMinute), Math.max(1L, maximumOpenTicks),
                Math.max(0L, requestedSubsidy), 0L, 0L, null, null, null,
                State.APPROVAL_PENDING, Math.max(0L, createdAt), 0L, 0L, Map.of(), "");
        contracts.put(value.id(), value);
        setDirty();
        return value;
    }

    public Optional<Contract> byId(UUID id) { return Optional.ofNullable(contracts.get(id)); }
    public List<Contract> all() { return List.copyOf(contracts.values()); }
    public List<Contract> forNation(UUID nationId) {
        return contracts.values().stream().filter(value -> nationId.equals(value.employerNation())
                || nationId.equals(value.providerNation())).sorted(Comparator.comparingLong(Contract::createdAt).reversed()).toList();
    }

    public Optional<Contract> activeForPlayer(UUID playerId) {
        return contracts.values().stream().filter(value -> value.participants().contains(playerId)
                && value.state() == State.ACTIVE).findFirst();
    }

    public Optional<Contract> reservedForPlayer(UUID playerId) {
        return contracts.values().stream().filter(value -> value.participants().contains(playerId)
                && value.state() != State.COMPLETED && value.state() != State.CANCELLED
                && value.state() != State.REVIEW_REQUIRED).findFirst();
    }

    public Contract approve(UUID id, boolean employerApproval, boolean providerApproval, boolean subsidyApproval,
                            long expectedRevision) {
        Contract before = contracts.get(id);
        if (before == null || before.revision() != expectedRevision || before.state() != State.APPROVAL_PENDING
                && before.state() != State.CONSENT_PENDING) return null;
        boolean employer = before.employerApproved() || employerApproval;
        boolean provider = before.providerApproved() || providerApproval;
        boolean subsidy = before.subsidyApproved() || subsidyApproval;
        State state = employer && provider ? State.CONSENT_PENDING : State.APPROVAL_PENDING;
        Contract after = before.withApprovals(employer, provider, subsidy, state);
        replace(after);
        return after;
    }

    public Contract consent(UUID id, UUID playerId, boolean accepted, long expectedRevision) {
        Contract before = contracts.get(id);
        if (before == null || before.revision() != expectedRevision || !before.participants().contains(playerId)
                || before.state() != State.CONSENT_PENDING && before.state() != State.APPROVAL_PENDING) return null;
        Set<UUID> consents = new LinkedHashSet<>(before.consents());
        if (accepted) consents.add(playerId); else consents.remove(playerId);
        Contract after = before.withConsents(Set.copyOf(consents));
        replace(after);
        return after;
    }

    public Contract beginFunding(UUID id, long expectedRevision) {
        Contract before = contracts.get(id);
        if (before == null || before.revision() != expectedRevision || before.state() != State.CONSENT_PENDING
                || !DispatchContractPolicy.mayFund(before.employerApproved(), before.providerApproved(),
                before.participants().size(), before.consents().size())) return null;
        Contract after = before.withState(State.FUNDING, "");
        replace(after);
        return after;
    }

    public Contract funded(UUID id, long ownEscrow, long subsidyEscrow, UUID employerTransaction,
                           UUID fundTransaction) {
        Contract before = contracts.get(id);
        if (before == null || before.state() != State.FUNDING) return null;
        Contract after = before.withFunding(ownEscrow, subsidyEscrow, employerTransaction,
                fundTransaction, State.FUNDED);
        replace(after);
        return after;
    }

    public Contract fundingRejected(UUID id, String reason) {
        Contract before = contracts.get(id);
        if (before == null || before.state() != State.FUNDING) return null;
        Contract after = before.withState(State.CONSENT_PENDING, reason);
        replace(after);
        return after;
    }

    public Contract bind(UUID id, UUID siegeId, long openTick) {
        Contract before = contracts.get(id);
        if (before == null || before.state() != State.FUNDED || before.siegeId() != null) return null;
        Contract after = before.withBinding(siegeId, openTick);
        replace(after);
        return after;
    }

    public Contract bill(UUID id, Map<UUID, Long> additions, long now) {
        Contract before = contracts.get(id);
        if (before == null || before.state() != State.ACTIVE) return null;
        Map<UUID, Long> billed = new LinkedHashMap<>(before.billedTicks());
        for (var addition : additions.entrySet()) {
            long current = billed.getOrDefault(addition.getKey(), 0L);
            billed.put(addition.getKey(), Math.min(before.maximumOpenTicks(), safeAdd(current, addition.getValue())));
        }
        Contract after = before.withBilling(Map.copyOf(billed), now);
        replace(after);
        return after;
    }

    public Contract settling(UUID id, String reason) {
        Contract before = contracts.get(id);
        if (before == null || before.state() != State.ACTIVE && before.state() != State.FUNDED
                && before.state() != State.FUNDING) return null;
        Contract after = before.withState(State.SETTLING, reason);
        replace(after);
        return after;
    }

    public Contract finish(UUID id, boolean cancelled, String reason) {
        Contract before = contracts.get(id);
        if (before == null || before.state() != State.SETTLING && before.state() != State.APPROVAL_PENDING
                && before.state() != State.CONSENT_PENDING) return null;
        Contract after = before.withState(cancelled ? State.CANCELLED : State.COMPLETED, reason);
        replace(after);
        return after;
    }

    public Contract review(UUID id, String reason) {
        Contract before = contracts.get(id);
        if (before == null) return null;
        Contract after = before.withState(State.REVIEW_REQUIRED, reason);
        replace(after);
        return after;
    }

    private void replace(Contract value) { contracts.put(value.id(), value); setDirty(); }
    private static long safeAdd(long left, long right) {
        long amount = Math.max(0L, right);
        return left > Long.MAX_VALUE - amount ? Long.MAX_VALUE : left + amount;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt("SchemaVersion", 1);
        ListTag list = new ListTag();
        for (Contract value : contracts.values()) list.add(saveContract(value));
        tag.put("Contracts", list);
        return tag;
    }

    private static CompoundTag saveContract(Contract value) {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("Id", value.id());
        tag.putLong("Revision", value.revision());
        tag.putUUID("Employer", value.employerNation());
        tag.putUUID("Provider", value.providerNation());
        tag.putUUID("TargetCore", value.targetCoreId());
        tag.putUUID("Opponent", value.opponentNation());
        tag.putString("Side", value.side().name());
        putUuids(tag, "Participants", value.participants());
        putUuids(tag, "Consents", value.consents());
        tag.putBoolean("EmployerApproved", value.employerApproved());
        tag.putBoolean("ProviderApproved", value.providerApproved());
        tag.putBoolean("SubsidyApproved", value.subsidyApproved());
        tag.putLong("Price", value.pricePerOpenMinute());
        tag.putLong("MaximumTicks", value.maximumOpenTicks());
        tag.putLong("RequestedSubsidy", value.requestedSubsidy());
        tag.putLong("OwnEscrow", value.ownEscrow());
        tag.putLong("SubsidyEscrow", value.subsidyEscrow());
        if (value.employerTransactionId() != null) tag.putUUID("EmployerTransaction", value.employerTransactionId());
        if (value.fundTransactionId() != null) tag.putUUID("FundTransaction", value.fundTransactionId());
        if (value.siegeId() != null) tag.putUUID("Siege", value.siegeId());
        tag.putString("State", value.state().name());
        tag.putLong("CreatedAt", value.createdAt());
        tag.putLong("ActivatedAt", value.activatedAt());
        tag.putLong("LastAccountedAt", value.lastAccountedAt());
        ListTag billed = new ListTag();
        for (var entry : value.billedTicks().entrySet()) {
            CompoundTag row = new CompoundTag();
            row.putUUID("Player", entry.getKey());
            row.putLong("Ticks", entry.getValue());
            billed.add(row);
        }
        tag.put("Billed", billed);
        tag.putString("EndReason", value.endReason());
        return tag;
    }

    private static void putUuids(CompoundTag tag, String name, Set<UUID> values) {
        ListTag list = new ListTag();
        for (UUID id : values) { CompoundTag row = new CompoundTag(); row.putUUID("Id", id); list.add(row); }
        tag.put(name, list);
    }

    private static Set<UUID> readUuids(CompoundTag tag, String name) {
        Set<UUID> result = new LinkedHashSet<>();
        ListTag list = tag.getList(name, Tag.TAG_COMPOUND);
        for (int index = 0; index < list.size(); index++) if (list.getCompound(index).hasUUID("Id")) result.add(list.getCompound(index).getUUID("Id"));
        return Set.copyOf(result);
    }

    public static DispatchContractSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        DispatchContractSavedData data = new DispatchContractSavedData();
        ListTag list = tag.getList("Contracts", Tag.TAG_COMPOUND);
        for (int index = 0; index < list.size(); index++) {
            CompoundTag value = list.getCompound(index);
            if (!value.hasUUID("Id") || !value.hasUUID("Employer") || !value.hasUUID("Provider")
                    || !value.hasUUID("TargetCore") || !value.hasUUID("Opponent")) continue;
            Side side;
            State state;
            try { side = Side.valueOf(value.getString("Side")); } catch (IllegalArgumentException ignored) { continue; }
            try { state = State.valueOf(value.getString("State")); } catch (IllegalArgumentException ignored) { state = State.REVIEW_REQUIRED; }
            Map<UUID, Long> billed = new LinkedHashMap<>();
            ListTag billedTag = value.getList("Billed", Tag.TAG_COMPOUND);
            for (int row = 0; row < billedTag.size(); row++) {
                CompoundTag entry = billedTag.getCompound(row);
                if (entry.hasUUID("Player")) billed.put(entry.getUUID("Player"), Math.max(0L, entry.getLong("Ticks")));
            }
            Contract contract = new Contract(value.getUUID("Id"), Math.max(1L, value.getLong("Revision")),
                    value.getUUID("Employer"), value.getUUID("Provider"), value.getUUID("TargetCore"),
                    value.getUUID("Opponent"), side, readUuids(value, "Participants"), readUuids(value, "Consents"),
                    value.getBoolean("EmployerApproved"), value.getBoolean("ProviderApproved"),
                    value.getBoolean("SubsidyApproved"), Math.max(0L, value.getLong("Price")),
                    Math.max(1L, value.getLong("MaximumTicks")), Math.max(0L, value.getLong("RequestedSubsidy")),
                    Math.max(0L, value.getLong("OwnEscrow")), Math.max(0L, value.getLong("SubsidyEscrow")),
                    value.hasUUID("EmployerTransaction") ? value.getUUID("EmployerTransaction") : null,
                    value.hasUUID("FundTransaction") ? value.getUUID("FundTransaction") : null,
                    value.hasUUID("Siege") ? value.getUUID("Siege") : null, state,
                    Math.max(0L, value.getLong("CreatedAt")), Math.max(0L, value.getLong("ActivatedAt")),
                    Math.max(0L, value.getLong("LastAccountedAt")), Map.copyOf(billed), value.getString("EndReason"));
            data.contracts.put(contract.id(), contract);
        }
        return data;
    }

    public static DispatchContractSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(DispatchContractSavedData::new, DispatchContractSavedData::load, null),
                "moveearth_dispatch_contracts");
    }

    public enum Side { OFFENSE, DEFENSE }
    public enum State { APPROVAL_PENDING, CONSENT_PENDING, FUNDING, FUNDED, ACTIVE, SETTLING, COMPLETED, CANCELLED, REVIEW_REQUIRED }
    public record Contract(UUID id, long revision, UUID employerNation, UUID providerNation,
                           UUID targetCoreId, UUID opponentNation, Side side, Set<UUID> participants,
                           Set<UUID> consents, boolean employerApproved, boolean providerApproved,
                           boolean subsidyApproved, long pricePerOpenMinute, long maximumOpenTicks,
                           long requestedSubsidy, long ownEscrow, long subsidyEscrow,
                           UUID employerTransactionId, UUID fundTransactionId, UUID siegeId,
                           State state, long createdAt, long activatedAt, long lastAccountedAt,
                           Map<UUID, Long> billedTicks, String endReason) {
        Contract withApprovals(boolean employer, boolean provider, boolean subsidy, State next) {
            return copy(revision + 1L, consents, employer, provider, subsidy, ownEscrow, subsidyEscrow,
                    employerTransactionId, fundTransactionId, siegeId, next, activatedAt, lastAccountedAt,
                    billedTicks, endReason);
        }
        Contract withConsents(Set<UUID> nextConsents) {
            return copy(revision + 1L, nextConsents, employerApproved, providerApproved, subsidyApproved,
                    ownEscrow, subsidyEscrow, employerTransactionId, fundTransactionId, siegeId, state,
                    activatedAt, lastAccountedAt, billedTicks, endReason);
        }
        Contract withFunding(long own, long subsidy, UUID employerTx, UUID fundTx, State next) {
            return copy(revision + 1L, consents, employerApproved, providerApproved, subsidyApproved,
                    own, subsidy, employerTx, fundTx, siegeId, next, activatedAt, lastAccountedAt,
                    billedTicks, endReason);
        }
        Contract withBinding(UUID siege, long openTick) {
            return copy(revision + 1L, consents, employerApproved, providerApproved, subsidyApproved,
                    ownEscrow, subsidyEscrow, employerTransactionId, fundTransactionId, siege,
                    State.ACTIVE, openTick, openTick, billedTicks, "");
        }
        Contract withBilling(Map<UUID, Long> billed, long now) {
            return copy(revision + 1L, consents, employerApproved, providerApproved, subsidyApproved,
                    ownEscrow, subsidyEscrow, employerTransactionId, fundTransactionId, siegeId,
                    state, activatedAt, now, billed, endReason);
        }
        Contract withState(State next, String reason) {
            return copy(revision + 1L, consents, employerApproved, providerApproved, subsidyApproved,
                    ownEscrow, subsidyEscrow, employerTransactionId, fundTransactionId, siegeId,
                    next, activatedAt, lastAccountedAt, billedTicks, reason == null ? "" : reason);
        }
        private Contract copy(long nextRevision, Set<UUID> nextConsents, boolean employer, boolean provider,
                              boolean subsidyApproved, long own, long subsidy, UUID employerTx, UUID fundTx,
                              UUID siege, State nextState, long activated, long accounted,
                              Map<UUID, Long> billed, String reason) {
            return new Contract(id, nextRevision, employerNation, providerNation, targetCoreId, opponentNation,
                    side, participants, nextConsents, employer, provider, subsidyApproved,
                    pricePerOpenMinute, maximumOpenTicks, requestedSubsidy, own, subsidy,
                    employerTx, fundTx, siege, nextState, createdAt, activated, accounted, billed, reason);
        }
    }
}
