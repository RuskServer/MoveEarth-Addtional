package com.ruskserver.moveearth_addtional.network;

import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import com.ruskserver.moveearth_addtional.pvp.PvpRewardData;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

public record C2S_ClaimPvpTaskPacket(String taskId) implements CustomPacketPayload {
    public static final Type<C2S_ClaimPvpTaskPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Moveearth_addtional.MODID, "pvp_task_claim"));
    public static final StreamCodec<FriendlyByteBuf, C2S_ClaimPvpTaskPacket> STREAM_CODEC = StreamCodec.of(
            (buffer, packet) -> buffer.writeUtf(packet.taskId, 64),
            buffer -> new C2S_ClaimPvpTaskPacket(buffer.readUtf(64)));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public void handle(net.neoforged.neoforge.network.handling.IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            PvpRewardData rewards = PvpRewardData.get(player.server);
            rewards.claim(player, taskId);
            rewards.openTaskScreen(player);
        });
    }
}
