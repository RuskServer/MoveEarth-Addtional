package com.ruskserver.moveearth_addtional.s2.presence;

import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import com.ruskserver.moveearth_addtional.advancement.ModCriteria;
import com.ruskserver.moveearth_addtional.s2.nation.NationSavedData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * A light-hearted advancement, 「生きてたんか」 ("You're Alive?!"), for logging in again after two days or more away. The
 * last time each player was on is kept with the player, written at logout and every few minutes while
 * online so a server crash does not lose it. A player with no record yet falls back to their nation's
 * last-seen time, so the first return after this was added still counts.
 */
@EventBusSubscriber(modid = Moveearth_addtional.MODID)
public final class ReturnAfterBreakService {
    private static final String NBT_LAST_SEEN = "MoveEarthLastSeenMillis";
    private static final int REFRESH_TICKS = 20 * 60 * 5;

    private ReturnAfterBreakService() { }

    /** Before anything else on login refreshes the nation's last-seen time. */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        long now = System.currentTimeMillis();
        CompoundTag data = persisted(player);
        long lastSeen = data.contains(NBT_LAST_SEEN, Tag.TAG_LONG) ? data.getLong(NBT_LAST_SEEN) : nationLastSeen(player);
        if (ReturnAfterBreakPolicy.returned(lastSeen, now)) ModCriteria.trigger(player, ModCriteria.RETURNED_AFTER_BREAK);
        data.putLong(NBT_LAST_SEEN, now);
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) persisted(player).putLong(NBT_LAST_SEEN, System.currentTimeMillis());
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player && player.tickCount % REFRESH_TICKS == 0) {
            persisted(player).putLong(NBT_LAST_SEEN, System.currentTimeMillis());
        }
    }

    private static long nationLastSeen(ServerPlayer player) {
        NationSavedData nations = NationSavedData.get(player.server);
        return nations.nationFor(player.getUUID())
                .map(nation -> nation.members().get(player.getUUID()))
                .map(NationSavedData.Member::lastSeenAt)
                .orElse(0L);
    }

    private static CompoundTag persisted(ServerPlayer player) {
        CompoundTag root = player.getPersistentData();
        if (!root.contains(Player.PERSISTED_NBT_TAG, Tag.TAG_COMPOUND)) root.put(Player.PERSISTED_NBT_TAG, new CompoundTag());
        return root.getCompound(Player.PERSISTED_NBT_TAG);
    }
}
