package com.ruskserver.moveearth_addtional.network;

import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public record S2C_BalanceSnapshotPacket(boolean openScreen, long balance, List<HistoryEntry> history,
                                        List<Recipient> recipients, String result, boolean success)
        implements CustomPacketPayload {
    private static final int MAX_HISTORY = 40;
    private static final int MAX_RECIPIENTS = 256;
    public static final Type<S2C_BalanceSnapshotPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Moveearth_addtional.MODID, "balance_snapshot"));
    public static final StreamCodec<FriendlyByteBuf, S2C_BalanceSnapshotPacket> STREAM_CODEC = StreamCodec.of(
            S2C_BalanceSnapshotPacket::write, S2C_BalanceSnapshotPacket::read);

    private static void write(FriendlyByteBuf buffer, S2C_BalanceSnapshotPacket packet) {
        buffer.writeBoolean(packet.openScreen);
        buffer.writeVarLong(packet.balance);
        buffer.writeVarInt(packet.history.size());
        for (HistoryEntry entry : packet.history) {
            buffer.writeLong(entry.occurredAt);
            buffer.writeVarLong(entry.amount);
            buffer.writeBoolean(entry.incoming);
            buffer.writeUtf(entry.reason, 80);
            buffer.writeUtf(entry.counterparty, 48);
        }
        buffer.writeVarInt(packet.recipients.size());
        for (Recipient recipient : packet.recipients) {
            buffer.writeUUID(recipient.id);
            buffer.writeUtf(recipient.name, 32);
        }
        buffer.writeUtf(packet.result, 120);
        buffer.writeBoolean(packet.success);
    }

    private static S2C_BalanceSnapshotPacket read(FriendlyByteBuf buffer) {
        boolean openScreen = buffer.readBoolean();
        long balance = buffer.readVarLong();
        int historyCount = count(buffer.readVarInt(), MAX_HISTORY);
        List<HistoryEntry> history = new ArrayList<>(historyCount);
        for (int i = 0; i < historyCount; i++) history.add(new HistoryEntry(buffer.readLong(),
                buffer.readVarLong(), buffer.readBoolean(), buffer.readUtf(80), buffer.readUtf(48)));
        int recipientCount = count(buffer.readVarInt(), MAX_RECIPIENTS);
        List<Recipient> recipients = new ArrayList<>(recipientCount);
        for (int i = 0; i < recipientCount; i++)
            recipients.add(new Recipient(buffer.readUUID(), buffer.readUtf(32)));
        return new S2C_BalanceSnapshotPacket(openScreen, balance, List.copyOf(history),
                List.copyOf(recipients), buffer.readUtf(120), buffer.readBoolean());
    }

    private static int count(int count, int max) {
        if (count < 0 || count > max) throw new IllegalArgumentException("Balance snapshot is too large");
        return count;
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    public void handle(net.neoforged.neoforge.network.handling.IPayloadContext context) {
        context.enqueueWork(() -> com.ruskserver.moveearth_addtional.client.ClientPacketHandler.handleBalance(this));
    }

    public record HistoryEntry(long occurredAt, long amount, boolean incoming, String reason, String counterparty) { }
    public record Recipient(UUID id, String name) { }
}
