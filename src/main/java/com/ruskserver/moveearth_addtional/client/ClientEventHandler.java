package com.ruskserver.moveearth_addtional.client;

import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import com.ruskserver.moveearth_addtional.client.ui.SuppressesChatOverlay;
import com.ruskserver.moveearth_addtional.item.ModItems;
import com.ruskserver.moveearth_addtional.network.C2S_SetWeldingBrushPacket;
import com.ruskserver.moveearth_addtional.ui.MoveEarthMessage;
import net.minecraft.client.Minecraft;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid = Moveearth_addtional.MODID, bus = EventBusSubscriber.Bus.GAME, value = Dist.CLIENT)
public class ClientEventHandler {

    private static int tickCounter = 0;

    @SubscribeEvent
    public static void onItemTooltip(ItemTooltipEvent event) {
        if (event.getItemStack().is(ModItems.RESTRAINTS.get())) {
            Minecraft minecraft = Minecraft.getInstance();
            event.getToolTip().add(Component.translatable(
                    "tooltip.moveearth_addtional.restraints.capture",
                    minecraft.options.keyUse.getTranslatedKeyMessage()).withStyle(ChatFormatting.GRAY));
            event.getToolTip().add(Component.translatable(
                    "tooltip.moveearth_addtional.restraints.rescue",
                    minecraft.options.keyShift.getTranslatedKeyMessage(),
                    minecraft.options.keyUse.getTranslatedKeyMessage()).withStyle(ChatFormatting.GRAY));
            event.getToolTip().add(Component.translatable(
                    "tooltip.moveearth_addtional.restraints.rule").withStyle(ChatFormatting.DARK_GRAY));
        }
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        S2ClientKeys.clientTick();
        NationFoundationClientState.tick();
        tickCounter++;
        if (tickCounter >= 100) { // 5秒ごとに更新 (100 ticks)
            tickCounter = 0;
            DiscordRPCManager.update();
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void beforeGuiLayer(RenderGuiLayerEvent.Pre event) {
        if (event.getName().equals(VanillaGuiLayers.CHAT)
                && Minecraft.getInstance().screen instanceof SuppressesChatOverlay) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onMouseScroll(InputEvent.MouseScrollingEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen != null || minecraft.player == null || event.getScrollDeltaY() == 0.0D
                || !minecraft.player.getMainHandItem().is(ModItems.WELDING_TOOL.get())) return;
        event.setCanceled(true);
        int direction = event.getScrollDeltaY() > 0.0D ? 1 : -1;
        if (!WeldingBrushClientState.adjust(direction)) return;
        PacketDistributor.sendToServer(new C2S_SetWeldingBrushPacket(WeldingBrushClientState.radius()));
        minecraft.player.displayClientMessage(MoveEarthMessage.info(Component.translatable(
                "message.moveearth_addtional.welding.brush_changed",
                WeldingBrushClientState.size(), WeldingBrushClientState.size())), true);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onInteractionKey(InputEvent.InteractionKeyMappingTriggered event) {
        if (!NationFoundationClientState.active() || (!event.isAttack() && !event.isUseItem())) return;
        event.setCanceled(true);
        event.setSwingHand(false);
        NationFoundationClientState.handleInteraction(event.isAttack(), event.isUseItem());
    }

    @EventBusSubscriber(modid = Moveearth_addtional.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static class ModBusEvents {
        @SubscribeEvent
        public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
            S2ClientKeys.register(event);
        }

        @SubscribeEvent
        public static void onClientSetup(FMLClientSetupEvent event) {
            event.enqueueWork(DiscordRPCManager::init);
        }
    }
}
