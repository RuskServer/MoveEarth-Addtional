package com.ruskserver.moveearth_addtional.network;

import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import java.util.*;

public record S2C_PvpTeamPacket(List<UUID> allies) implements CustomPacketPayload {
    public static final Type<S2C_PvpTeamPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Moveearth_addtional.MODID, "pvp_team"));
    public static final StreamCodec<FriendlyByteBuf, S2C_PvpTeamPacket> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> { buf.writeVarInt(p.allies.size()); p.allies.forEach(buf::writeUUID); },
            buf -> { int size = buf.readVarInt(); List<UUID> ids = new ArrayList<>(size); for (int i=0;i<size;i++) ids.add(buf.readUUID()); return new S2C_PvpTeamPacket(ids); });
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    public void handle(net.neoforged.neoforge.network.handling.IPayloadContext context) {
        context.enqueueWork(() -> com.ruskserver.moveearth_addtional.client.ClientPacketHandler.handlePvpTeam(this));
    }
}
