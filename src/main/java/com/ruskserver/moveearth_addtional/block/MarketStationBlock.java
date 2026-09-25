package com.ruskserver.moveearth_addtional.block;

import com.ruskserver.moveearth_addtional.block.entity.MarketStationBlockEntity;
import com.ruskserver.moveearth_addtional.economy.MarketStationSavedData;
import com.ruskserver.moveearth_addtional.s2.S2Permission;
import com.ruskserver.moveearth_addtional.s2.nation.NationSavedData;
import com.ruskserver.moveearth_addtional.s2.territory.TerritorySavedData;
import com.ruskserver.moveearth_addtional.ui.MoveEarthMessage;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/** Nation-scoped market hand-off point. Placement is server-authoritative. */
public final class MarketStationBlock extends Block implements EntityBlock {
    public MarketStationBlock(Properties properties) { super(properties); }

    @Nullable @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new MarketStationBlockEntity(pos, state);
    }

    @Override public void setPlacedBy(Level level, BlockPos pos, BlockState state,
                                      @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!(level instanceof ServerLevel serverLevel)) return;
        if (!(placer instanceof ServerPlayer player)
                || !(level.getBlockEntity(pos) instanceof MarketStationBlockEntity entity)) {
            level.removeBlock(pos, false);
            return;
        }
        NationSavedData nations = NationSavedData.get(player.server);
        UUID nation = nations.nationIdFor(player.getUUID()).orElse(null);
        boolean valid = nation != null && nations.can(player.getUUID(), S2Permission.MANAGE_TERRITORY)
                && TerritorySavedData.get(player.server).controlsChunk(player.server, nation,
                        level.dimension().location(), pos);
        MarketStationSavedData stations = MarketStationSavedData.get(player.server);
        if (!valid || !stations.register(new MarketStationSavedData.Station(UUID.randomUUID(), nation,
                level.dimension().location(), pos))) {
            player.sendSystemMessage(MoveEarthMessage.error(Component.literal(
                    "Market Stationは自国の稼働領土に、管理権限者が1国1台だけ設置できます")));
            level.destroyBlock(pos, true);
            return;
        }
        MarketStationSavedData.Station registered = stations.forNation(nation).orElseThrow();
        entity.bind(registered.id(), nation);
    }

    @Override protected void onRemove(BlockState state, Level level, BlockPos pos,
                                      BlockState replacement, boolean moving) {
        if (!state.is(replacement.getBlock()) && level instanceof ServerLevel serverLevel
                && level.getBlockEntity(pos) instanceof MarketStationBlockEntity entity
                && entity.nationId() != null && entity.stationId() != null) {
            MarketStationSavedData.get(serverLevel.getServer()).remove(entity.nationId(), entity.stationId());
        }
        super.onRemove(state, level, pos, replacement, moving);
    }

    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                                          Player player, BlockHitResult hit) {
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        if (player instanceof ServerPlayer serverPlayer
                && level.getBlockEntity(pos) instanceof MarketStationBlockEntity entity
                && entity.stationId() != null)
            com.ruskserver.moveearth_addtional.economy.MarketScreenSync.open(serverPlayer, entity.stationId());
        return InteractionResult.CONSUME;
    }
}
