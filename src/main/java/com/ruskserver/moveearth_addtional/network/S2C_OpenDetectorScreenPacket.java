package com.ruskserver.moveearth_addtional.network;

import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import com.ruskserver.moveearth_addtional.detector.DetectorNamePolicy;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

public record S2C_OpenDetectorScreenPacket(
        BlockPos pos,
        String detectorName,
        String ownerName,
        boolean ownerAccess,
        List<String> whitelist,
        List<String> managers,
        List<String> onlinePlayers
) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<S2C_OpenDetectorScreenPacket> TYPE = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(Moveearth_addtional.MODID, "open_detector_screen"));

    public static final StreamCodec<FriendlyByteBuf, S2C_OpenDetectorScreenPacket> STREAM_CODEC = StreamCodec.of(
            (buf, packet) -> {
                buf.writeBlockPos(packet.pos());
                buf.writeUtf(packet.detectorName(), DetectorNamePolicy.MAX_LENGTH);
                buf.writeUtf(packet.ownerName());
                buf.writeBoolean(packet.ownerAccess());
                buf.writeCollection(packet.whitelist(), FriendlyByteBuf::writeUtf);
                buf.writeCollection(packet.managers(), FriendlyByteBuf::writeUtf);
                buf.writeCollection(packet.onlinePlayers(), FriendlyByteBuf::writeUtf);
            },
            buf -> new S2C_OpenDetectorScreenPacket(
                    buf.readBlockPos(),
                    buf.readUtf(DetectorNamePolicy.MAX_LENGTH),
                    buf.readUtf(),
                    buf.readBoolean(),
                    buf.readCollection(ArrayList::new, FriendlyByteBuf::readUtf),
                    buf.readCollection(ArrayList::new, FriendlyByteBuf::readUtf),
                    buf.readCollection(ArrayList::new, FriendlyByteBuf::readUtf)
            )
    );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public void handle(net.neoforged.neoforge.network.handling.IPayloadContext context) {
        context.enqueueWork(() -> {
            com.ruskserver.moveearth_addtional.client.ClientPacketHandler.handleOpenDetectorScreen(this);
        });
    }
}
