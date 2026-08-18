package com.ruskserver.moveearth_addtional.network;

import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import com.ruskserver.moveearth_addtional.block.entity.PlayerDetectorBlockEntity;
import io.github.lightman314.lightmanscurrency.api.money.bank.BankAPI;
import io.github.lightman314.lightmanscurrency.api.money.bank.IBankAccount;
import io.github.lightman314.lightmanscurrency.api.money.bank.reference.BankReference;
import io.github.lightman314.lightmanscurrency.api.money.value.MoneyValue;
import io.github.lightman314.lightmanscurrency.api.money.value.MoneyValueParser;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

public record C2S_ConfigurePaymentPacket(
        BlockPos pos,
        boolean isActivateRequest,
        BankReference newReference
) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<C2S_ConfigurePaymentPacket> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(Moveearth_addtional.MODID, "configure_payment"));

    public static final StreamCodec<FriendlyByteBuf, C2S_ConfigurePaymentPacket> STREAM_CODEC = StreamCodec.of(
            (buf, packet) -> {
                buf.writeBlockPos(packet.pos());
                buf.writeBoolean(packet.isActivateRequest());
                
                // newReference (Nullable)
                buf.writeBoolean(packet.newReference() != null);
                if (packet.newReference() != null) {
                    packet.newReference().encode(buf);
                }
            },
            buf -> {
                BlockPos pos = buf.readBlockPos();
                boolean isActivateRequest = buf.readBoolean();
                
                BankReference newReference = null;
                if (buf.readBoolean()) {
                    newReference = BankReference.decode(buf);
                }
                
                return new C2S_ConfigurePaymentPacket(pos, isActivateRequest, newReference);
            }
    );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public void handle(net.neoforged.neoforge.network.handling.IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                var level = player.serverLevel();
                var blockEntity = level.getBlockEntity(pos);
                
                if (blockEntity instanceof PlayerDetectorBlockEntity detector) {
                    // 所有者チェック
                    if (detector.getOwnerUUID() != null && detector.getOwnerUUID().equals(player.getUUID())) {
                        if (newReference != null && (!newReference.isValid() || !newReference.allowedAccess(player))) {
                            player.sendSystemMessage(Component.literal("この口座は利用できません。"));
                            return;
                        }
                        
                        // 1. 口座情報の更新
                        detector.setBankReference(newReference);
                        
                        // 2. 支払・有効化リクエストの処理
                        if (isActivateRequest) {
                            long currentTime = System.currentTimeMillis();
                            // 既に稼働中かつ期限内の場合は二重支払いを防ぐ
                            if (detector.isActive() && currentTime < detector.getNextPaymentTime()) {
                                detector.setChanged();
                                player.sendSystemMessage(Component.literal("§a引き落とし口座の変更を保存しました。"));
                            } else {
                                boolean paySuccess = false;
                                if (newReference != null && newReference.isValid()) {
                                    IBankAccount account = newReference.get();
                                    if (account != null) {
                                        MoneyValue fee = MoneyValueParser.ParseConfigString("coin;5-lightmanscurrency:coin_gold", () -> MoneyValue.empty());
                                        if (account.getStoredMoney().containsValue(fee)) {
                                            var result = BankAPI.getApi().BankWithdrawFromServer(account, fee);
                                            if (result.getFirst()) {
                                                paySuccess = true;
                                                detector.setActive(true);
                                                detector.setNextPaymentTime(currentTime + (2 * 60 * 60 * 1000));
                                                detector.setChanged();
                                                player.sendSystemMessage(Component.literal("§a検知ブロックを2時間有効化しました（5ゴールド支払済）。"));
                                            }
                                        }
                                    }
                                }
                                
                                if (!paySuccess) {
                                    detector.setActive(false);
                                    detector.setChanged();
                                    player.sendSystemMessage(Component.literal("§c支払いに失敗しました。口座の残高が不足しているか、無効な口座です。"));
                                }
                            }
                        } else {
                            detector.setChanged();
                            player.sendSystemMessage(Component.literal("§a引き落とし口座の変更を保存しました。"));
                        }
                        
                        // 3. 最新の状況をクライアントへ同期返信する
                        // プレイヤーがアクセスできる全口座を取得
                        List<BankReference> availableAccounts = new ArrayList<>();
                        List<String> availableAccountNames = new ArrayList<>();
                        for (BankReference ref : BankAPI.getApi().GetAllBankReferences(false)) {
                            try {
                                if (ref != null && ref.isValid() && ref.allowedAccess(player)) {
                                    IBankAccount account = ref.get();
                                    if (account != null) {
                                        availableAccounts.add(ref);
                                        availableAccountNames.add(account.getName().getString());
                                    }
                                }
                            } catch (RuntimeException ignored) {
                                // Skip stale or otherwise unresolvable account references.
                            }
                        }
                        
                        PacketDistributor.sendToPlayer(player, new S2C_SyncDetectorPaymentPacket(
                                pos,
                                detector.isActive(),
                                detector.getNextPaymentTime(),
                                detector.getPlacedTime(),
                                detector.getBankReference(),
                                availableAccounts,
                                availableAccountNames
                        ));
                    }
                }
            }
        });
    }
}
