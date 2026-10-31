package com.ruskserver.moveearth_addtional.network.s2c.pvp;

import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/** Where the player's teammates are, for the deploy screen's map. Enemies are never sent. */
public record S2C_PvpAllyPositionsPacket(List<Ally> allies) implements CustomPacketPayload {
    private static final int MAX_ALLIES = 128;

    public record Ally(String name, int x, int z, float yaw) { }

    public static final Type<S2C_PvpAllyPositionsPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Moveearth_addtional.MODID, "pvp_ally_positions"));
    public static final StreamCodec<FriendlyByteBuf, S2C_PvpAllyPositionsPacket> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeVarInt(p.allies.size());
                for (Ally ally : p.allies) {
                    buf.writeUtf(ally.name, 16);
                    buf.writeInt(ally.x);
                    buf.writeInt(ally.z);
                    buf.writeFloat(ally.yaw);
                }
            },
            buf -> {
                int count = buf.readVarInt();
                if (count < 0 || count > MAX_ALLIES) throw new IllegalArgumentException("Invalid ally count: " + count);
                List<Ally> allies = new ArrayList<>(count);
                for (int i = 0; i < count; i++) {
                    allies.add(new Ally(buf.readUtf(16), buf.readInt(), buf.readInt(), buf.readFloat()));
                }
                return new S2C_PvpAllyPositionsPacket(allies);
            });

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public void handle(net.neoforged.neoforge.network.handling.IPayloadContext context) {
        context.enqueueWork(() -> com.ruskserver.moveearth_addtional.client.ClientPacketHandler.handlePvpAllyPositions(this));
    }
}
