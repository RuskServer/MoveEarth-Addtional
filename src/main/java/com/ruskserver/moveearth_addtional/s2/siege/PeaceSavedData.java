package com.ruskserver.moveearth_addtional.s2.siege;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Persistent bilateral peace proposals. */
public final class PeaceSavedData extends SavedData {
    private final Map<UUID, Proposal> proposals = new LinkedHashMap<>();

    public ProposalResult propose(UUID proposer, UUID receiver, long gold, long durationTicks) {
        if (proposer == null || receiver == null || proposer.equals(receiver)
                || !PeaceTermsPolicy.validCompensation(gold)) return new ProposalResult(Status.INVALID, null);
        proposals.values().removeIf(value -> samePair(value.proposerNation, value.receiverNation,
                proposer, receiver));
        Proposal proposal = new Proposal(UUID.randomUUID(), proposer, receiver, gold,
                Math.max(1L, durationTicks), true);
        proposals.put(proposal.id, proposal);
        setDirty();
        return new ProposalResult(Status.PROPOSED, proposal);
    }

    public Optional<Proposal> proposal(UUID id) { return Optional.ofNullable(proposals.get(id)); }

    public List<Proposal> forNation(UUID nationId) {
        return proposals.values().stream()
                .filter(value -> value.proposerNation.equals(nationId)
                        || value.receiverNation.equals(nationId))
                .sorted(Comparator.comparingLong(Proposal::remainingTicks)).toList();
    }

    public boolean remove(UUID id) {
        if (proposals.remove(id) == null) return false;
        setDirty();
        return true;
    }

    public void removeBetween(UUID first, UUID second) {
        if (proposals.values().removeIf(value -> samePair(
                value.proposerNation, value.receiverNation, first, second))) setDirty();
    }

    public void advance(long elapsedTicks) {
        if (elapsedTicks <= 0L || proposals.isEmpty()) return;
        var iterator = proposals.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            long remaining = Math.max(0L, entry.getValue().remainingTicks - elapsedTicks);
            if (remaining == 0L) iterator.remove();
            else entry.setValue(entry.getValue().withRemaining(remaining));
        }
        setDirty();
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (Proposal proposal : proposals.values()) {
            CompoundTag value = new CompoundTag();
            value.putUUID("Id", proposal.id);
            value.putUUID("Proposer", proposal.proposerNation);
            value.putUUID("Receiver", proposal.receiverNation);
            value.putLong("Gold", proposal.goldCompensation);
            value.putLong("Remaining", proposal.remainingTicks);
            value.putBoolean("ReturnPrisoners", proposal.returnPrisoners);
            list.add(value);
        }
        tag.put("Proposals", list);
        return tag;
    }

    public static PeaceSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        PeaceSavedData data = new PeaceSavedData();
        ListTag list = tag.getList("Proposals", Tag.TAG_COMPOUND);
        for (int index = 0; index < list.size(); index++) {
            CompoundTag value = list.getCompound(index);
            long gold = value.getLong("Gold");
            long remaining = value.getLong("Remaining");
            if (!PeaceTermsPolicy.validCompensation(gold) || remaining <= 0L) continue;
            try {
                Proposal proposal = new Proposal(value.getUUID("Id"), value.getUUID("Proposer"),
                        value.getUUID("Receiver"), gold, remaining,
                        value.getBoolean("ReturnPrisoners"));
                data.proposals.put(proposal.id, proposal);
            } catch (IllegalArgumentException ignored) { }
        }
        return data;
    }

    public static PeaceSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(PeaceSavedData::new, PeaceSavedData::load, null),
                "moveearth_peace_proposals");
    }

    private static boolean samePair(UUID leftA, UUID leftB, UUID rightA, UUID rightB) {
        return leftA.equals(rightA) && leftB.equals(rightB)
                || leftA.equals(rightB) && leftB.equals(rightA);
    }

    public enum Status { PROPOSED, INVALID }
    public record ProposalResult(Status status, Proposal proposal) { }
    public record Proposal(UUID id, UUID proposerNation, UUID receiverNation,
                           long goldCompensation, long remainingTicks, boolean returnPrisoners) {
        public Proposal withRemaining(long ticks) {
            return new Proposal(id, proposerNation, receiverNation, goldCompensation,
                    Math.max(1L, ticks), returnPrisoners);
        }
    }
}
