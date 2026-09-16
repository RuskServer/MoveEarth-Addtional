package com.ruskserver.moveearth_addtional.network;

import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import com.ruskserver.moveearth_addtional.s2.siege.PrisonerService;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

public record C2S_PrisonerActionPacket(Action action, UUID targetId, BlockPos intakePos)
        implements CustomPacketPayload {
    private static final UUID NONE = new UUID(0L, 0L);
    public static final Type<C2S_PrisonerActionPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Moveearth_addtional.MODID, "prisoner_action"));
    public static final StreamCodec<FriendlyByteBuf, C2S_PrisonerActionPacket> STREAM_CODEC = StreamCodec.of(
            (buffer, packet) -> {
                buffer.writeByte(packet.action.ordinal());
                buffer.writeUUID(packet.targetId == null ? NONE : packet.targetId);
                buffer.writeBoolean(packet.intakePos != null);
                if (packet.intakePos != null) buffer.writeBlockPos(packet.intakePos);
            }, buffer -> {
                int id = buffer.readUnsignedByte();
                UUID target = buffer.readUUID();
                BlockPos intake = buffer.readBoolean() ? buffer.readBlockPos() : null;
                return new C2S_PrisonerActionPacket(Action.fromId(id), target.equals(NONE) ? null : target, intake);
            });

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public void handle(net.neoforged.neoforge.network.handling.IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                PrisonerService.handleAction(player, action, targetId, intakePos);
            }
        });
    }

    public enum Action {
        IMPRISON, RELEASE, TRANSFER;
        private static Action fromId(int id) { return id >= 0 && id < values().length ? values()[id] : RELEASE; }
    }
}
