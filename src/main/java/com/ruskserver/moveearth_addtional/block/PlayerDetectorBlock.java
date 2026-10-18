package com.ruskserver.moveearth_addtional.block;

import com.ruskserver.moveearth_addtional.block.entity.ModBlockEntities;
import com.ruskserver.moveearth_addtional.block.entity.PlayerDetectorBlockEntity;
import com.ruskserver.moveearth_addtional.data.DetectorBlockPositionSavedData;
import com.ruskserver.moveearth_addtional.detector.DetectorOwnership;
import com.ruskserver.moveearth_addtional.detector.DetectorScreenSync;
import com.ruskserver.moveearth_addtional.s2.nation.NationSavedData;
import com.ruskserver.moveearth_addtional.ui.MoveEarthMessage;
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

import java.util.List;
import java.util.Set;
import java.util.UUID;

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
        if (level instanceof ServerLevel serverLevel) {
            BlockEntity blockEntity = level.getBlockEntity(pos);
            if (blockEntity instanceof PlayerDetectorBlockEntity detectorEntity) {
                if (placer instanceof Player player) {
                    detectorEntity.setOwner(player.getUUID(), player.getScoreboardName());
                    NationSavedData.get(serverLevel.getServer()).nationIdFor(player.getUUID())
                            .ifPresent(nation -> detectorEntity.setOwnerNation(nation, true));
                }
                detectorEntity.ensurePositionRegistered(serverLevel);
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
            if (!detectorEntity.ensurePositionRegistered((ServerLevel) level)) {
                player.sendSystemMessage(MoveEarthMessage.error("近くに別の検知ブロックがあるため、このブロックは作動できません。"));
                return InteractionResult.CONSUME;
            }
            if (!(player instanceof ServerPlayer serverPlayer)) {
                return InteractionResult.CONSUME;
            }
            // 解散した国の検知ブロックはここで所有者なしに戻り、そのまま登録し直せる
            DetectorOwnership.settle(serverPlayer.server, detectorEntity);
            if (detectorEntity.getOwnerUUID() == null && detectorEntity.getOwnerNation() == null) {
                // 所有者がいない場合は、最初に右クリックしたプレイヤー（国民なら国家）を所有者にする
                if (!DetectorOwnership.mayPlace(serverPlayer)) {
                    player.sendSystemMessage(MoveEarthMessage.error(
                            "国家の検知ブロックにするには、拠点操作か国庫管理の権限が必要です。"));
                    return InteractionResult.CONSUME;
                }
                detectorEntity.setOwner(player.getUUID(), player.getScoreboardName());
                DetectorOwnership.settle(serverPlayer.server, detectorEntity);
                // Claiming takes the rights mayPlace checked, so the claimer agrees to the treasury paying.
                if (detectorEntity.getOwnerNation() != null) detectorEntity.approveTreasury();
                player.sendSystemMessage(MoveEarthMessage.success(detectorEntity.getOwnerNation() != null
                        ? "このブロックを国家の検知ブロックとして登録しました。もう一度右クリックして設定を開いてください。"
                        : "このブロックの所有者として登録されました。もう一度右クリックして設定を開いてください。"));
                return InteractionResult.SUCCESS;
            }

            if (!DetectorOwnership.mayView(serverPlayer, detectorEntity)) {
                player.sendSystemMessage(MoveEarthMessage.error("この検知ブロックの設定を見る権限がありません。"));
                return InteractionResult.CONSUME;
            }
            DetectorScreenSync.open(serverPlayer, detectorEntity);
            return InteractionResult.SUCCESS;
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
        tooltip.add(Component.literal("§7国民が置くと国家の物になり、維持費（"
                + PlayerDetectorBlockEntity.UPKEEP_CYCLE_HOURS + "時間ごとに"
                + PlayerDetectorBlockEntity.UPKEEP_COST + " TC）は国庫から払われます。"));
        tooltip.add(Component.literal("§7国民全員に侵入警告が届き、同盟国の人は警告しません。"));
        tooltip.add(Component.literal("§c※ 半径3チャンク以内に重複して設置することはできません。"));
    }

    @SuppressWarnings("unchecked")
    @Nullable
    private static <E extends BlockEntity, A extends BlockEntity> BlockEntityTicker<A> createTickerHelper(
            BlockEntityType<A> actualType, BlockEntityType<E> expectedType, BlockEntityTicker<? super E> ticker) {
        return expectedType == actualType ? (BlockEntityTicker<A>) ticker : null;
    }
}
