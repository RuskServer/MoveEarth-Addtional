package com.ruskserver.moveearth_addtional.oxygen;

import com.ruskserver.moveearth_addtional.ModSounds;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

public class CarbonFilterItem extends Item {
    public CarbonFilterItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack heldFilter = player.getItemInHand(hand);
        ItemStack headArmor = player.getItemBySlot(EquipmentSlot.HEAD);

        if (headArmor.getItem() instanceof GasMaskItem) {
            int currentTicks = GasMaskItem.getFilterTicks(headArmor);
            int maxTicks = GasMaskItem.getMaxFilterTicks();

            if (currentTicks >= maxTicks) {
                if (!level.isClientSide) {
                    player.displayClientMessage(Component.translatable("message.moveearth_addtional.filter_already_full")
                            .withStyle(ChatFormatting.YELLOW), true);
                }
                return InteractionResultHolder.fail(heldFilter);
            }

            // フィルター交換実行
            if (!level.isClientSide) {
                GasMaskItem.setFilterTicks(headArmor, maxTicks);
                level.playSound(null, player.getX(), player.getY(), player.getZ(),
                        ModSounds.FILTER_REPLACE.get(), SoundSource.PLAYERS, 1.0f, 1.0f);
                player.displayClientMessage(Component.translatable("message.moveearth_addtional.filter_replaced")
                        .withStyle(ChatFormatting.GREEN), true);
            }

            if (!player.getAbilities().instabuild) {
                heldFilter.shrink(1);
            }

            return InteractionResultHolder.sidedSuccess(heldFilter, level.isClientSide);
        } else {
            if (!level.isClientSide) {
                player.displayClientMessage(Component.translatable("message.moveearth_addtional.no_gas_mask_equipped")
                        .withStyle(ChatFormatting.RED), true);
            }
            return InteractionResultHolder.fail(heldFilter);
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        tooltipComponents.add(Component.translatable("tooltip.moveearth_addtional.carbon_filter.desc")
                .withStyle(ChatFormatting.GRAY));
        super.appendHoverText(stack, context, tooltipComponents, tooltipFlag);
    }
}
