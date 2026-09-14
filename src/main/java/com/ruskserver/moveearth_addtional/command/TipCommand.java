package com.ruskserver.moveearth_addtional.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import com.ruskserver.moveearth_addtional.config.TipConfig;
import com.ruskserver.moveearth_addtional.s2.tip.TipCatalog;
import com.ruskserver.moveearth_addtional.s2.tip.TipSavedData;
import com.ruskserver.moveearth_addtional.s2.tip.TipService;
import com.ruskserver.moveearth_addtional.s2.tip.TipProgress;
import com.ruskserver.moveearth_addtional.ui.MoveEarthMessage;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Player controls and a compact browsable archive for the periodic tip system. */
@EventBusSubscriber(modid = Moveearth_addtional.MODID)
public final class TipCommand {
    private static final int PAGE_SIZE = 5;

    private TipCommand() { }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        register(event.getDispatcher());
    }

    private static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("tip")
                .requires(source -> source.hasPermission(0))
                .executes(context -> overview(context.getSource().getPlayerOrException()))
                .then(Commands.literal("on").executes(context -> setEnabled(
                        context.getSource().getPlayerOrException(), true)))
                .then(Commands.literal("off").executes(context -> setEnabled(
                        context.getSource().getPlayerOrException(), false)))
                .then(Commands.literal("history").executes(context -> history(
                        context.getSource().getPlayerOrException())))
                .then(Commands.literal("list")
                        .executes(context -> list(context.getSource().getPlayerOrException(), 1))
                        .then(Commands.argument("page", IntegerArgumentType.integer(1))
                                .executes(context -> list(context.getSource().getPlayerOrException(),
                                        IntegerArgumentType.getInteger(context, "page"))))));
    }

    private static int overview(ServerPlayer player) {
        TipProgress state = TipSavedData.get(player.server)
                .state(player.getUUID(), TipConfig.initialDelaySeconds());
        player.sendSystemMessage(MoveEarthMessage.tip(Component.translatable(
                state.enabled() ? "tip.moveearth_addtional.status.enabled"
                        : "tip.moveearth_addtional.status.disabled")));
        player.sendSystemMessage(Component.literal("  ")
                .append(TipService.commandButton("tip.moveearth_addtional.action.history",
                        ChatFormatting.GREEN, "/tip history"))
                .append(Component.literal(" "))
                .append(TipService.commandButton("tip.moveearth_addtional.action.list",
                        ChatFormatting.AQUA, "/tip list"))
                .append(Component.literal(" "))
                .append(TipService.commandButton(state.enabled()
                                ? "tip.moveearth_addtional.action.disable"
                                : "tip.moveearth_addtional.action.enable",
                        state.enabled() ? ChatFormatting.DARK_GRAY : ChatFormatting.GREEN,
                        state.enabled() ? "/tip off" : "/tip on")));
        return 1;
    }

    private static int setEnabled(ServerPlayer player, boolean enabled) {
        TipSavedData.get(player.server).setEnabled(player.getUUID(), enabled,
                TipConfig.initialDelaySeconds());
        player.sendSystemMessage(enabled
                ? MoveEarthMessage.success(Component.translatable("tip.moveearth_addtional.enabled"))
                : MoveEarthMessage.info(Component.translatable("tip.moveearth_addtional.disabled")));
        return 1;
    }

    private static int history(ServerPlayer player) {
        List<String> ids = new ArrayList<>(TipSavedData.get(player.server).history(player.getUUID()));
        if (ids.isEmpty()) {
            player.sendSystemMessage(MoveEarthMessage.info(Component.translatable(
                    "tip.moveearth_addtional.history.empty")));
            return 1;
        }
        Collections.reverse(ids);
        player.sendSystemMessage(MoveEarthMessage.tip(Component.translatable(
                "tip.moveearth_addtional.history.header")));
        ids.stream().limit(10).map(TipCatalog::byId).filter(java.util.Objects::nonNull)
                .forEach(tip -> player.sendSystemMessage(Component.literal("  • ")
                        .append(Component.translatable(tip.titleKey()).withStyle(ChatFormatting.GREEN))
                        .append(Component.literal(" — ").withStyle(ChatFormatting.DARK_GRAY))
                        .append(Component.translatable(tip.bodyKey()).withStyle(ChatFormatting.GRAY))));
        return ids.size();
    }

    private static int list(ServerPlayer player, int requestedPage) {
        int pages = Math.max(1, (TipCatalog.ALL.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        int page = Math.min(requestedPage, pages);
        int start = (page - 1) * PAGE_SIZE;
        int end = Math.min(start + PAGE_SIZE, TipCatalog.ALL.size());
        player.sendSystemMessage(MoveEarthMessage.tip(Component.translatable(
                "tip.moveearth_addtional.list.header", page, pages)));
        for (TipCatalog.Tip tip : TipCatalog.ALL.subList(start, end)) {
            player.sendSystemMessage(Component.literal("  • ")
                    .append(Component.translatable(tip.titleKey()).withStyle(ChatFormatting.GREEN))
                    .append(Component.literal(" — ").withStyle(ChatFormatting.DARK_GRAY))
                    .append(Component.translatable(tip.bodyKey()).withStyle(ChatFormatting.GRAY)));
        }
        if (page < pages) player.sendSystemMessage(Component.literal("  ").append(
                TipService.commandButton("tip.moveearth_addtional.action.next",
                        ChatFormatting.AQUA, "/tip list " + (page + 1))));
        return end - start;
    }
}
