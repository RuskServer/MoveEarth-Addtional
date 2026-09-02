package com.ruskserver.moveearth_addtional.network;

import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

public record S2C_SyncDetectorManagersPacket(
        BlockPos pos,
        List<String> managers,
        boolean success,
        String message
) implements CustomPacketPayload {
    private static final int MAX_MESSAGE_LENGTH = 128;

    public static final Type<S2C_SyncDetectorManagersPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Moveearth_addtional.MODID, "sync_detector_managers")
    );

    public static final StreamCodec<FriendlyByteBuf, S2C_SyncDetectorManagersPacket> STREAM_CODEC = StreamCodec.of(
            (buf, packet) -> {
                buf.writeBlockPos(packet.pos());
                buf.writeCollection(packet.managers(), FriendlyByteBuf::writeUtf);
                buf.writeBoolean(packet.success());
                buf.writeUtf(packet.message(), MAX_MESSAGE_LENGTH);
            },
            buf -> new S2C_SyncDetectorManagersPacket(
                    buf.readBlockPos(),
                    buf.readCollection(ArrayList::new, FriendlyByteBuf::readUtf),
                    buf.readBoolean(),
                    buf.readUtf(MAX_MESSAGE_LENGTH)
            )
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public void handle(net.neoforged.neoforge.network.handling.IPayloadContext context) {
        context.enqueueWork(() ->
                com.ruskserver.moveearth_addtional.client.ClientPacketHandler.handleSyncDetectorManagers(this));
    }
}
