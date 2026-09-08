package com.ruskserver.moveearth_addtional.network;

import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import com.ruskserver.moveearth_addtional.detector.DetectorNamePolicy;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Returns the authoritative detector name and rename result to its open screen. */
public record S2C_SyncDetectorNamePacket(
        BlockPos pos,
        boolean success,
        String detectorName,
        String message
) implements CustomPacketPayload {
    private static final int MAX_MESSAGE_LENGTH = 128;

    public static final Type<S2C_SyncDetectorNamePacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Moveearth_addtional.MODID, "sync_detector_name")
    );

    public static final StreamCodec<FriendlyByteBuf, S2C_SyncDetectorNamePacket> STREAM_CODEC = StreamCodec.of(
            (buffer, packet) -> {
                buffer.writeBlockPos(packet.pos());
                buffer.writeBoolean(packet.success());
                buffer.writeUtf(packet.detectorName(), DetectorNamePolicy.MAX_LENGTH);
                buffer.writeUtf(packet.message(), MAX_MESSAGE_LENGTH);
            },
            buffer -> new S2C_SyncDetectorNamePacket(
                    buffer.readBlockPos(),
                    buffer.readBoolean(),
                    buffer.readUtf(DetectorNamePolicy.MAX_LENGTH),
                    buffer.readUtf(MAX_MESSAGE_LENGTH)
            )
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public void handle(net.neoforged.neoforge.network.handling.IPayloadContext context) {
        context.enqueueWork(() ->
                com.ruskserver.moveearth_addtional.client.ClientPacketHandler.handleSyncDetectorName(this));
    }
}
