package com.ruskserver.moveearth_addtional.client;

import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import com.ruskserver.moveearth_addtional.network.s2c.other.S2C_TutorialPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;

import java.util.List;

/**
 * The current tutorial goal, drawn under the balance without a background as a
 * left-aligned block against the right edge: progress, the goal with its item, one line of how, and always the
 * way to skip. {@link EconomyWaypointHud} places it in its column.
 */
@EventBusSubscriber(modid = Moveearth_addtional.MODID, value = Dist.CLIENT)
public final class TutorialHud {
    private static final String KEY = "tutorial.moveearth_addtional.";
    private static final int DETAIL_WIDTH = 190;
    /** Item icon plus the gap before the goal text. */
    private static final int ICON_SIZE = 18;
    private static S2C_TutorialPacket state = S2C_TutorialPacket.hidden();
    private static ItemStack icon = ItemStack.EMPTY;

    private TutorialHud() { }

    public static void update(S2C_TutorialPacket packet) {
        state = packet;
        ResourceLocation id = packet.icon().isEmpty() ? null : ResourceLocation.tryParse(packet.icon());
        icon = id == null ? ItemStack.EMPTY
                : BuiltInRegistries.ITEM.getOptional(id).map(ItemStack::new).orElse(ItemStack.EMPTY);
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        update(S2C_TutorialPacket.hidden());
    }

    /**
     * Draws the goal from {@code y} down as one block whose widest line ends at
     * {@code right}; every line starts at the block's left edge. Returns the y below it.
     */
    static int render(GuiGraphics graphics, Minecraft minecraft, int right, int y) {
        if (!state.visible()) return y;
        Font font = minecraft.font;
        Component hubKey = Component.keybind("key.moveearth_addtional.s2_hub");
        Component header = Component.translatable(KEY + "header", state.step() + 1, state.total());
        Component goal = Component.translatable(KEY + state.textId() + ".title", hubKey);
        List<FormattedCharSequence> detail = font.split(
                Component.translatable(KEY + state.textId() + ".detail", hubKey), DETAIL_WIDTH);
        Component skip = Component.translatable(KEY + "skip_hint");
        int goalIndent = icon.isEmpty() ? 0 : ICON_SIZE;

        int width = Math.max(font.width(header), goalIndent + font.width(goal));
        for (FormattedCharSequence line : detail) width = Math.max(width, font.width(line));
        width = Math.max(width, font.width(skip));
        int left = right - width;

        graphics.drawString(font, header, left, y, 0xFF9AD5D1, true);
        y += 11;
        if (icon.isEmpty()) {
            graphics.drawString(font, goal, left, y, 0xFFFFFFFF, true);
            y += 11;
        } else {
            graphics.renderItem(icon, left, y);
            graphics.drawString(font, goal, left + goalIndent, y + 4, 0xFFFFFFFF, true);
            y += 18;
        }
        for (FormattedCharSequence line : detail) {
            graphics.drawString(font, line, left, y, 0xFFB9C2C7, true);
            y += 10;
        }
        graphics.drawString(font, skip, left, y + 1, 0xFF7D878D, true);
        return y + 15;
    }
}
