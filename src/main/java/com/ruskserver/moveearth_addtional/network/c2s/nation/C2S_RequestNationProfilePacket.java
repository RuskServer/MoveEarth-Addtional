package com.ruskserver.moveearth_addtional.network.c2s.nation;

import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import com.ruskserver.moveearth_addtional.s2.nation.NationProfileService;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

/** Asks for another nation's profile, opened from the diplomacy tab. */
public record C2S_RequestNationProfilePacket(UUID nationId) implements CustomPacketPayload {
    public static final Type<C2S_RequestNationProfilePacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Moveearth_addtional.MODID, "nation_profile_request"));
    public static final StreamCodec<FriendlyByteBuf, C2S_RequestNationProfilePacket> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> buf.writeUUID(p.nationId),
            buf -> new C2S_RequestNationProfilePacket(buf.readUUID()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public void handle(net.neoforged.neoforge.network.handling.IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) NationProfileService.send(player, nationId);
        });
    }
}
