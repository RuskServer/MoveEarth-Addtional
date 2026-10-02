package com.ruskserver.moveearth_addtional.network.c2s.siege;

import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import com.ruskserver.moveearth_addtional.s2.siege.WarConsentService;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

/** The confirm button of the war-consent prompt; cancelling sends nothing. */
public record C2S_WarConsentPacket(UUID targetNationId) implements CustomPacketPayload {
    public static final Type<C2S_WarConsentPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Moveearth_addtional.MODID, "war_consent"));
    public static final StreamCodec<FriendlyByteBuf, C2S_WarConsentPacket> STREAM_CODEC = StreamCodec.of(
            (buffer, packet) -> buffer.writeUUID(packet.targetNationId()),
            buffer -> new C2S_WarConsentPacket(buffer.readUUID()));

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public void handle(net.neoforged.neoforge.network.handling.IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) WarConsentService.confirm(player, targetNationId);
        });
    }
}
