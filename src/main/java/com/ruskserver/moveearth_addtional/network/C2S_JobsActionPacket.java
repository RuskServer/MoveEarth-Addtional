package com.ruskserver.moveearth_addtional.network;

import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import com.ruskserver.moveearth_addtional.jobs.JobsScreenSync;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

public record C2S_JobsActionPacket(String action, String jobId, String targetName, int amount)
        implements CustomPacketPayload {
    public static final int MAX_ABSOLUTE_AMOUNT = 1_000_000;
    public static final Type<C2S_JobsActionPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Moveearth_addtional.MODID, "jobs_action"));
    public static final StreamCodec<FriendlyByteBuf, C2S_JobsActionPacket> STREAM_CODEC = StreamCodec.of(
            C2S_JobsActionPacket::encode, C2S_JobsActionPacket::decode);

    private static void encode(FriendlyByteBuf buffer, C2S_JobsActionPacket packet) {
        buffer.writeUtf(packet.action, 16);
        buffer.writeUtf(packet.jobId, 128);
        buffer.writeUtf(packet.targetName, 16);
        buffer.writeInt(packet.amount);
    }

    private static C2S_JobsActionPacket decode(FriendlyByteBuf buffer) {
        return new C2S_JobsActionPacket(
                buffer.readUtf(16), buffer.readUtf(128), buffer.readUtf(16), buffer.readInt());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public void handle(net.neoforged.neoforge.network.handling.IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                JobsScreenSync.handleAction(player, this);
            }
        });
    }
}
