package com.ruskserver.moveearth_addtional.network;

import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import com.ruskserver.moveearth_addtional.s2.S2HubTab;
import com.ruskserver.moveearth_addtional.s2.S2NationViewService;
import com.ruskserver.moveearth_addtional.s2.nation.NationSavedData;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

public record C2S_CreateNationPacket(int requestId, long expectedRevision, String name, String tag)
        implements CustomPacketPayload {
    public static final Type<C2S_CreateNationPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Moveearth_addtional.MODID, "nation_create"));
    public static final StreamCodec<FriendlyByteBuf, C2S_CreateNationPacket> STREAM_CODEC = StreamCodec.of(
            (buffer, packet) -> {
                buffer.writeVarInt(packet.requestId);
                buffer.writeLong(packet.expectedRevision);
                buffer.writeUtf(packet.name, 32);
                buffer.writeUtf(packet.tag, 5);
            },
            buffer -> new C2S_CreateNationPacket(buffer.readVarInt(), buffer.readLong(),
                    buffer.readUtf(32), buffer.readUtf(5)));

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public void handle(net.neoforged.neoforge.network.handling.IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            NationSavedData data = NationSavedData.get(player.server);
            NationSavedData.CreateResult result = data.create(player.getUUID(),
                    player.getGameProfile().getName(), name, tag, expectedRevision);
            boolean success = result.status() == NationSavedData.Status.CREATED;
            String messageKey = switch (result.status()) {
                case CREATED -> "screen.moveearth_addtional.nation.created";
                case INVALID -> "screen.moveearth_addtional.nation.invalid";
                case DUPLICATE -> "screen.moveearth_addtional.nation.duplicate";
                case ALREADY_MEMBER -> "screen.moveearth_addtional.nation.already_member";
                case STALE -> "screen.moveearth_addtional.nation.stale";
            };
            if (success) S2NationViewService.INSTANCE.sendHub(player, S2HubTab.OVERVIEW);
            PacketDistributor.sendToPlayer(player, new S2C_S2ActionResultPacket(
                    requestId, success, result.revision(), messageKey));
        });
    }
}
