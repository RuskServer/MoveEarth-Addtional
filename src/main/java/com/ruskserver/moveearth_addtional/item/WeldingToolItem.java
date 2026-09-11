package com.ruskserver.moveearth_addtional.item;

import com.ruskserver.moveearth_addtional.s2.reinforcement.ReinforcementService;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;

import java.util.List;

public final class WeldingToolItem extends Item {
    public WeldingToolItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (context.getLevel().isClientSide) return InteractionResult.SUCCESS;
        if (!(context.getPlayer() instanceof ServerPlayer player)) return InteractionResult.PASS;
        return ReinforcementService.apply(player, context.getClickedPos(), context.getClickedFace());
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context,
                                List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        tooltipComponents.add(Component.translatable("tooltip.moveearth_addtional.welding_tool"));
        tooltipComponents.add(Component.translatable("tooltip.moveearth_addtional.welding_tool.material"));
        tooltipComponents.add(Component.translatable("tooltip.moveearth_addtional.welding_tool.brush"));
        super.appendHoverText(stack, context, tooltipComponents, tooltipFlag);
    }
}
