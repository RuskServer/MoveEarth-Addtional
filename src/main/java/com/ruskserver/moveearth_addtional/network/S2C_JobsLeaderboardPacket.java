package com.ruskserver.moveearth_addtional.network;

import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

public record S2C_JobsLeaderboardPacket(ResourceLocation jobId, List<Entry> entries)
        implements CustomPacketPayload {
    public static final int MAX_ENTRIES = 20;
    public static final Type<S2C_JobsLeaderboardPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Moveearth_addtional.MODID, "jobs_leaderboard"));
    public static final StreamCodec<FriendlyByteBuf, S2C_JobsLeaderboardPacket> STREAM_CODEC = StreamCodec.of(
            S2C_JobsLeaderboardPacket::encode, S2C_JobsLeaderboardPacket::decode);

    private static void encode(FriendlyByteBuf buffer, S2C_JobsLeaderboardPacket packet) {
        buffer.writeResourceLocation(packet.jobId);
        int count = Math.min(packet.entries.size(), MAX_ENTRIES);
        buffer.writeVarInt(count);
        for (int i = 0; i < count; i++) {
            Entry entry = packet.entries.get(i);
            buffer.writeUtf(entry.playerName, 16);
            buffer.writeVarInt(entry.level);
            buffer.writeDouble(entry.xpInLevel);
            buffer.writeDouble(entry.totalXp);
        }
    }

    private static S2C_JobsLeaderboardPacket decode(FriendlyByteBuf buffer) {
        ResourceLocation jobId = buffer.readResourceLocation();
        int count = buffer.readVarInt();
        if (count < 0 || count > MAX_ENTRIES) {
            throw new IllegalArgumentException("Invalid jobs leaderboard count: " + count);
        }
        List<Entry> entries = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            entries.add(new Entry(buffer.readUtf(16), buffer.readVarInt(),
                    buffer.readDouble(), buffer.readDouble()));
        }
        return new S2C_JobsLeaderboardPacket(jobId, List.copyOf(entries));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public void handle(net.neoforged.neoforge.network.handling.IPayloadContext context) {
        context.enqueueWork(() ->
                com.ruskserver.moveearth_addtional.client.ClientPacketHandler.handleJobsLeaderboard(this));
    }

    public record Entry(String playerName, int level, double xpInLevel, double totalXp) {
    }
}
