package com.ruskserver.moveearth_addtional.network;

import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import com.ruskserver.moveearth_addtional.economy.BalanceScreenSync;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

public record C2S_BalanceActionPacket(String action, UUID recipient, long amount) implements CustomPacketPayload {
    public static final Type<C2S_BalanceActionPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Moveearth_addtional.MODID, "balance_action"));
    public static final StreamCodec<FriendlyByteBuf, C2S_BalanceActionPacket> STREAM_CODEC = StreamCodec.of(
            (buffer, packet) -> {
                buffer.writeUtf(packet.action, 12);
                buffer.writeUUID(packet.recipient);
                buffer.writeVarLong(packet.amount);
            },
            buffer -> new C2S_BalanceActionPacket(buffer.readUtf(12), buffer.readUUID(), buffer.readVarLong()));

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    public void handle(net.neoforged.neoforge.network.handling.IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) BalanceScreenSync.handle(player, this);
        });
    }
}
