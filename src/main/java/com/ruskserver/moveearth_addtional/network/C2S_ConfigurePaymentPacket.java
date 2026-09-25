package com.ruskserver.moveearth_addtional.network;

import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import com.ruskserver.moveearth_addtional.block.entity.PlayerDetectorBlockEntity;
import com.ruskserver.moveearth_addtional.economy.EconomyLedgerSavedData;
import com.ruskserver.moveearth_addtional.ui.MoveEarthMessage;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/** Detector charges always come from its owner's MoveEarth balance. */
public record C2S_ConfigurePaymentPacket(BlockPos pos) implements CustomPacketPayload {
    public static final Type<C2S_ConfigurePaymentPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Moveearth_addtional.MODID, "configure_payment"));
    public static final StreamCodec<FriendlyByteBuf, C2S_ConfigurePaymentPacket> STREAM_CODEC = StreamCodec.of(
            (buf, packet) -> buf.writeBlockPos(packet.pos),
            buf -> new C2S_ConfigurePaymentPacket(buf.readBlockPos()));

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public void handle(net.neoforged.neoforge.network.handling.IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)
                    || !ServerPacketGuards.canAccessLoadedBlock(player, pos, 64.0D)
                    || !(player.serverLevel().getBlockEntity(pos) instanceof PlayerDetectorBlockEntity detector)
                    || !player.getUUID().equals(detector.getOwnerUUID())) return;
            long now = System.currentTimeMillis();
            if (detector.isActive() && now < detector.getNextPaymentTime()) return;
            EconomyLedgerSavedData ledger = EconomyLedgerSavedData.get(player.getServer());
            UUID chargeId = UUID.nameUUIDFromBytes(("detector:activate:" + player.serverLevel().dimension().location()
                    + ":" + pos.asLong() + ":" + detector.getPlacedTime() + ":" + detector.getNextPaymentTime())
                    .getBytes(StandardCharsets.UTF_8));
            EconomyLedgerSavedData.Result result = ledger.transfer(chargeId,
                    EconomyLedgerSavedData.Account.player(player.getUUID()), null, 5L, "detector_activation");
            if (result == EconomyLedgerSavedData.Result.APPLIED
                    || result == EconomyLedgerSavedData.Result.ALREADY_APPLIED) {
                detector.setActive(true);
                detector.setNextPaymentTime(now + 2 * 60 * 60 * 1000L);
                player.sendSystemMessage(MoveEarthMessage.success("検知器を2時間有効化しました（5支払済み）。"));
            } else {
                player.sendSystemMessage(MoveEarthMessage.error("残高不足のため検知器を有効化できません。"));
            }
            PacketDistributor.sendToPlayer(player, new S2C_SyncDetectorPaymentPacket(pos,
                    detector.isActive(), detector.getNextPaymentTime(), detector.getPlacedTime(),
                    ledger.balance(EconomyLedgerSavedData.Account.player(player.getUUID()))));
        });
    }
}
