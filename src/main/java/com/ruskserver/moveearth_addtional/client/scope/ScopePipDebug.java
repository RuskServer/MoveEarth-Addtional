package com.ruskserver.moveearth_addtional.client.scope;

import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import com.ruskserver.moveearth_addtional.ui.MoveEarthMessage;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;

@EventBusSubscriber(modid = Moveearth_addtional.MODID, value = Dist.CLIENT)
public final class ScopePipDebug {
    private ScopePipDebug() { }

    @SubscribeEvent
    public static void commands(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("moveearthpip")
                .then(Commands.literal("on").executes(context -> {
                    ScopePipRenderer.retry();
                    ScopePipConfig.ENABLED.set(true);
                    ScopePipConfig.SPEC.save();
                    context.getSource().sendSuccess(() -> MoveEarthMessage.info(
                            Component.translatable("scope_pip.moveearth_addtional.enabled")), false);
                    return 1;
                }))
                .then(Commands.literal("off").executes(context -> {
                    ScopePipConfig.ENABLED.set(false);
                    ScopePipConfig.SPEC.save();
                    context.getSource().sendSuccess(() -> MoveEarthMessage.info(
                            Component.translatable("scope_pip.moveearth_addtional.disabled")), false);
                    return 1;
                }))
                .then(Commands.literal("shaders").executes(context -> {
                    boolean enabled = !ScopePipConfig.IRIS_EXPERIMENTAL.get();
                    ScopePipConfig.IRIS_EXPERIMENTAL.set(enabled);
                    ScopePipConfig.SPEC.save();
                    context.getSource().sendSuccess(() -> MoveEarthMessage.warning(Component.translatable(
                            "scope_pip.moveearth_addtional.shaders", enabled ? "ON" : "OFF")), false);
                    return 1;
                }))
                .then(Commands.literal("debug").executes(context -> {
                    boolean enabled = !ScopePipConfig.DEBUG.get();
                    ScopePipConfig.DEBUG.set(enabled);
                    ScopePipConfig.SPEC.save();
                    context.getSource().sendSuccess(() -> MoveEarthMessage.info(Component.translatable(
                            "scope_pip.moveearth_addtional.debug", enabled ? "ON" : "OFF")), false);
                    return 1;
                })));
    }

    @SubscribeEvent
    public static void overlay(RenderGuiEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!ScopePipConfig.DEBUG.get() || minecraft.player == null || minecraft.screen != null
                || minecraft.options.hideGui) return;
        event.getGuiGraphics().drawString(minecraft.font, ScopePipRenderer.diagnostic(), 8, 8, 0xFFFFFF, true);
    }
}
