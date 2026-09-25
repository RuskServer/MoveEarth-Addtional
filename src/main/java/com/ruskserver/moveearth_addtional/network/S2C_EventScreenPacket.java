package com.ruskserver.moveearth_addtional.network;

import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

public record S2C_EventScreenPacket(boolean open, boolean hasEvent, boolean active, String title, String target,
                                    int remainingMinutes, int nextMinutes, int score, int rank,
                                    boolean jobBonus, int paidCurrency, List<Leader> leaders,
                                    List<ClaimItem> claims, String result) implements CustomPacketPayload {
    public static final Type<S2C_EventScreenPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Moveearth_addtional.MODID, "event_screen"));
    public static final StreamCodec<FriendlyByteBuf, S2C_EventScreenPacket> STREAM_CODEC = StreamCodec.of(
            S2C_EventScreenPacket::encode, S2C_EventScreenPacket::decode);

    private static void encode(FriendlyByteBuf buffer, S2C_EventScreenPacket packet) {
        buffer.writeBoolean(packet.open);
        buffer.writeBoolean(packet.hasEvent);
        buffer.writeBoolean(packet.active);
        buffer.writeUtf(packet.title, 32);
        buffer.writeUtf(packet.target, 64);
        buffer.writeVarInt(packet.remainingMinutes);
        buffer.writeVarInt(packet.nextMinutes);
        buffer.writeVarInt(packet.score);
        buffer.writeVarInt(packet.rank);
        buffer.writeBoolean(packet.jobBonus);
        buffer.writeVarInt(packet.paidCurrency);
        buffer.writeVarInt(packet.leaders.size());
        for (Leader leader : packet.leaders) {
            buffer.writeUtf(leader.name, 32);
            buffer.writeVarInt(leader.score);
        }
        buffer.writeVarInt(packet.claims.size());
        for (ClaimItem claim : packet.claims) {
            buffer.writeResourceLocation(claim.itemId);
            buffer.writeVarInt(claim.count);
        }
        buffer.writeUtf(packet.result, 120);
    }

    private static S2C_EventScreenPacket decode(FriendlyByteBuf buffer) {
        boolean open = buffer.readBoolean();
        boolean hasEvent = buffer.readBoolean();
        boolean active = buffer.readBoolean();
        String title = buffer.readUtf(32);
        String target = buffer.readUtf(64);
        int remainingMinutes = buffer.readVarInt();
        int nextMinutes = buffer.readVarInt();
        int score = buffer.readVarInt();
        int rank = buffer.readVarInt();
        boolean jobBonus = buffer.readBoolean();
        int paidCurrency = buffer.readVarInt();
        int leaderCount = checkedCount(buffer, 5);
        List<Leader> leaders = new ArrayList<>(leaderCount);
        for (int index = 0; index < leaderCount; index++)
            leaders.add(new Leader(buffer.readUtf(32), buffer.readVarInt()));
        int claimCount = checkedCount(buffer, 16);
        List<ClaimItem> claims = new ArrayList<>(claimCount);
        for (int index = 0; index < claimCount; index++)
            claims.add(new ClaimItem(buffer.readResourceLocation(), buffer.readVarInt()));
        return new S2C_EventScreenPacket(open, hasEvent, active, title, target, remainingMinutes,
                nextMinutes, score, rank, jobBonus, paidCurrency, List.copyOf(leaders),
                List.copyOf(claims), buffer.readUtf(120));
    }

    private static int checkedCount(FriendlyByteBuf buffer, int max) {
        int count = buffer.readVarInt();
        if (count < 0 || count > max) throw new IllegalArgumentException("Invalid event screen list size");
        return count;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public void handle(net.neoforged.neoforge.network.handling.IPayloadContext context) {
        context.enqueueWork(() -> com.ruskserver.moveearth_addtional.client.EventScreen.receive(this));
    }

    public record Leader(String name, int score) { }
    public record ClaimItem(ResourceLocation itemId, int count) { }
}
