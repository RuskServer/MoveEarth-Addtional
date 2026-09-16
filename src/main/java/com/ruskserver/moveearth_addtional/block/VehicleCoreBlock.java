package com.ruskserver.moveearth_addtional.block;

import com.ruskserver.moveearth_addtional.block.entity.VehicleCoreBlockEntity;
import com.ruskserver.moveearth_addtional.s2.S2Permission;
import com.ruskserver.moveearth_addtional.s2.nation.NationSavedData;
import com.ruskserver.moveearth_addtional.s2.territory.NationUpkeepService;
import com.ruskserver.moveearth_addtional.s2.territory.TerritorySavedData;
import com.ruskserver.moveearth_addtional.s2.vehicle.VehicleSavedData;
import com.ruskserver.moveearth_addtional.ui.MoveEarthMessage;
import com.ruskserver.moveearth_addtional.network.S2C_OpenVehicleCoreScreenPacket;
import com.ruskserver.moveearth_addtional.compat.vehicle.SableVehicleTopology;
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
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.UUID;
import com.ruskserver.moveearth_addtional.compat.vehicle.VehicleAssemblyGuard;

/** Nation-owned identity anchor for reinforced Sable vehicles. */
public final class VehicleCoreBlock extends Block implements EntityBlock {
    public VehicleCoreBlock(Properties properties) { super(properties); }

    @Nullable @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new VehicleCoreBlockEntity(pos, state);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state,
                            @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!(level instanceof ServerLevel serverLevel) || !(placer instanceof ServerPlayer player)
                || !(level.getBlockEntity(pos) instanceof VehicleCoreBlockEntity core)) return;
        NationSavedData nations = NationSavedData.get(player.server);
        UUID nationId = nations.nationIdFor(player.getUUID()).orElse(null);
        boolean allowed = nationId != null && nations.can(player.getUUID(), S2Permission.MANAGE_REINFORCEMENT)
                && TerritorySavedData.get(player.server).allowsReinforcement(player.server, nationId,
                level.dimension().location(), pos);
        if (!allowed) {
            player.sendSystemMessage(MoveEarthMessage.error(Component.translatable(
                    "message.moveearth_addtional.vehicle_core.requires_shipyard")));
            serverLevel.destroyBlock(pos, true);
            return;
        }
        VehicleSavedData.VehicleRecord record = VehicleSavedData.get(player.server).register(
                nationId, player.getUUID(), level.dimension().location(), pos);
        core.bind(record);
        player.sendSystemMessage(MoveEarthMessage.success(Component.translatable(
                "message.moveearth_addtional.vehicle_core.registered")));
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!isMoving && !VehicleAssemblyGuard.isMoving(pos) && !state.is(newState.getBlock())
                && level instanceof ServerLevel serverLevel
                && level.getBlockEntity(pos) instanceof VehicleCoreBlockEntity core) {
            VehicleSavedData.get(serverLevel.getServer()).remove(core.vehicleId());
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                                Player player, BlockHitResult hitResult) {
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        if (!(player instanceof ServerPlayer serverPlayer)
                || !(level.getBlockEntity(pos) instanceof VehicleCoreBlockEntity core)
                || core.vehicleId() == null) return InteractionResult.CONSUME;
        UUID playerNation = NationSavedData.get(serverPlayer.server).nationIdFor(player.getUUID()).orElse(null);
        if (!core.nationId().equals(playerNation)) {
            player.sendSystemMessage(MoveEarthMessage.error(Component.translatable(
                    "message.moveearth_addtional.vehicle_core.not_owner")));
            return InteractionResult.CONSUME;
        }
        VehicleSavedData.VehicleRecord vehicle = VehicleSavedData.get(serverPlayer.server)
                .vehicle(core.vehicleId()).orElse(null);
        if (vehicle != null) {
            var penalty = NationUpkeepService.penalty(serverPlayer.server, vehicle.nationId());
            var statistics = SableVehicleTopology.statistics(serverPlayer.serverLevel(), pos);
            String nationName = NationSavedData.get(serverPlayer.server).nation(vehicle.nationId())
                    .map(NationSavedData.Nation::name).orElse("?");
            PacketDistributor.sendToPlayer(serverPlayer, new S2C_OpenVehicleCoreScreenPacket(
                    nationName, vehicle.health(), vehicle.maximumHealth(), penalty.name(),
                    com.ruskserver.moveearth_addtional.config.S2TerritoryConfig.vehicleCoreCost(),
                    statistics.connectedBodies(), statistics.reinforcedBlocks()));
        }
        return InteractionResult.SUCCESS;
    }
}
