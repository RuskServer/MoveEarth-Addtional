package com.ruskserver.moveearth_addtional.compat.mekanism;

import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import com.ruskserver.moveearth_addtional.ui.MoveEarthMessage;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/** Server-authoritative guard for Mekanism's alloy-on-transmitter shortcut. */
@EventBusSubscriber(modid = Moveearth_addtional.MODID)
public final class MekanismCableUpgradeEvents {
    private MekanismCableUpgradeEvents() {
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.isCanceled()) {
            return;
        }

        ItemStack held = event.getItemStack();
        if (held.isEmpty()) {
            return;
        }
        BlockState clicked = event.getLevel().getBlockState(event.getPos());
        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(held.getItem());
        ResourceLocation blockId = BuiltInRegistries.BLOCK.getKey(clicked.getBlock());
        if (!MekanismCableUpgradePolicy.shouldBlock(
                itemId.getNamespace(), itemId.getPath(),
                blockId.getNamespace(), blockId.getPath())) {
            return;
        }

        // Cancel on both logical sides so the client does not briefly predict an
        // upgrade that the authoritative server will reject.
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.FAIL);
        if (event.getEntity() instanceof ServerPlayer player
                && !player.getCooldowns().isOnCooldown(held.getItem())) {
            player.getCooldowns().addCooldown(held.getItem(), 20);
            player.sendSystemMessage(MoveEarthMessage.error(Component.translatable(
                    "message.moveearth_addtional.mekanism.cable_upgrade_blocked")));
        }
    }
}
