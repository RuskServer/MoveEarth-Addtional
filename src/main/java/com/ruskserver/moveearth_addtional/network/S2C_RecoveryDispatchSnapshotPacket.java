package com.ruskserver.moveearth_addtional.network;

import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public record S2C_RecoveryDispatchSnapshotPacket(boolean openScreen, long openTick, UUID ownNationId, boolean member,
        boolean admin, boolean canManageSiege, boolean canManageTreasury, boolean canManageDispatch,
        boolean canManageDiplomacy, long fundBalance, long fundReserved, RecoveryView recovery,
        List<ContractView> contracts, List<NationOption> nations, List<CoreOption> cores,
        List<HistoryView> history, List<FundReview> fundReviews) implements CustomPacketPayload {
    private static final int MAX_ROWS = 256;
    public static final Type<S2C_RecoveryDispatchSnapshotPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Moveearth_addtional.MODID, "recovery_dispatch_snapshot"));
    public static final StreamCodec<FriendlyByteBuf, S2C_RecoveryDispatchSnapshotPacket> STREAM_CODEC =
            StreamCodec.of(S2C_RecoveryDispatchSnapshotPacket::write, S2C_RecoveryDispatchSnapshotPacket::read);

    public S2C_RecoveryDispatchSnapshotPacket {
        contracts = contracts == null ? List.of() : List.copyOf(contracts);
        nations = nations == null ? List.of() : List.copyOf(nations);
        cores = cores == null ? List.of() : List.copyOf(cores);
        history = history == null ? List.of() : List.copyOf(history);
        fundReviews = fundReviews == null ? List.of() : List.copyOf(fundReviews);
    }

    private static void write(FriendlyByteBuf buffer, S2C_RecoveryDispatchSnapshotPacket value) {
        buffer.writeBoolean(value.openScreen); buffer.writeVarLong(value.openTick); writeUuid(buffer, value.ownNationId);
        buffer.writeBoolean(value.member); buffer.writeBoolean(value.admin);
        buffer.writeBoolean(value.canManageSiege); buffer.writeBoolean(value.canManageTreasury);
        buffer.writeBoolean(value.canManageDispatch); buffer.writeBoolean(value.canManageDiplomacy);
        buffer.writeVarLong(value.fundBalance); buffer.writeVarLong(value.fundReserved);
        buffer.writeBoolean(value.recovery != null); if (value.recovery != null) value.recovery.write(buffer);
        writeList(buffer, value.contracts, (target, row) -> row.write(target));
        writeList(buffer, value.nations, (target, row) -> row.write(target));
        writeList(buffer, value.cores, (target, row) -> row.write(target));
        writeList(buffer, value.history, (target, row) -> row.write(target));
        writeList(buffer, value.fundReviews, (target, row) -> row.write(target));
    }

    private static S2C_RecoveryDispatchSnapshotPacket read(FriendlyByteBuf buffer) {
        boolean open = buffer.readBoolean(); long tick = buffer.readVarLong(); UUID ownNation = readUuid(buffer);
        boolean member = buffer.readBoolean(); boolean admin = buffer.readBoolean();
        boolean siege = buffer.readBoolean(); boolean treasury = buffer.readBoolean();
        boolean dispatch = buffer.readBoolean(); boolean diplomacy = buffer.readBoolean();
        long balance = buffer.readVarLong(); long reserved = buffer.readVarLong();
        RecoveryView recovery = buffer.readBoolean() ? RecoveryView.read(buffer) : null;
        return new S2C_RecoveryDispatchSnapshotPacket(open, tick, ownNation, member, admin, siege, treasury,
                dispatch, diplomacy, balance, reserved, recovery, readList(buffer, ContractView::read),
                readList(buffer, NationOption::read), readList(buffer, CoreOption::read),
                readList(buffer, HistoryView::read), readList(buffer, FundReview::read));
    }

    private static <T> void writeList(FriendlyByteBuf buffer, List<T> values, Writer<T> writer) {
        int size = Math.min(MAX_ROWS, values.size()); buffer.writeVarInt(size);
        for (int i = 0; i < size; i++) writer.write(buffer, values.get(i));
    }
    private static <T> List<T> readList(FriendlyByteBuf buffer, Reader<T> reader) {
        int size = buffer.readVarInt();
        if (size < 0 || size > MAX_ROWS) throw new IllegalArgumentException("Recovery snapshot list is too large");
        List<T> result = new ArrayList<>(size); for (int i = 0; i < size; i++) result.add(reader.read(buffer));
        return List.copyOf(result);
    }
    private static void writeUuid(FriendlyByteBuf buffer, UUID id) { buffer.writeBoolean(id != null); if (id != null) buffer.writeUUID(id); }
    private static UUID readUuid(FriendlyByteBuf buffer) { return buffer.readBoolean() ? buffer.readUUID() : null; }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    public void handle(net.neoforged.neoforge.network.handling.IPayloadContext context) {
        context.enqueueWork(() -> com.ruskserver.moveearth_addtional.client.ClientPacketHandler.handleRecoveryDispatch(this));
    }

    public record RecoveryView(UUID id, long revision, String state, long remainingTicks,
                               int supportPercent, boolean resealed, int healthyWalls, int wallTarget,
                               boolean upkeepPaid, long aidUsed, long aidReserved,
                               UUID attackerNationId, String attackerName,
                               String rivalName, boolean protectionWaived) {
        void write(FriendlyByteBuf b) { b.writeUUID(id); b.writeVarLong(revision); b.writeUtf(state, 24);
            b.writeVarLong(remainingTicks); b.writeVarInt(supportPercent); b.writeBoolean(resealed);
            b.writeVarInt(healthyWalls); b.writeVarInt(wallTarget); b.writeBoolean(upkeepPaid);
            b.writeVarLong(aidUsed); b.writeVarLong(aidReserved); writeUuid(b, attackerNationId);
            b.writeUtf(attackerName == null ? "" : attackerName, 64); b.writeUtf(rivalName == null ? "" : rivalName, 64);
            b.writeBoolean(protectionWaived); }
        static RecoveryView read(FriendlyByteBuf b) { return new RecoveryView(b.readUUID(), b.readVarLong(),
                b.readUtf(24), b.readVarLong(), b.readVarInt(), b.readBoolean(), b.readVarInt(), b.readVarInt(),
                b.readBoolean(), b.readVarLong(), b.readVarLong(), readUuid(b), b.readUtf(64),
                b.readUtf(64), b.readBoolean()); }
    }
    public record ContractView(UUID id, long revision, String state, String employer, String provider,
                               String side, String target, List<String> participants, int consentCount,
                               long pricePerMinute, long maximumTicks, long billedTicks,
                               long requestedSubsidy, long ownEscrow, long subsidyEscrow, boolean employerApproved,
                               boolean providerApproved, boolean subsidyApproved, boolean viewerParticipant,
                               boolean viewerConsented, String endReason) {
        void write(FriendlyByteBuf b) { b.writeUUID(id); b.writeVarLong(revision); b.writeUtf(state, 32);
            b.writeUtf(employer, 64); b.writeUtf(provider, 64); b.writeUtf(side, 16); b.writeUtf(target, 96);
            writeList(b, participants, (buf, text) -> buf.writeUtf(text, 64)); b.writeVarInt(consentCount);
            b.writeVarLong(pricePerMinute); b.writeVarLong(maximumTicks); b.writeVarLong(billedTicks);
            b.writeVarLong(requestedSubsidy); b.writeVarLong(ownEscrow); b.writeVarLong(subsidyEscrow); b.writeBoolean(employerApproved);
            b.writeBoolean(providerApproved); b.writeBoolean(subsidyApproved); b.writeBoolean(viewerParticipant);
            b.writeBoolean(viewerConsented); b.writeUtf(endReason == null ? "" : endReason, 64); }
        static ContractView read(FriendlyByteBuf b) { return new ContractView(b.readUUID(), b.readVarLong(),
                b.readUtf(32), b.readUtf(64), b.readUtf(64), b.readUtf(16), b.readUtf(96),
                readList(b, value -> value.readUtf(64)), b.readVarInt(), b.readVarLong(), b.readVarLong(),
                b.readVarLong(), b.readVarLong(), b.readVarLong(), b.readVarLong(), b.readBoolean(), b.readBoolean(),
                b.readBoolean(), b.readBoolean(), b.readBoolean(), b.readUtf(64)); }
    }
    public record NationOption(UUID id, String name, String tag, List<MemberOption> members) {
        void write(FriendlyByteBuf b) { b.writeUUID(id); b.writeUtf(name, 64); b.writeUtf(tag, 16);
            writeList(b, members, (target, row) -> row.write(target)); }
        static NationOption read(FriendlyByteBuf b) { return new NationOption(b.readUUID(), b.readUtf(64),
                b.readUtf(16), readList(b, MemberOption::read)); }
    }
    public record MemberOption(UUID id, String name) {
        void write(FriendlyByteBuf b) { b.writeUUID(id); b.writeUtf(name, 32); }
        static MemberOption read(FriendlyByteBuf b) { return new MemberOption(b.readUUID(), b.readUtf(32)); }
    }
    public record CoreOption(UUID id, UUID nationId, String owner, String type, String dimension,
                             int x, int y, int z) {
        void write(FriendlyByteBuf b) { b.writeUUID(id); b.writeUUID(nationId); b.writeUtf(owner, 64);
            b.writeUtf(type, 16); b.writeUtf(dimension, 128); b.writeInt(x); b.writeInt(y); b.writeInt(z); }
        static CoreOption read(FriendlyByteBuf b) { return new CoreOption(b.readUUID(), b.readUUID(), b.readUtf(64),
                b.readUtf(16), b.readUtf(128), b.readInt(), b.readInt(), b.readInt()); }
    }
    public record HistoryView(long openTick, String type, String primary, String secondary, List<String> details) {
        void write(FriendlyByteBuf b) { b.writeVarLong(openTick); b.writeUtf(type, 32); b.writeUtf(primary, 64);
            b.writeUtf(secondary, 64); writeList(b, details, (buf, text) -> buf.writeUtf(text, 128)); }
        static HistoryView read(FriendlyByteBuf b) { return new HistoryView(b.readVarLong(), b.readUtf(32),
                b.readUtf(64), b.readUtf(64), readList(b, value -> value.readUtf(128))); }
    }
    public record FundReview(UUID id, String type, String nation, long amount, String detail) {
        void write(FriendlyByteBuf b) { b.writeUUID(id); b.writeUtf(type, 32); b.writeUtf(nation, 64);
            b.writeVarLong(amount); b.writeUtf(detail, 128); }
        static FundReview read(FriendlyByteBuf b) { return new FundReview(b.readUUID(), b.readUtf(32),
                b.readUtf(64), b.readVarLong(), b.readUtf(128)); }
    }
    private interface Writer<T> { void write(FriendlyByteBuf buffer, T value); }
    private interface Reader<T> { T read(FriendlyByteBuf buffer); }
}
