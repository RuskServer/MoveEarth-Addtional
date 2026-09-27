package com.ruskserver.moveearth_addtional.client;

import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import com.ruskserver.moveearth_addtional.s2.siege.CoreSabotagePrompt;
import com.ruskserver.moveearth_addtional.s2.siege.CoreSabotagePrompt.Kind;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiEvent;

/** Vanilla-styled action prompt under the crosshair for core sabotage and defusal. */
@EventBusSubscriber(modid = Moveearth_addtional.MODID, value = Dist.CLIENT)
public final class SabotagePromptHud {
    private static final String KEY = "overlay.moveearth_addtional.sabotage.";
    private static final int BAR_WIDTH = 80;
    private static final int PLANT_COLOR = 0xFFFFFF55;
    private static final int DEFUSE_COLOR = 0xFF55FFFF;

    private SabotagePromptHud() { }

    @SubscribeEvent
    public static void render(RenderGuiEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.screen != null || minecraft.options.hideGui) return;
        if (!(minecraft.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) return;
        CoreSabotagePrompt prompt = WeldingTargetClientState.prompt(hit.getBlockPos());
        if (prompt.kind() == Kind.NONE) return;

        GuiGraphics graphics = event.getGuiGraphics();
        int centerX = graphics.guiWidth() / 2;
        // Below the crosshair and clear of the vanilla attack indicator.
        int y = graphics.guiHeight() / 2 + 16;
        graphics.drawCenteredString(minecraft.font, title(minecraft, prompt), centerX, y, 0xFFFFFFFF);
        y += 11;
        if (prompt.kind().hasProgress()) {
            int left = centerX - BAR_WIDTH / 2;
            graphics.fill(left - 1, y - 1, left + BAR_WIDTH + 1, y + 3, 0xC0000000);
            int filled = Math.round(BAR_WIDTH * prompt.progress());
            if (filled > 0) graphics.fill(left, y, left + filled, y + 2,
                    prompt.kind() == Kind.PLANTING ? PLANT_COLOR : DEFUSE_COLOR);
            y += 6;
        }
        Component detail = detail(prompt);
        if (detail != null) graphics.drawCenteredString(minecraft.font, detail, centerX, y, 0xFFFFFFFF);
    }

    private static Component title(Minecraft minecraft, CoreSabotagePrompt prompt) {
        return switch (prompt.kind()) {
            case PLANT -> Component.translatable(KEY + "plant", keys(minecraft));
            case DEFUSE -> Component.translatable(KEY + "defuse", keys(minecraft));
            case PLANT_NEEDS_TNT -> Component.translatable(KEY + "needs_tnt", prompt.tnt(),
                    CoreSabotagePrompt.REQUIRED_TNT).withStyle(ChatFormatting.GRAY);
            case ENEMY_PLANTING -> Component.translatable(KEY + "enemy_planting").withStyle(ChatFormatting.RED);
            case UNAVAILABLE, TOO_FAR -> Component.translatable(KEY + prompt.kind().name().toLowerCase(java.util.Locale.ROOT))
                    .withStyle(ChatFormatting.GRAY);
            default -> Component.translatable(KEY + prompt.kind().name().toLowerCase(java.util.Locale.ROOT));
        };
    }

    private static Component detail(CoreSabotagePrompt prompt) {
        return switch (prompt.kind()) {
            case PLANTING, ALLY_PLANTING, ENEMY_PLANTING ->
                    Component.translatable(KEY + "remaining", prompt.seconds()).withStyle(ChatFormatting.GRAY);
            case ARMED, DEFUSE, DEFUSE_NEEDS_TOOL, DEFUSING, ALLY_DEFUSING ->
                    Component.translatable(KEY + "fuse", prompt.seconds()).withStyle(ChatFormatting.RED);
            case TOO_FAR -> prompt.seconds() > 0
                    ? Component.translatable(KEY + "fuse", prompt.seconds()).withStyle(ChatFormatting.RED) : null;
            default -> null;
        };
    }

    /** Uses the player's own bindings so rebound sneak/use keys read correctly. */
    private static MutableComponent keys(Minecraft minecraft) {
        return Component.translatable(KEY + "keys", minecraft.options.keyShift.getTranslatedKeyMessage(),
                minecraft.options.keyUse.getTranslatedKeyMessage()).withStyle(ChatFormatting.YELLOW);
    }
}
