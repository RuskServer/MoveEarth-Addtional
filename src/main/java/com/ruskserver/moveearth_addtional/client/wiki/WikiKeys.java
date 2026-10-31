package com.ruskserver.moveearth_addtional.client.wiki;

import com.mojang.blaze3d.platform.InputConstants;
import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/**
 * The wiki key: in the world it opens the wiki (at the held item's page when one explains it); over an item
 * in an inventory or in JEI it opens that item's page, the way Ponder answers its key. Items with a page
 * say so in their tooltip.
 */
@EventBusSubscriber(modid = Moveearth_addtional.MODID, value = Dist.CLIENT)
public final class WikiKeys {
    public static final KeyMapping OPEN_WIKI = new KeyMapping("key.moveearth_addtional.wiki",
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_K, "key.categories.moveearth_addtional");

    private WikiKeys() { }

    /** Called from the client tick with the other MoveEarth keys. */
    public static void clientTick() {
        Minecraft minecraft = Minecraft.getInstance();
        while (OPEN_WIKI.consumeClick()) {
            if (minecraft.player == null || minecraft.screen != null) continue;
            if (!WikiScreen.openFor(minecraft.player.getMainHandItem(), true)) WikiScreen.open();
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onScreenKey(ScreenEvent.KeyPressed.Pre event) {
        Screen screen = event.getScreen();
        if (screen instanceof WikiScreen || !OPEN_WIKI.isActiveAndMatches(InputConstants.getKey(event.getKeyCode(),
                event.getScanCode()))) return;
        // A key typed into a search or name box is text, not a command. The creative inventory's search tab
        // takes every letter without focusing its box, so the key is left to it there.
        if (screen.getFocused() instanceof EditBox box && box.isFocused()) return;
        if (screen instanceof net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen) return;
        if (jeiLoaded() && com.ruskserver.moveearth_addtional.compat.jei.JeiWikiBridge.typing()) return;
        ItemStack hovered = hoveredItem(screen);
        if (!hovered.isEmpty() && WikiScreen.openFor(hovered, false)) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onTooltip(ItemTooltipEvent event) {
        if (Minecraft.getInstance().screen == null || event.getItemStack().isEmpty()) return;
        List<String> pages = WikiLibrary.get().pagesFor(event.getItemStack());
        if (pages.isEmpty()) return;
        String title = WikiLibrary.get().page(pages.getFirst()).map(WikiLibrary.Page::title).orElse("");
        event.getToolTip().add(Component.translatable("tooltip.moveearth_addtional.wiki",
                OPEN_WIKI.getTranslatedKeyMessage(), title).withStyle(ChatFormatting.DARK_GRAY));
    }

    private static ItemStack hoveredItem(Screen screen) {
        if (screen instanceof AbstractContainerScreen<?> container) {
            Slot slot = container.getSlotUnderMouse();
            if (slot != null && slot.hasItem()) return slot.getItem();
        }
        return jeiLoaded() ? com.ruskserver.moveearth_addtional.compat.jei.JeiWikiBridge.hoveredItem() : ItemStack.EMPTY;
    }

    private static boolean jeiLoaded() {
        return ModList.get().isLoaded("jei");
    }

    @EventBusSubscriber(modid = Moveearth_addtional.MODID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
    public static final class Reload {
        private Reload() { }

        @SubscribeEvent
        public static void onRegisterReloadListeners(RegisterClientReloadListenersEvent event) {
            event.registerReloadListener((ResourceManagerReloadListener) resources -> WikiLibrary.invalidate());
        }
    }
}
