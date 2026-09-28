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

import java.util.List;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.AdvancementEvent;
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
            List<String> available = TipCatalog.availableIds(TipConfig.wikiConfigured());
            if (state.hasSeenAll(available)) {
                data.resetSeen(player.getUUID());
                state = data.state(player.getUUID(), TipConfig.initialDelaySeconds());
            }
            long entropy = player.getRandom().nextLong() ^ event.getServer().overworld().getGameTime();
            String selected = TipSelectionPolicy.select(available, state.seen(), state.previous(), entropy);
            TipCatalog.Tip tip = TipCatalog.byId(selected);
            if (tip == null) continue;
            show(player, tip);
            data.markShown(player.getUUID(), tip.id(), TipConfig.intervalSeconds(), TipConfig.historySize());
        }
    }

    /** Points a player who just reached an industry milestone at the next step ({@link TipAdvancementLinks}). */
    @SubscribeEvent
    public static void onAdvancementEarned(AdvancementEvent.AdvancementEarnEvent event) {
        if (!TipConfig.enabled() || !(event.getEntity() instanceof ServerPlayer player)) return;
        var id = event.getAdvancement().id();
        if (!Moveearth_addtional.MODID.equals(id.getNamespace())) return;
        String tipId = TipAdvancementLinks.nextTip(id.getPath());
        TipCatalog.Tip tip = tipId == null ? null : TipCatalog.byId(tipId);
        if (tip == null) return;
        TipSavedData data = TipSavedData.get(player.server);
        if (!data.state(player.getUUID(), TipConfig.initialDelaySeconds()).enabled() || shouldDefer(player)) return;
        show(player, tip);
        // Counts as the periodic tip too, so another does not follow right after.
        data.markShown(player.getUUID(), tip.id(), TipConfig.intervalSeconds(), TipConfig.historySize());
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
                .append(wikiButton(tip))
                .append(commandButton("tip.moveearth_addtional.action.history", ChatFormatting.GREEN,
                        "/tip history"))
                .append(Component.literal(" "))
                .append(commandButton("tip.moveearth_addtional.action.disable", ChatFormatting.DARK_GRAY,
                        "/tip off"));
        player.sendSystemMessage(actions);
    }

    /** A button opening the configured wiki for a wiki tip, or nothing. */
    public static Component wikiButton(TipCatalog.Tip tip) {
        String url = TipConfig.wikiUrl();
        if (!tip.wiki() || url.isEmpty()) return Component.empty();
        return Component.translatable("tip.moveearth_addtional.action.wiki").withStyle(style -> style
                        .withColor(ChatFormatting.AQUA)
                        .withBold(true)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, url)))
                .append(Component.literal(" ").withStyle(style -> style.withBold(false)));
    }

    public static Component commandButton(String translationKey, ChatFormatting color, String command) {
        return Component.translatable(translationKey).withStyle(style -> style
                .withColor(color)
                .withBold(true)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command)));
    }
}
