package com.ruskserver.moveearth_addtional.network;

import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import com.ruskserver.moveearth_addtional.client.ClientPacketHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record S2C_OpenStatsScreenPacket(String playerName, int playTimeTicks, int playerKills, int mobKills,
                                        int deaths, int damageDealt, int damageTaken, int distanceCm, int jumps)
        implements CustomPacketPayload {
    public static final Type<S2C_OpenStatsScreenPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Moveearth_addtional.MODID, "open_stats_screen"));
    public static final StreamCodec<FriendlyByteBuf, S2C_OpenStatsScreenPacket> STREAM_CODEC = StreamCodec.of(
            (buf, packet) -> packet.write(buf), S2C_OpenStatsScreenPacket::new);

    public S2C_OpenStatsScreenPacket(FriendlyByteBuf buf) {
        this(buf.readUtf(16), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
                buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt());
    }

    private void write(FriendlyByteBuf buf) {
        buf.writeUtf(playerName, 16);
        buf.writeVarInt(playTimeTicks);
        buf.writeVarInt(playerKills);
        buf.writeVarInt(mobKills);
        buf.writeVarInt(deaths);
        buf.writeVarInt(damageDealt);
        buf.writeVarInt(damageTaken);
        buf.writeVarInt(distanceCm);
        buf.writeVarInt(jumps);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public void handle(IPayloadContext context) {
        context.enqueueWork(() -> ClientPacketHandler.handleOpenStatsScreen(this));
    }
}
