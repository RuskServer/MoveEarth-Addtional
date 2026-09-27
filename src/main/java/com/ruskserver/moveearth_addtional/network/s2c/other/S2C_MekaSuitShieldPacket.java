package com.ruskserver.moveearth_addtional.network.s2c.other;

import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** The MekaSuit heavy-hit shield stopped a hit; {@code depleted} when that was its last charge. */
public record S2C_MekaSuitShieldPacket(boolean depleted) implements CustomPacketPayload {
    public static final Type<S2C_MekaSuitShieldPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Moveearth_addtional.MODID, "mekasuit_shield"));
    public static final StreamCodec<ByteBuf, S2C_MekaSuitShieldPacket> STREAM_CODEC =
            ByteBufCodecs.BOOL.map(S2C_MekaSuitShieldPacket::new, S2C_MekaSuitShieldPacket::depleted);

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public void handle(IPayloadContext context) {
        context.enqueueWork(() -> com.ruskserver.moveearth_addtional.client.MekaSuitShieldFlash.trigger(depleted));
    }
}
