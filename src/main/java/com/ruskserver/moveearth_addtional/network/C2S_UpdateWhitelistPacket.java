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

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public record C2S_UpdateWhitelistPacket(BlockPos pos, String playerName, boolean isAdd)
        implements CustomPacketPayload {

    private static final int MAX_NAME_LENGTH = 16;
    private static final int MAX_WHITELIST_SIZE = 100;
    private static final double MAX_INTERACTION_DISTANCE_SQR = 8.0D * 8.0D;

    public static final Type<C2S_UpdateWhitelistPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Moveearth_addtional.MODID, "update_whitelist")
    );

    public static final StreamCodec<FriendlyByteBuf, C2S_UpdateWhitelistPacket> STREAM_CODEC = StreamCodec.of(
            (buf, packet) -> {
                buf.writeBlockPos(packet.pos());
                buf.writeUtf(packet.playerName(), MAX_NAME_LENGTH);
                buf.writeBoolean(packet.isAdd());
            },
            buf -> new C2S_UpdateWhitelistPacket(
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
                    || !(level.getBlockEntity(this.pos) instanceof PlayerDetectorBlockEntity detector)) {
                return;
            }

            UUID ownerUuid = detector.getOwnerUUID();
            PlayerWhitelistSavedData data = PlayerWhitelistSavedData.get(level);
            if (ownerUuid == null || !data.canEditWhitelist(ownerUuid, player.getUUID())) {
                return;
            }
            if (this.playerName == null || this.playerName.isBlank()
                    || this.playerName.length() > MAX_NAME_LENGTH
                    || !this.playerName.matches("[A-Za-z0-9_]+")) {
                return;
            }

            if (this.isAdd) {
                ServerPlayer targetPlayer = level.getServer().getPlayerList().getPlayerByName(this.playerName);
                if (targetPlayer == null) {
                    return;
                }
                List<String> currentNames = data.getMemberNamesForDisplay(ownerUuid);
                if (currentNames.size() >= MAX_WHITELIST_SIZE
                        && currentNames.stream().noneMatch(this.playerName::equalsIgnoreCase)) {
                    return;
                }
                data.addToWhitelist(ownerUuid, targetPlayer.getUUID(), targetPlayer.getScoreboardName());
            } else {
                data.removeFromWhitelistByName(ownerUuid, this.playerName);
            }

            List<String> onlinePlayers = new ArrayList<>();
            for (ServerPlayer onlinePlayer : level.getServer().getPlayerList().getPlayers()) {
                onlinePlayers.add(onlinePlayer.getScoreboardName());
            }
            PacketDistributor.sendToPlayer(player, new S2C_SyncWhitelistPacket(
                    this.pos,
                    data.getMemberNamesForDisplay(ownerUuid),
                    onlinePlayers
            ));
            Moveearth_addtional.LOGGER.info(
                    "Detector whitelist {} by {} for owner {}: {}",
                    this.isAdd ? "addition" : "removal",
                    player.getGameProfile().getName(),
                    ownerUuid,
                    this.playerName
            );
        });
    }
}
