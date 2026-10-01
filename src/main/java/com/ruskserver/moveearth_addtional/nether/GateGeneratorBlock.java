package com.ruskserver.moveearth_addtional.nether;

import com.ruskserver.moveearth_addtional.s2.nation.NationSavedData;
import com.ruskserver.moveearth_addtional.s2.territory.TerritorySavedData;
import com.ruskserver.moveearth_addtional.ui.MoveEarthMessage;
import com.simibubi.create.content.kinetics.base.RotatedPillarKineticBlock;
import com.simibubi.create.foundation.block.IBE;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import java.util.UUID;

/**
 * A shaft-driven generator that opens a Nether gate once charged. It works only
 * in its placer's own national territory; anywhere else it breaks back into an
 * item on placement.
 */
public final class GateGeneratorBlock extends RotatedPillarKineticBlock implements IBE<GateGeneratorBlockEntity> {
    public GateGeneratorBlock(Properties properties) {
        super(properties);
    }

    @Override
    public Direction.Axis getRotationAxis(BlockState state) {
        return state.getValue(AXIS);
    }

    @Override
    public boolean hasShaftTowards(LevelReader level, BlockPos pos, BlockState state, Direction face) {
        return face.getAxis() == state.getValue(AXIS);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level.isClientSide) return;
        UUID nation = placer instanceof ServerPlayer player
                ? NationSavedData.get(player.server).nationIdFor(player.getUUID()).orElse(null) : null;
        UUID controlling = level.getServer() == null ? null : TerritorySavedData.get(level.getServer())
                .controllingNation(level.getServer(), level.dimension().location(), pos).orElse(null);
        if (nation == null || !nation.equals(controlling)) {
            if (placer instanceof ServerPlayer player) {
                player.sendSystemMessage(MoveEarthMessage.error(Component.translatable(
                        "message.moveearth_addtional.nether_gate.own_territory_only")));
            }
            level.destroyBlock(pos, true);
            return;
        }
        withBlockEntityDo(level, pos, generator -> generator.setOwnerNation(nation));
    }

    @Override
    public Class<GateGeneratorBlockEntity> getBlockEntityClass() {
        return GateGeneratorBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends GateGeneratorBlockEntity> getBlockEntityType() {
        return NetherGateRegistry.GATE_GENERATOR_ENTITY.get();
    }
}
