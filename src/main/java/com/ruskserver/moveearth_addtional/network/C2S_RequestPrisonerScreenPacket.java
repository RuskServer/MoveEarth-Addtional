package com.ruskserver.moveearth_addtional.network;

import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import com.ruskserver.moveearth_addtional.s2.siege.PrisonerService;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

public record C2S_RequestPrisonerScreenPacket(BlockPos intakePos) implements CustomPacketPayload {
    public static final Type<C2S_RequestPrisonerScreenPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Moveearth_addtional.MODID, "prisoner_screen_request"));
    public static final StreamCodec<FriendlyByteBuf, C2S_RequestPrisonerScreenPacket> STREAM_CODEC = StreamCodec.of(
            (buffer, packet) -> {
                buffer.writeBoolean(packet.intakePos != null);
                if (packet.intakePos != null) buffer.writeBlockPos(packet.intakePos);
            }, buffer -> new C2S_RequestPrisonerScreenPacket(buffer.readBoolean() ? buffer.readBlockPos() : null));

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public void handle(net.neoforged.neoforge.network.handling.IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) PrisonerService.sendSnapshot(player, true, intakePos);
        });
    }
}
