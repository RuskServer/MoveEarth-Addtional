package com.ruskserver.moveearth_addtional.network;

import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import com.ruskserver.moveearth_addtional.pvp.PvpMatchManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

public record C2S_PvpActionPacket(boolean join, int hotbarSlot) implements CustomPacketPayload {
    public static final Type<C2S_PvpActionPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Moveearth_addtional.MODID, "pvp_action"));
    public static final StreamCodec<FriendlyByteBuf, C2S_PvpActionPacket> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> { buf.writeBoolean(p.join); buf.writeVarInt(p.hotbarSlot); },
            buf -> new C2S_PvpActionPacket(buf.readBoolean(), buf.readVarInt()));
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    public void handle(net.neoforged.neoforge.network.handling.IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                if (join) PvpMatchManager.INSTANCE.join(player, hotbarSlot); else PvpMatchManager.INSTANCE.leave(player);
            }
        });
    }
}
