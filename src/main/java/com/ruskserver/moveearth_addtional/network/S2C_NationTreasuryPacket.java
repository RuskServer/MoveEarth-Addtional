package com.ruskserver.moveearth_addtional.network;

import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import com.ruskserver.moveearth_addtional.s2.territory.UpkeepPenalty;
import java.util.List;
import java.util.ArrayList;

public record S2C_NationTreasuryPacket(boolean canManage, long upkeep, int upkeepCycleHours,
                                       long nationBalance, long playerBalance,
                                       long nextDueAt, int failedPayments, long overdueSince,
                                       UpkeepPenalty penalty, List<String> recentTransactions)
        implements CustomPacketPayload {
    public static final Type<S2C_NationTreasuryPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Moveearth_addtional.MODID, "nation_treasury"));
    public static final StreamCodec<FriendlyByteBuf, S2C_NationTreasuryPacket> STREAM_CODEC = StreamCodec.of(
            (buffer, packet) -> {
                buffer.writeBoolean(packet.canManage);
                buffer.writeVarLong(Math.max(0L, packet.upkeep));
                buffer.writeVarInt(packet.upkeepCycleHours);
                buffer.writeVarLong(packet.nationBalance);
                buffer.writeVarLong(packet.playerBalance);
                buffer.writeLong(packet.nextDueAt);
                buffer.writeVarInt(packet.failedPayments);
                buffer.writeLong(packet.overdueSince);
                buffer.writeEnum(packet.penalty);
                buffer.writeVarInt(packet.recentTransactions.size());
                packet.recentTransactions.forEach(value -> buffer.writeUtf(value, 120));
            },
            buffer -> {
                boolean canManage = buffer.readBoolean();
                long upkeep = buffer.readVarLong();
                int upkeepCycleHours = buffer.readVarInt();
                long nationBalance = buffer.readVarLong();
                long playerBalance = buffer.readVarLong();
                long nextDueAt = buffer.readLong();
                int failedPayments = buffer.readVarInt();
                long overdueSince = buffer.readLong();
                UpkeepPenalty penalty = buffer.readEnum(UpkeepPenalty.class);
                int count = buffer.readVarInt();
                if (count < 0 || count > 8) throw new IllegalArgumentException("Invalid treasury history length");
                List<String> history = new ArrayList<>();
                for (int i = 0; i < count; i++) history.add(buffer.readUtf(120));
                return new S2C_NationTreasuryPacket(canManage, upkeep, upkeepCycleHours, nationBalance, playerBalance,
                        nextDueAt, failedPayments, overdueSince, penalty, history);
            });

    public S2C_NationTreasuryPacket {
        if (upkeepCycleHours < 1 || upkeepCycleHours > 720)
            throw new IllegalArgumentException("Invalid upkeep cycle");
        if (penalty == null) penalty = UpkeepPenalty.CURRENT;
        overdueSince = Math.max(0L, overdueSince);
        recentTransactions = List.copyOf(recentTransactions);
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public void handle(net.neoforged.neoforge.network.handling.IPayloadContext context) {
        context.enqueueWork(() -> com.ruskserver.moveearth_addtional.client.ClientPacketHandler.handleNationTreasury(this));
    }
}
