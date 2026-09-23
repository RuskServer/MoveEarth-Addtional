package com.ruskserver.moveearth_addtional.network;

import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

public record C2S_CreateNationPacket(int requestId, long expectedRevision, String name, String tag,
                                     ResourceLocation dimension, BlockPos corePos)
        implements CustomPacketPayload {
    public static final Type<C2S_CreateNationPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Moveearth_addtional.MODID, "nation_create"));
    public static final StreamCodec<FriendlyByteBuf, C2S_CreateNationPacket> STREAM_CODEC = StreamCodec.of(
            (buffer, packet) -> {
                buffer.writeVarInt(packet.requestId);
                buffer.writeLong(packet.expectedRevision);
                buffer.writeUtf(packet.name, 32);
                buffer.writeUtf(packet.tag, 5);
                buffer.writeResourceLocation(packet.dimension);
                buffer.writeBlockPos(packet.corePos);
            },
            buffer -> new C2S_CreateNationPacket(buffer.readVarInt(), buffer.readLong(),
                    buffer.readUtf(32), buffer.readUtf(5), buffer.readResourceLocation(), buffer.readBlockPos()));

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public void handle(net.neoforged.neoforge.network.handling.IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            var result = com.ruskserver.moveearth_addtional.s2.nation.NationFoundationService.establish(
                    player, name, tag, expectedRevision, dimension, corePos);
            boolean success = result.success();
            String messageKey = switch (result.status()) {
                case CREATED -> "screen.moveearth_addtional.nation.created";
                case INVALID -> "screen.moveearth_addtional.nation.invalid";
                case DUPLICATE -> "screen.moveearth_addtional.nation.duplicate";
                case ALREADY_MEMBER -> "screen.moveearth_addtional.nation.already_member";
                case STALE -> "screen.moveearth_addtional.nation.stale";
                // The same sentence the selection overlay was already showing,
                // rather than a generic one listing every possible cause.
                case INVALID_LOCATION -> result.verdict() == null
                        ? "screen.moveearth_addtional.nation.invalid_location"
                        : result.verdict().messageKey();
                case TERRITORY_CONFLICT -> "screen.moveearth_addtional.nation.territory_conflict";
                case PLACEMENT_FAILED -> "screen.moveearth_addtional.nation.placement_failed";
            };
            if (success && result.core() != null) {
                PacketDistributor.sendToPlayer(player, new S2C_OpenTerritoryCoreScreenPacket(
                        result.core().pos(), result.core().radius(), result.core().state(),
                        result.core().health(), result.core().maximumHealth()));
            }
            PacketDistributor.sendToPlayer(player, new S2C_S2ActionResultPacket(
                    requestId, success, result.revision(), messageKey));
        });
    }
}
