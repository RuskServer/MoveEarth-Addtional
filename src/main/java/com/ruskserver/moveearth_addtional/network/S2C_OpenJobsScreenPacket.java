package com.ruskserver.moveearth_addtional.network;

import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

public record S2C_OpenJobsScreenPacket(
        String subjectName,
        boolean selfView,
        boolean canAdmin,
        int points,
        int maxActiveJobs,
        List<JobEntry> jobs,
        List<String> onlinePlayers) implements CustomPacketPayload {
    private static final int MAX_JOBS = 64;
    private static final int MAX_PLAYERS = 256;
    public static final Type<S2C_OpenJobsScreenPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Moveearth_addtional.MODID, "jobs_screen"));
    public static final StreamCodec<FriendlyByteBuf, S2C_OpenJobsScreenPacket> STREAM_CODEC = StreamCodec.of(
            S2C_OpenJobsScreenPacket::encode, S2C_OpenJobsScreenPacket::decode);

    private static void encode(FriendlyByteBuf buffer, S2C_OpenJobsScreenPacket packet) {
        buffer.writeUtf(packet.subjectName, 16);
        buffer.writeBoolean(packet.selfView);
        buffer.writeBoolean(packet.canAdmin);
        buffer.writeVarInt(packet.points);
        buffer.writeVarInt(packet.maxActiveJobs);
        buffer.writeVarInt(packet.jobs.size());
        for (JobEntry job : packet.jobs) {
            buffer.writeResourceLocation(job.id);
            buffer.writeUtf(job.displayName, 64);
            buffer.writeVarInt(job.maxLevel);
            buffer.writeVarInt(job.pointsPerLevel);
            buffer.writeBoolean(job.active);
            buffer.writeVarInt(job.level);
            buffer.writeVarLong(job.xpInLevel);
            buffer.writeVarLong(job.xpForNextLevel);
            buffer.writeVarLong(job.totalXp);
        }
        buffer.writeVarInt(packet.onlinePlayers.size());
        for (String playerName : packet.onlinePlayers) {
            buffer.writeUtf(playerName, 16);
        }
    }

    private static S2C_OpenJobsScreenPacket decode(FriendlyByteBuf buffer) {
        String subjectName = buffer.readUtf(16);
        boolean selfView = buffer.readBoolean();
        boolean canAdmin = buffer.readBoolean();
        int points = buffer.readVarInt();
        int maxActiveJobs = buffer.readVarInt();
        int jobCount = checkedSize(buffer.readVarInt(), MAX_JOBS, "job");
        List<JobEntry> jobs = new ArrayList<>(jobCount);
        for (int i = 0; i < jobCount; i++) {
            jobs.add(new JobEntry(
                    buffer.readResourceLocation(),
                    buffer.readUtf(64),
                    buffer.readVarInt(),
                    buffer.readVarInt(),
                    buffer.readBoolean(),
                    buffer.readVarInt(),
                    buffer.readVarLong(),
                    buffer.readVarLong(),
                    buffer.readVarLong()));
        }
        int playerCount = checkedSize(buffer.readVarInt(), MAX_PLAYERS, "player");
        List<String> onlinePlayers = new ArrayList<>(playerCount);
        for (int i = 0; i < playerCount; i++) {
            onlinePlayers.add(buffer.readUtf(16));
        }
        return new S2C_OpenJobsScreenPacket(subjectName, selfView, canAdmin, points, maxActiveJobs,
                List.copyOf(jobs), List.copyOf(onlinePlayers));
    }

    private static int checkedSize(int size, int max, String name) {
        if (size < 0 || size > max) {
            throw new IllegalArgumentException("Invalid " + name + " count: " + size);
        }
        return size;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public void handle(net.neoforged.neoforge.network.handling.IPayloadContext context) {
        context.enqueueWork(() -> com.ruskserver.moveearth_addtional.client.ClientPacketHandler.handleOpenJobs(this));
    }

    public record JobEntry(ResourceLocation id, String displayName, int maxLevel, int pointsPerLevel,
                           boolean active, int level, long xpInLevel, long xpForNextLevel, long totalXp) {
    }
}
