package com.ruskserver.moveearth_addtional.block;

import com.ruskserver.moveearth_addtional.block.entity.ModBlockEntities;
import com.ruskserver.moveearth_addtional.block.entity.PlayerDetectorBlockEntity;
import com.ruskserver.moveearth_addtional.data.DetectorBlockPositionSavedData;
import com.ruskserver.moveearth_addtional.data.PlayerWhitelistSavedData;
import com.ruskserver.moveearth_addtional.network.S2C_OpenDetectorScreenPacket;
import com.ruskserver.moveearth_addtional.network.S2C_SyncDetectorPaymentPacket;
import io.github.lightman314.lightmanscurrency.api.money.bank.BankAPI;
import io.github.lightman314.lightmanscurrency.api.money.bank.IBankAccount;
import io.github.lightman314.lightmanscurrency.api.money.bank.reference.BankReference;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class PlayerDetectorBlock extends Block implements EntityBlock {

    public PlayerDetectorBlock(Properties properties) {
        super(properties);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new PlayerDetectorBlockEntity(pos, state);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide() && placer instanceof Player player) {
            BlockEntity blockEntity = level.getBlockEntity(pos);
            if (blockEntity instanceof PlayerDetectorBlockEntity detectorEntity) {
                detectorEntity.setOwner(player.getUUID(), player.getScoreboardName());
            }
            if (level instanceof ServerLevel serverLevel) {
                DetectorBlockPositionSavedData.get(serverLevel).addPosition(pos);
            }
        }
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!state.is(newState.getBlock())) {
            if (!level.isClientSide() && level instanceof ServerLevel serverLevel) {
                DetectorBlockPositionSavedData.get(serverLevel).removePosition(pos);
                BlockEntity blockEntity = level.getBlockEntity(pos);
                if (blockEntity instanceof PlayerDetectorBlockEntity detector) {
                    detector.onDestroy(serverLevel);
                }
            }
            super.onRemove(state, level, pos, newState, isMoving);
        }
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }

        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity instanceof PlayerDetectorBlockEntity detectorEntity) {
            if (detectorEntity.getOwnerUUID() != null) {
                if (detectorEntity.getOwnerUUID().equals(player.getUUID())) {
                    if (player instanceof ServerPlayer serverPlayer) {
                        // 所有者のホワイトリストを取得
                        ServerLevel serverLevel = (ServerLevel) level;
                        List<String> whitelist = PlayerWhitelistSavedData.get(serverLevel).getMemberNamesForDisplay(player.getUUID());

                        // 現在オンラインのプレイヤー一覧を取得
                        List<String> onlinePlayers = new ArrayList<>();
                        for (ServerPlayer onlinePlayer : serverLevel.getServer().getPlayerList().getPlayers()) {
                            onlinePlayers.add(onlinePlayer.getScoreboardName());
                        }

                        // クライアントへGUI表示パケットを送信
                        PacketDistributor.sendToPlayer(serverPlayer, new S2C_OpenDetectorScreenPacket(detectorEntity.getOwnerName(), whitelist, onlinePlayers));
                        
                        // 新規追加：決済データも取得して送信
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
                        PacketDistributor.sendToPlayer(serverPlayer, new S2C_SyncDetectorPaymentPacket(
                                pos,
                                detectorEntity.isActive(),
                                detectorEntity.getNextPaymentTime(),
                                detectorEntity.getPlacedTime(),
                                detectorEntity.getBankReference(),
                                availableAccounts,
                                availableAccountNames
                        ));
                    }
                    return InteractionResult.SUCCESS;
                } else {
                    player.sendSystemMessage(Component.literal("この検知ブロックの所有者ではありません。設定を変更することはできません。"));
                    return InteractionResult.CONSUME;
                }
            } else {
                // 所有者がいない場合は、最初に右クリックしたプレイヤーを所有者にする
                detectorEntity.setOwner(player.getUUID(), player.getScoreboardName());
                player.sendSystemMessage(Component.literal("このブロックの所有者として登録されました。もう一度右クリックして設定を開いてください。"));
                return InteractionResult.SUCCESS;
            }
        }

        return InteractionResult.PASS;
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> blockEntityType) {
        return level.isClientSide() ? null : createTickerHelper(blockEntityType, ModBlockEntities.PLAYER_DETECTOR.get(),
                (lvl, pos, st, blockEntity) -> blockEntity.tick(lvl, pos, st, blockEntity));
    }

    @Override
    public void appendHoverText(ItemStack stack, net.minecraft.world.item.Item.TooltipContext context, List<Component> tooltip, net.minecraft.world.item.TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        tooltip.add(Component.literal("§7周囲100ブロックに侵入したプレイヤーを検知します。"));
        tooltip.add(Component.literal("§7右クリックでホワイトリストの設定画面を開きます。"));
        tooltip.add(Component.literal("§c※ 半径3チャンク以内に重複して設置することはできません。"));
    }

    @SuppressWarnings("unchecked")
    @Nullable
    private static <E extends BlockEntity, A extends BlockEntity> BlockEntityTicker<A> createTickerHelper(
            BlockEntityType<A> actualType, BlockEntityType<E> expectedType, BlockEntityTicker<? super E> ticker) {
        return expectedType == actualType ? (BlockEntityTicker<A>) ticker : null;
    }
}
