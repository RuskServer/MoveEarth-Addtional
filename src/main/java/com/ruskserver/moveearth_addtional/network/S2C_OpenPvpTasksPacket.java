package com.ruskserver.moveearth_addtional.network;

import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

public record S2C_OpenPvpTasksPacket(int points, List<TaskEntry> tasks) implements CustomPacketPayload {
    private static final int MAX_TASKS = 32;
    public static final Type<S2C_OpenPvpTasksPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Moveearth_addtional.MODID, "pvp_tasks_open"));
    public static final StreamCodec<FriendlyByteBuf, S2C_OpenPvpTasksPacket> STREAM_CODEC = StreamCodec.of(
            S2C_OpenPvpTasksPacket::encode, S2C_OpenPvpTasksPacket::decode);

    private static void encode(FriendlyByteBuf buffer, S2C_OpenPvpTasksPacket packet) {
        buffer.writeVarInt(packet.points);
        buffer.writeVarInt(packet.tasks.size());
        for (TaskEntry task : packet.tasks) {
            buffer.writeUtf(task.id, 64);
            buffer.writeUtf(task.category, 16);
            buffer.writeUtf(task.title, 96);
            buffer.writeUtf(task.description, 192);
            buffer.writeVarInt(task.progress);
            buffer.writeVarInt(task.target);
            buffer.writeVarInt(task.pointReward);
            buffer.writeResourceLocation(task.itemReward);
            buffer.writeVarInt(task.itemCount);
            buffer.writeBoolean(task.claimed);
        }
    }

    private static S2C_OpenPvpTasksPacket decode(FriendlyByteBuf buffer) {
        int points = buffer.readVarInt();
        int size = buffer.readVarInt();
        if (size < 0 || size > MAX_TASKS) throw new IllegalArgumentException("Invalid PvP task count: " + size);
        List<TaskEntry> tasks = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            tasks.add(new TaskEntry(buffer.readUtf(64), buffer.readUtf(16), buffer.readUtf(96),
                    buffer.readUtf(192), buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt(),
                    buffer.readResourceLocation(), buffer.readVarInt(), buffer.readBoolean()));
        }
        return new S2C_OpenPvpTasksPacket(points, List.copyOf(tasks));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public void handle(net.neoforged.neoforge.network.handling.IPayloadContext context) {
        context.enqueueWork(() -> com.ruskserver.moveearth_addtional.client.ClientPacketHandler.handleOpenPvpTasks(this));
    }

    public record TaskEntry(String id, String category, String title, String description,
                            int progress, int target, int pointReward,
                            ResourceLocation itemReward, int itemCount, boolean claimed) {
        public boolean complete() {
            return progress >= target;
        }
    }
}
