package com.ruskserver.moveearth_addtional.network;

import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Keeps an already-open PvP screen synchronized with queue and match changes. */
public record S2C_PvpEntryStatePacket(boolean joined, boolean active, boolean hosting, boolean matchRunning,
                                      int entryCount) implements CustomPacketPayload {
    public static final Type<S2C_PvpEntryStatePacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Moveearth_addtional.MODID, "pvp_entry_state"));
    public static final StreamCodec<FriendlyByteBuf, S2C_PvpEntryStatePacket> STREAM_CODEC = StreamCodec.of(
            (buffer, packet) -> {
                buffer.writeBoolean(packet.joined);
                buffer.writeBoolean(packet.active);
                buffer.writeBoolean(packet.hosting);
                buffer.writeBoolean(packet.matchRunning);
                buffer.writeVarInt(packet.entryCount);
            },
            buffer -> new S2C_PvpEntryStatePacket(buffer.readBoolean(), buffer.readBoolean(),
                    buffer.readBoolean(), buffer.readBoolean(), buffer.readVarInt()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public void handle(net.neoforged.neoforge.network.handling.IPayloadContext context) {
        context.enqueueWork(() -> com.ruskserver.moveearth_addtional.client.ClientPacketHandler.handlePvpEntryState(this));
    }
}
