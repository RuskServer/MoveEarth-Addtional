package com.ruskserver.moveearth_addtional.network;

import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import com.ruskserver.moveearth_addtional.s2.dispatch.RecoveryDispatchViewService;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public record C2S_RecoveryDispatchActionPacket(int requestId, Action action, UUID targetId,
        UUID secondaryId, UUID tertiaryId, long expectedRevision, long amount,
        long auxiliaryAmount, int option, List<UUID> participants) implements CustomPacketPayload {
    private static final UUID NONE = new UUID(0L, 0L);
    public static final Type<C2S_RecoveryDispatchActionPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Moveearth_addtional.MODID, "recovery_dispatch_action"));
    public static final StreamCodec<FriendlyByteBuf, C2S_RecoveryDispatchActionPacket> STREAM_CODEC = StreamCodec.of(
            C2S_RecoveryDispatchActionPacket::write, C2S_RecoveryDispatchActionPacket::read);

    public C2S_RecoveryDispatchActionPacket {
        if (action == null) action = Action.REFRESH;
        participants = participants == null ? List.of() : List.copyOf(participants.subList(0, Math.min(64, participants.size())));
    }

    private static void write(FriendlyByteBuf b, C2S_RecoveryDispatchActionPacket value) {
        b.writeVarInt(value.requestId); b.writeByte(value.action.ordinal());
        b.writeUUID(value.targetId == null ? NONE : value.targetId);
        b.writeUUID(value.secondaryId == null ? NONE : value.secondaryId);
        b.writeUUID(value.tertiaryId == null ? NONE : value.tertiaryId);
        b.writeVarLong(Math.max(0L, value.expectedRevision)); b.writeVarLong(Math.max(0L, value.amount));
        b.writeVarLong(Math.max(0L, value.auxiliaryAmount)); b.writeVarInt(Math.max(0, value.option));
        b.writeVarInt(value.participants.size()); for (UUID id : value.participants) b.writeUUID(id);
    }
    private static C2S_RecoveryDispatchActionPacket read(FriendlyByteBuf b) {
        int request = b.readVarInt(); int ordinal = b.readUnsignedByte(); UUID target = optional(b.readUUID());
        UUID secondary = optional(b.readUUID()); UUID tertiary = optional(b.readUUID());
        long revision = b.readVarLong(); long amount = b.readVarLong(); long auxiliary = b.readVarLong();
        int option = b.readVarInt(); int count = b.readVarInt();
        if (count < 0 || count > 64) throw new IllegalArgumentException("Too many dispatch participants");
        List<UUID> participants = new ArrayList<>(count); for (int i = 0; i < count; i++) participants.add(b.readUUID());
        return new C2S_RecoveryDispatchActionPacket(request, Action.fromId(ordinal), target, secondary,
                tertiary, revision, amount, auxiliary, option, participants);
    }
    private static UUID optional(UUID id) { return NONE.equals(id) ? null : id; }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    public void handle(net.neoforged.neoforge.network.handling.IPayloadContext context) {
        context.enqueueWork(() -> { if (context.player() instanceof ServerPlayer player)
            RecoveryDispatchViewService.handle(player, this); });
    }
    public enum Action {
        REFRESH, CREATE, APPROVE, APPROVE_SUBSIDY, CONSENT, DECLINE, FUND, CANCEL,
        WAIVE_PROTECTION, SET_RIVAL, CLEAR_RIVAL, ADMIN_MINT, NATION_DONATE;
        static Action fromId(int id) { return id >= 0 && id < values().length ? values()[id] : REFRESH; }
    }
}
