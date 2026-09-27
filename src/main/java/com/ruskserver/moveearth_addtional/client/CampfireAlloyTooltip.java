package com.ruskserver.moveearth_addtional.client;

import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import com.ruskserver.moveearth_addtional.compat.create.CampfireAlloying;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

/** Tells players that campfire alloys can be mixed over a campfire; recipe viewers still show "heated". */
@EventBusSubscriber(modid = Moveearth_addtional.MODID, value = Dist.CLIENT)
public final class CampfireAlloyTooltip {
    private CampfireAlloyTooltip() { }

    @SubscribeEvent
    public static void onItemTooltip(ItemTooltipEvent event) {
        if (!event.getItemStack().is(CampfireAlloying.CAMPFIRE_ALLOYS)) return;
        event.getToolTip().add(Component.translatable("tooltip.moveearth_addtional.campfire_alloy")
                .withStyle(ChatFormatting.GOLD));
    }
}
