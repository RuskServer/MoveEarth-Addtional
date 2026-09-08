package com.ruskserver.moveearth_addtional.analytics.collector;

import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import com.ruskserver.moveearth_addtional.analytics.storage.AnalyticsStorageService;
import net.neoforged.neoforge.event.server.ServerStartingEvent;

/**
 * プレイヤー分析用ゲームイベントリスナー
 */
@EventBusSubscriber(modid = Moveearth_addtional.MODID, bus = EventBusSubscriber.Bus.GAME)
public class AnalyticsGameEvents {

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onServerStarting(ServerStartingEvent event) {
        AnalyticsStorageService.INSTANCE.start(event.getServer());
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!AnalyticsStorageService.INSTANCE.isRunning()) return;
        if (event.getEntity() instanceof ServerPlayer player) {
            AnalyticsCollectorManager.INSTANCE.onPlayerLogin(player, System.currentTimeMillis());
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!AnalyticsStorageService.INSTANCE.isRunning()) return;
        if (event.getEntity() instanceof ServerPlayer player) {
            AnalyticsCollectorManager.INSTANCE.onPlayerLogout(player, System.currentTimeMillis());
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (!AnalyticsStorageService.INSTANCE.isRunning()) return;
        if (!event.isCanceled() && event.getPlayer() instanceof ServerPlayer player) {
            AnalyticsCollectorManager.INSTANCE.recordBlockBreak(player);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
        if (!AnalyticsStorageService.INSTANCE.isRunning()) return;
        if (!event.isCanceled() && event.getEntity() instanceof ServerPlayer player) {
            AnalyticsCollectorManager.INSTANCE.recordBlockPlace(player);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onItemCrafted(PlayerEvent.ItemCraftedEvent event) {
        if (!AnalyticsStorageService.INSTANCE.isRunning()) return;
        if (event.getEntity() instanceof ServerPlayer player) {
            AnalyticsCollectorManager.INSTANCE.recordCraft(player);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingDeath(LivingDeathEvent event) {
        if (!AnalyticsStorageService.INSTANCE.isRunning()) return;
        if (event.isCanceled()) {
            return;
        }

        // 被害者判定
        if (event.getEntity() instanceof ServerPlayer victim) {
            AnalyticsCollectorManager.INSTANCE.recordDeath(victim);
        }

        // 加害者（キル）判定
        if (event.getSource().getEntity() instanceof ServerPlayer killer) {
            boolean isPvpVictim = event.getEntity() instanceof ServerPlayer;
            AnalyticsCollectorManager.INSTANCE.recordKill(killer, isPvpVictim);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onServerTick(ServerTickEvent.Post event) {
        if (!AnalyticsStorageService.INSTANCE.isRunning()) return;
        long gameTime = event.getServer().overworld().getGameTime();
        AnalyticsCollectorManager.INSTANCE.onServerTick(event.getServer(), gameTime);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onServerStopping(ServerStoppingEvent event) {
        if (!AnalyticsStorageService.INSTANCE.isRunning()) return;
        long now = System.currentTimeMillis();
        for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
            AnalyticsCollectorManager.INSTANCE.onPlayerLogout(player, now);
        }
        AnalyticsCollectorManager.INSTANCE.onServerStopping(event.getServer());
        AnalyticsStorageService.INSTANCE.stop(5000L); // 5秒タイムアウトでフラッシュ・クローズ
    }
}
