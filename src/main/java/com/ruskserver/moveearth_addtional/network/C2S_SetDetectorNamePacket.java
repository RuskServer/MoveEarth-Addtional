package com.ruskserver.moveearth_addtional.network;

import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import com.ruskserver.moveearth_addtional.block.entity.PlayerDetectorBlockEntity;
import com.ruskserver.moveearth_addtional.detector.DetectorNamePolicy;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

/** Requests a server-authoritative rename of one owned detector block. */
public record C2S_SetDetectorNamePacket(BlockPos pos, String detectorName) implements CustomPacketPayload {
    private static final double MAX_INTERACTION_DISTANCE_SQR = 8.0D * 8.0D;

    public static final Type<C2S_SetDetectorNamePacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Moveearth_addtional.MODID, "set_detector_name")
    );

    public static final StreamCodec<FriendlyByteBuf, C2S_SetDetectorNamePacket> STREAM_CODEC = StreamCodec.of(
            (buffer, packet) -> {
                buffer.writeBlockPos(packet.pos());
                buffer.writeUtf(packet.detectorName(), DetectorNamePolicy.MAX_LENGTH);
            },
            buffer -> new C2S_SetDetectorNamePacket(
                    buffer.readBlockPos(),
                    buffer.readUtf(DetectorNamePolicy.MAX_LENGTH)
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
            DetectorNamePolicy.Validation validation = DetectorNamePolicy.validate(this.detectorName);
            if (!validation.valid()) {
                sendResult(player, false, "", validation.errorMessage());
                return;
            }

            ServerLevel level = player.serverLevel();
            if (!level.hasChunkAt(this.pos)
                    || player.position().distanceToSqr(Vec3.atCenterOf(this.pos)) > MAX_INTERACTION_DISTANCE_SQR
                    || !(level.getBlockEntity(this.pos) instanceof PlayerDetectorBlockEntity detector)
                    || detector.getOwnerUUID() == null
                    || !detector.getOwnerUUID().equals(player.getUUID())) {
                sendResult(player, false, "", "この検知ブロックの名称を変更する権限がありません。");
                return;
            }

            detector.setDetectorName(validation.normalized());
            String message = validation.normalized().isEmpty()
                    ? "検知ブロックの名称を未設定へ戻しました。"
                    : "検知ブロックの名称を「" + validation.normalized() + "」に変更しました。";
            sendResult(player, true, detector.getDetectorName(), message);
        });
    }

    private void sendResult(ServerPlayer player, boolean success, String detectorName, String message) {
        PacketDistributor.sendToPlayer(
                player,
                new S2C_SyncDetectorNamePacket(this.pos, success, detectorName, message)
        );
    }
}
