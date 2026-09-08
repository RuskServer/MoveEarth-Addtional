package com.ruskserver.moveearth_addtional.network;

import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import com.ruskserver.moveearth_addtional.block.entity.PlayerDetectorBlockEntity;
import com.ruskserver.moveearth_addtional.data.PlayerWhitelistSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.UUID;

/** Owner-only grant or revocation of a base-wide whitelist manager. */
public record C2S_UpdateDetectorManagerPacket(BlockPos pos, String playerName, boolean isAdd)
        implements CustomPacketPayload {
    private static final int MAX_NAME_LENGTH = 16;
    private static final int MAX_MANAGERS = 20;
    private static final double MAX_INTERACTION_DISTANCE_SQR = 8.0D * 8.0D;

    public static final Type<C2S_UpdateDetectorManagerPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Moveearth_addtional.MODID, "update_detector_manager")
    );

    public static final StreamCodec<FriendlyByteBuf, C2S_UpdateDetectorManagerPacket> STREAM_CODEC = StreamCodec.of(
            (buf, packet) -> {
                buf.writeBlockPos(packet.pos());
                buf.writeUtf(packet.playerName(), MAX_NAME_LENGTH);
                buf.writeBoolean(packet.isAdd());
            },
            buf -> new C2S_UpdateDetectorManagerPacket(
                    buf.readBlockPos(),
                    buf.readUtf(MAX_NAME_LENGTH),
                    buf.readBoolean()
            )
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public void handle(net.neoforged.neoforge.network.handling.IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            ServerLevel level = player.serverLevel();
            if (!level.hasChunkAt(this.pos)
                    || player.position().distanceToSqr(Vec3.atCenterOf(this.pos)) > MAX_INTERACTION_DISTANCE_SQR
                    || !(level.getBlockEntity(this.pos) instanceof PlayerDetectorBlockEntity detector)
                    || detector.getOwnerUUID() == null
                    || !detector.getOwnerUUID().equals(player.getUUID())) {
                return;
            }
            if (this.playerName == null || this.playerName.isBlank()
                    || this.playerName.length() > MAX_NAME_LENGTH
                    || !this.playerName.matches("[A-Za-z0-9_]+")) {
                return;
            }

            UUID ownerUuid = detector.getOwnerUUID();
            PlayerWhitelistSavedData data = PlayerWhitelistSavedData.get(level);
            String message;
            boolean success;
            if (this.isAdd) {
                ServerPlayer target = level.getServer().getPlayerList().getPlayerByName(this.playerName);
                if (target == null) {
                    sendResult(player, data, ownerUuid, false, "追加するプレイヤーがオンラインではありません。");
                    return;
                }
                if (target.getUUID().equals(ownerUuid)) {
                    sendResult(player, data, ownerUuid, false, "所有者自身を管理者に追加する必要はありません。");
                    return;
                }
                if (data.getManagers(ownerUuid).size() >= MAX_MANAGERS
                        && !data.isManager(ownerUuid, target.getUUID())) {
                    sendResult(player, data, ownerUuid, false, "拠点管理者は最大" + MAX_MANAGERS + "人です。");
                    return;
                }
                success = data.addManager(ownerUuid, target.getUUID(), target.getScoreboardName());
                message = success ? target.getScoreboardName() + "を拠点管理者に追加しました。" : "すでに拠点管理者です。";
            } else {
                success = data.removeManagerByName(ownerUuid, this.playerName);
                message = success ? this.playerName + "の拠点管理者権限を解除しました。" : "指定した拠点管理者が見つかりません。";
            }

            sendResult(player, data, ownerUuid, success, message);
            Moveearth_addtional.LOGGER.info(
                    "Detector manager {} by owner {}: {}",
                    this.isAdd ? "grant" : "revocation",
                    player.getGameProfile().getName(),
                    this.playerName
            );
        });
    }

    private void sendResult(
            ServerPlayer player,
            PlayerWhitelistSavedData data,
            UUID ownerUuid,
            boolean success,
            String message
    ) {
        PacketDistributor.sendToPlayer(player, new S2C_SyncDetectorManagersPacket(
                this.pos,
                data.getManagerNamesForDisplay(ownerUuid),
                success,
                message
        ));
    }
}
