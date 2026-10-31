package com.ruskserver.moveearth_addtional.network.s2c.pvp;

import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import java.util.UUID;

/**
 * The death camera: the player stays where they fell and the view follows their killer for {@code ticks}, with
 * a card naming the killer, the weapon, the killer's health and the distance. A zero {@code killerId} means
 * nobody was to blame, such as a fall or the battle area's edge; {@code x}/{@code y}/{@code z} is where to look
 * when the killer cannot be seen.
 */
public record S2C_PvpKillcamPacket(UUID killerId, String killer, double x, double y, double z, int ticks,
                                   String weapon, float killerHealth, float killerMaxHealth, float distance,
                                   int streak) implements CustomPacketPayload {
    public static final Type<S2C_PvpKillcamPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Moveearth_addtional.MODID, "pvp_killcam"));
    public static final StreamCodec<FriendlyByteBuf, S2C_PvpKillcamPacket> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeUUID(p.killerId);
                buf.writeUtf(p.killer, 64);
                buf.writeDouble(p.x);
                buf.writeDouble(p.y);
                buf.writeDouble(p.z);
                buf.writeVarInt(p.ticks);
                buf.writeUtf(p.weapon, 64);
                buf.writeFloat(p.killerHealth);
                buf.writeFloat(p.killerMaxHealth);
                buf.writeFloat(p.distance);
                buf.writeVarInt(p.streak);
            },
            buf -> new S2C_PvpKillcamPacket(buf.readUUID(), buf.readUtf(64), buf.readDouble(), buf.readDouble(),
                    buf.readDouble(), buf.readVarInt(), buf.readUtf(64), buf.readFloat(), buf.readFloat(),
                    buf.readFloat(), buf.readVarInt()));

    public boolean hasKiller() {
        return killerId.getMostSignificantBits() != 0L || killerId.getLeastSignificantBits() != 0L;
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    public void handle(net.neoforged.neoforge.network.handling.IPayloadContext context) {
        context.enqueueWork(() -> com.ruskserver.moveearth_addtional.client.ClientPacketHandler.handlePvpKillcam(this));
    }
}
