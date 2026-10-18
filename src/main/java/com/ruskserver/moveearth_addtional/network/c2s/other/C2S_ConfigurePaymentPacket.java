package com.ruskserver.moveearth_addtional.network.c2s.other;


import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import com.ruskserver.moveearth_addtional.block.entity.PlayerDetectorBlockEntity;
import com.ruskserver.moveearth_addtional.economy.EconomyLedgerSavedData;
import com.ruskserver.moveearth_addtional.detector.DetectorOwnership;
import com.ruskserver.moveearth_addtional.detector.DetectorScreenSync;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

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
                    || !(player.serverLevel().getBlockEntity(pos) instanceof PlayerDetectorBlockEntity detector)) return;
            DetectorOwnership.settle(player.server, detector);
            if (!DetectorOwnership.mayManage(player, detector) || detector.getOwnerUUID() == null
                    && detector.getOwnerNation() == null) {
                DetectorScreenSync.refresh(player, detector, false, "この検知ブロックの維持費を払う権限がありません。");
                return;
            }
            long now = System.currentTimeMillis();
            if (detector.isActive() && now < detector.getNextPaymentTime()) return;
            EconomyLedgerSavedData ledger = EconomyLedgerSavedData.get(player.getServer());
            UUID chargeId = UUID.nameUUIDFromBytes(("detector:activate:" + player.serverLevel().dimension().location()
                    + ":" + pos.asLong() + ":" + detector.getPlacedTime() + ":" + detector.getNextPaymentTime())
                    .getBytes(StandardCharsets.UTF_8));
            EconomyLedgerSavedData.Result result = ledger.transfer(chargeId,
                    DetectorOwnership.account(detector), null, PlayerDetectorBlockEntity.UPKEEP_COST, "detector_activation");
            String account = detector.getOwnerNation() != null ? "国庫" : "口座";
            if (result == EconomyLedgerSavedData.Result.APPLIED
                    || result == EconomyLedgerSavedData.Result.ALREADY_APPLIED) {
                detector.setActive(true);
                if (detector.getOwnerNation() != null) detector.approveTreasury();
                detector.setNextPaymentTime(now + PlayerDetectorBlockEntity.UPKEEP_INTERVAL_MS);
                DetectorScreenSync.refresh(player, detector, true,
                        "検知器を" + PlayerDetectorBlockEntity.UPKEEP_CYCLE_HOURS + "時間有効化しました（"
                                + account + "から" + PlayerDetectorBlockEntity.UPKEEP_COST + " TC支払済み）。");
            } else {
                DetectorScreenSync.refresh(player, detector, false, account + "の残高が足りないため、検知器を有効化できません。");
            }
        });
    }
}
