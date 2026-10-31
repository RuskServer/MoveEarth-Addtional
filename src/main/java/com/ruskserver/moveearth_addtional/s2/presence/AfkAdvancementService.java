package com.ruskserver.moveearth_addtional.s2.presence;

import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import com.ruskserver.moveearth_addtional.advancement.ModCriteria;
import com.ruskserver.moveearth_addtional.analytics.activity.PlayerActivityTracker;
import com.ruskserver.moveearth_addtional.analytics.config.AnalyticsConfig;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Light-hearted hidden advancements for standing idle: 「浮気中」 after half an hour ("playing some other game,
 * maybe?"), 「地蔵」 after two hours, 「南無」 for dying while away, and 「寝落ち」 for being still away when
 * the server closes ({@link #closedOn}). Idle time is the analytics activity tracker's, so these need
 * analytics running, as it is on the live server.
 */
@EventBusSubscriber(modid = Moveearth_addtional.MODID)
public final class AfkAdvancementService {
    private static final int CHECK_TICKS = 20 * 30;

    private AfkAdvancementService() { }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (event.getServer().getTickCount() % CHECK_TICKS != 0) return;
        long now = System.currentTimeMillis();
        for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
            long idle = AfkMilestonePolicy.idleMillis(lastActive(player), now);
            if (idle >= AfkMilestonePolicy.STRAYED_MILLIS) ModCriteria.trigger(player, ModCriteria.AFK_STRAYED);
            if (idle >= AfkMilestonePolicy.STATUE_MILLIS) ModCriteria.trigger(player, ModCriteria.AFK_STATUE);
        }
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && away(player)) {
            ModCriteria.trigger(player, ModCriteria.AFK_DIED);
        }
    }

    /** Called just before the closing-time disconnect of each player. */
    public static void closedOn(ServerPlayer player) {
        if (away(player)) ModCriteria.trigger(player, ModCriteria.AFK_ASLEEP);
    }

    private static boolean away(ServerPlayer player) {
        return AfkMilestonePolicy.away(lastActive(player), System.currentTimeMillis(), AnalyticsConfig.AFK_THRESHOLD_MS);
    }

    private static long lastActive(ServerPlayer player) {
        return PlayerActivityTracker.INSTANCE.getLastActiveTimeMs(player.getUUID());
    }
}
