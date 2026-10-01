package com.ruskserver.moveearth_addtional.network.c2s.nation;

import com.ruskserver.moveearth_addtional.network.s2c.other.S2C_S2ActionResultPacket;

import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import com.ruskserver.moveearth_addtional.network.common.C2SPacketGate;
import com.ruskserver.moveearth_addtional.network.common.C2SRateLimiter;
import com.ruskserver.moveearth_addtional.s2.S2Permission;
import com.ruskserver.moveearth_addtional.s2.nation.NationSavedData;
import com.ruskserver.moveearth_addtional.s2.notification.NationNotificationSavedData;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.UUID;

public record C2S_NotificationActionPacket(int requestId, Action action) implements CustomPacketPayload {
    public static final Type<C2S_NotificationActionPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Moveearth_addtional.MODID, "notification_action"));
    public static final StreamCodec<RegistryFriendlyByteBuf, C2S_NotificationActionPacket> STREAM_CODEC =
            StreamCodec.of((buffer, packet) -> {
                buffer.writeVarInt(packet.requestId);
                buffer.writeByte(packet.action.ordinal());
            }, buffer -> new C2S_NotificationActionPacket(buffer.readVarInt(),
                    Action.at(buffer.readUnsignedByte())));

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public void handle(net.neoforged.neoforge.network.handling.IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            NationNotificationSavedData data = NationNotificationSavedData.get(player.server);
            NationSavedData nations = NationSavedData.get(player.server);
            UUID nationId = nations.nationIdFor(player.getUUID()).orElse(null);
            boolean manage = nationId != null && nations.can(player.getUUID(), S2Permission.MANAGE_NOTIFICATIONS);
            boolean success;
            String result;
            // Unlinks write the shared Discord audit log, so they get a much tighter budget than the
            // packet itself; a refused attempt is answered but not audited.
            if ((action == Action.UNLINK_ACCOUNT || action == Action.UNLINK_NATION)
                    && !C2SPacketGate.allowSubAction(player, "notification_unlink", C2SRateLimiter.AUDIT)) {
                PacketDistributor.sendToPlayer(player, new S2C_S2ActionResultPacket(requestId, false,
                        data.revision(), "screen.moveearth_addtional.notifications.result.rate_limited"));
                return;
            }
            switch (action) {
                case TEST -> {
                    long now = System.currentTimeMillis();
                    if (manage && data.link(nationId).linked() && data.testThrottled(nationId, now)) {
                        success = false;
                        result = "test_cooldown";
                    } else {
                        success = manage && data.enqueueTest(nationId, now).isPresent();
                        result = success ? "test_queued" : manage ? "not_linked" : "no_permission";
                    }
                }
                case UNLINK_ACCOUNT -> {
                    success = data.unlinkAccount(player.getUUID());
                    result = success ? "account_unlinked" : "account_not_linked";
                    data.audit("account_unlink", nationId, player.getUUID(), 0L, success, result);
                }
                case UNLINK_NATION -> {
                    success = manage && data.link(nationId).linked();
                    if (success) data.unlink(nationId);
                    result = success ? "nation_unlinked" : manage ? "not_linked" : "no_permission";
                    data.audit("nation_unlink", nationId, player.getUUID(), 0L, success, result);
                }
                case REFRESH -> { success = true; result = "refreshed"; }
                default -> { success = false; result = "invalid_action"; }
            }
            PacketDistributor.sendToPlayer(player, new S2C_S2ActionResultPacket(requestId, success,
                    data.revision(), "screen.moveearth_addtional.notifications.result." + result));
            PacketDistributor.sendToPlayer(player, NationNotificationSnapshotFactory.create(player));
        });
    }

    public enum Action {
        TEST, UNLINK_ACCOUNT, UNLINK_NATION, REFRESH, UNKNOWN;
        static Action at(int index) { return index >= 0 && index < UNKNOWN.ordinal() ? values()[index] : UNKNOWN; }
    }
}
