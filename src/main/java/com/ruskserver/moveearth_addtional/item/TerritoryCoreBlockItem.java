package com.ruskserver.moveearth_addtional.item;

import com.ruskserver.moveearth_addtional.s2.S2Permission;
import com.ruskserver.moveearth_addtional.s2.nation.NationSavedData;
import com.ruskserver.moveearth_addtional.ui.MoveEarthMessage;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Block;

public final class TerritoryCoreBlockItem extends BlockItem {
    public TerritoryCoreBlockItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (context.getLevel().isClientSide()) return InteractionResult.SUCCESS;
        if (!(context.getPlayer() instanceof ServerPlayer player)) return InteractionResult.FAIL;
        NationSavedData data = NationSavedData.get(player.server);
        if (!data.can(player.getUUID(), S2Permission.MANAGE_TERRITORY)) {
            player.sendSystemMessage(MoveEarthMessage.error(Component.translatable(
                    data.nationFor(player.getUUID()).isEmpty()
                            ? "message.moveearth_addtional.territory_core.nation_required"
                            : "message.moveearth_addtional.territory_core.no_permission")));
            return InteractionResult.FAIL;
        }
        return super.useOn(context);
    }
}
