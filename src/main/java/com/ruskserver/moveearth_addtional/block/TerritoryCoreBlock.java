package com.ruskserver.moveearth_addtional.block;

import com.ruskserver.moveearth_addtional.block.entity.TerritoryCoreBlockEntity;
import com.ruskserver.moveearth_addtional.network.S2C_OpenTerritoryCoreScreenPacket;
import com.ruskserver.moveearth_addtional.s2.S2Permission;
import com.ruskserver.moveearth_addtional.s2.nation.NationSavedData;
import com.ruskserver.moveearth_addtional.s2.territory.TerritorySavedData;
import com.ruskserver.moveearth_addtional.ui.MoveEarthMessage;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public final class TerritoryCoreBlock extends Block implements EntityBlock {
    public TerritoryCoreBlock(Properties properties) {
        super(properties);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TerritoryCoreBlockEntity(pos, state);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state,
                            @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide() && placer instanceof ServerPlayer player
                && level.getBlockEntity(pos) instanceof TerritoryCoreBlockEntity core) {
            NationSavedData data = NationSavedData.get(player.server);
            UUID nationId = data.nationIdFor(player.getUUID()).orElse(null);
            if (nationId == null) return;
            TerritorySavedData.RegistrationResult result = TerritorySavedData.get(player.server).register(
                    nationId, player.getUUID(), level.dimension().location(), pos, core.radius());
            if (result.success()) {
                core.bind(result.core());
                player.sendSystemMessage(MoveEarthMessage.success(Component.translatable(
                        result.core().type() == TerritorySavedData.CoreType.CAPITAL
                                ? "message.moveearth_addtional.territory_core.capital_registered"
                                : "message.moveearth_addtional.territory_core.outpost_registered")));
            } else {
                player.sendSystemMessage(MoveEarthMessage.error(Component.translatable(
                        "message.moveearth_addtional.territory_core.foreign_conflict")));
                level.destroyBlock(pos, true);
            }
        }
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos,
                            BlockState newState, boolean isMoving) {
        if (!state.is(newState.getBlock()) && level instanceof ServerLevel serverLevel
                && level.getBlockEntity(pos) instanceof TerritoryCoreBlockEntity core) {
            TerritorySavedData.get(serverLevel.getServer()).remove(
                    level.dimension().location(), pos, core.coreId());
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                                Player player, BlockHitResult hitResult) {
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        if (!(player instanceof ServerPlayer serverPlayer)
                || !(level.getBlockEntity(pos) instanceof TerritoryCoreBlockEntity core)) {
            return InteractionResult.CONSUME;
        }
        NationSavedData data = NationSavedData.get(serverPlayer.server);
        UUID playerNation = data.nationIdFor(player.getUUID()).orElse(null);
        if (playerNation == null || core.nationId() == null || !playerNation.equals(core.nationId())) {
            player.sendSystemMessage(MoveEarthMessage.error(Component.translatable(
                    "message.moveearth_addtional.territory_core.not_owner")));
            return InteractionResult.CONSUME;
        }
        if (!data.can(player.getUUID(), S2Permission.MANAGE_TERRITORY)) {
            player.sendSystemMessage(MoveEarthMessage.error(Component.translatable(
                    "message.moveearth_addtional.territory_core.no_permission")));
            return InteractionResult.CONSUME;
        }
        TerritorySavedData.CoreRecord record = TerritorySavedData.get(serverPlayer.server)
                .core(level.dimension().location(), pos).orElse(null);
        if (record != null) core.bind(record);
        PacketDistributor.sendToPlayer(serverPlayer,
                new S2C_OpenTerritoryCoreScreenPacket(pos,
                        record == null ? core.radius() : record.radius(),
                        record == null ? core.coreState() : record.state(),
                        record == null ? core.health() : record.health(),
                        record == null ? core.maximumHealth() : record.maximumHealth()));
        return InteractionResult.SUCCESS;
    }
}
