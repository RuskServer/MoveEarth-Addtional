package com.ruskserver.moveearth_addtional.network;

import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import com.ruskserver.moveearth_addtional.pvp.PvpRewardData;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

public record C2S_RequestPvpTasksPacket() implements CustomPacketPayload {
    public static final Type<C2S_RequestPvpTasksPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Moveearth_addtional.MODID, "pvp_tasks_request"));
    public static final StreamCodec<FriendlyByteBuf, C2S_RequestPvpTasksPacket> STREAM_CODEC =
            StreamCodec.unit(new C2S_RequestPvpTasksPacket());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public void handle(net.neoforged.neoforge.network.handling.IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) PvpRewardData.get(player.server).openTaskScreen(player);
        });
    }
}
