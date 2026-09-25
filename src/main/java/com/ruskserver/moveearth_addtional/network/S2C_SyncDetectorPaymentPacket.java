package com.ruskserver.moveearth_addtional.network;

import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record S2C_SyncDetectorPaymentPacket(BlockPos pos, boolean isActive,
        long nextPaymentTime, long placedTime, long ownerBalance) implements CustomPacketPayload {
    public static final Type<S2C_SyncDetectorPaymentPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Moveearth_addtional.MODID, "sync_detector_payment"));
    public static final StreamCodec<FriendlyByteBuf, S2C_SyncDetectorPaymentPacket> STREAM_CODEC = StreamCodec.of(
            (buf, packet) -> {
                buf.writeBlockPos(packet.pos);
                buf.writeBoolean(packet.isActive);
                buf.writeLong(packet.nextPaymentTime);
                buf.writeLong(packet.placedTime);
                buf.writeLong(packet.ownerBalance);
            },
            buf -> new S2C_SyncDetectorPaymentPacket(buf.readBlockPos(), buf.readBoolean(),
                    buf.readLong(), buf.readLong(), buf.readLong()));

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public void handle(net.neoforged.neoforge.network.handling.IPayloadContext context) {
        context.enqueueWork(() -> com.ruskserver.moveearth_addtional.client.ClientPacketHandler.handleSyncDetectorPayment(this));
    }
}
