package com.ruskserver.moveearth_addtional.s2.tip;

import com.ruskserver.moveearth_addtional.CompatEventHandler;
import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import com.ruskserver.moveearth_addtional.config.TipConfig;
import com.ruskserver.moveearth_addtional.s2.combat.CombatTagService;
import com.ruskserver.moveearth_addtional.s2.siege.PrisonerSavedData;
import com.ruskserver.moveearth_addtional.ui.MoveEarthMessage;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Periodic, unread-first tips that defer while a player is in active danger. */
@EventBusSubscriber(modid = Moveearth_addtional.MODID)
public final class TipService {
    private TipService() { }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (!TipConfig.enabled() || event.getServer().overworld().getGameTime() % 20L != 13L) return;
        TipSavedData data = TipSavedData.get(event.getServer());
        for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
            TipProgress state = data.state(player.getUUID(), TipConfig.initialDelaySeconds());
            if (!state.enabled()) continue;
            if (state.remainingSeconds() > 0) {
                data.advanceSecond(player.getUUID(), TipConfig.initialDelaySeconds());
                continue;
            }
            if (shouldDefer(player)) continue;
            if (state.hasSeenAll(TipCatalog.IDS)) {
                data.resetSeen(player.getUUID());
                state = data.state(player.getUUID(), TipConfig.initialDelaySeconds());
            }
            long entropy = player.getRandom().nextLong() ^ event.getServer().overworld().getGameTime();
            String selected = TipSelectionPolicy.select(TipCatalog.IDS, state.seen(), state.previous(), entropy);
            TipCatalog.Tip tip = TipCatalog.byId(selected);
            if (tip == null) continue;
            show(player, tip);
            data.markShown(player.getUUID(), tip.id(), TipConfig.intervalSeconds(), TipConfig.historySize());
        }
    }

    private static boolean shouldDefer(ServerPlayer player) {
        if (CombatTagService.isTagged(player) || CompatEventHandler.isPlayerDown(player)) return true;
        PrisonerSavedData prisoners = PrisonerSavedData.get(player.server);
        return prisoners.custody(player.getUUID()).isPresent()
                || prisoners.prisoner(player.getUUID()).isPresent();
    }

    public static void show(ServerPlayer player, TipCatalog.Tip tip) {
        player.sendSystemMessage(MoveEarthMessage.tip(Component.translatable(tip.titleKey())));
        Component actions = Component.literal("  ")
                .append(Component.translatable(tip.bodyKey()).withStyle(ChatFormatting.GRAY))
                .append(Component.literal("  "))
                .append(commandButton("tip.moveearth_addtional.action.history", ChatFormatting.GREEN,
                        "/tip history"))
                .append(Component.literal(" "))
                .append(commandButton("tip.moveearth_addtional.action.disable", ChatFormatting.DARK_GRAY,
                        "/tip off"));
        player.sendSystemMessage(actions);
    }

    public static Component commandButton(String translationKey, ChatFormatting color, String command) {
        return Component.translatable(translationKey).withStyle(style -> style
                .withColor(color)
                .withBold(true)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command)));
    }
}
