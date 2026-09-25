package com.ruskserver.moveearth_addtional.network;

import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import com.ruskserver.moveearth_addtional.economy.MarketScreenSync;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

public record C2S_MarketActionPacket(String action, UUID target, int quantity, long unitPrice)
        implements CustomPacketPayload {
    public static final Type<C2S_MarketActionPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Moveearth_addtional.MODID, "market_action"));
    public static final StreamCodec<FriendlyByteBuf, C2S_MarketActionPacket> STREAM_CODEC = StreamCodec.of(
            (buf, packet) -> {
                buf.writeUtf(packet.action, 16);
                buf.writeUUID(packet.target);
                buf.writeVarInt(packet.quantity);
                buf.writeVarLong(packet.unitPrice);
            },
            buf -> new C2S_MarketActionPacket(buf.readUtf(16), buf.readUUID(),
                    buf.readVarInt(), buf.readVarLong()));

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    public void handle(net.neoforged.neoforge.network.handling.IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) MarketScreenSync.handle(player, this);
        });
    }
}
