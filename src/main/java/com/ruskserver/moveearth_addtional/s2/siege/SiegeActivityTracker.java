package com.ruskserver.moveearth_addtional.s2.siege;

import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import com.ruskserver.moveearth_addtional.config.S2TerritoryConfig;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * When each player last did something that counts as pressing a siege.
 *
 * <p>Standing in the defender's territory stops the siege clock, and standing
 * is the entire cost of that. One attacker who lands a single hit and then
 * walks inside can hold two nations in a war for the whole hold budget without
 * touching anything again, and a player left logged in overnight would do it
 * without being at the keyboard. Neither is a siege.
 *
 * <p>Breaking a block counts, which is the point. The reason the clock stops at
 * all is that crossing ground made deliberately hard to cross takes longer than
 * the clock allows, and crossing it means digging -- so a tunneller stays
 * counted while someone standing still does not. Taking or dealing damage
 * counts for the same reason: a fight has lulls, and a defender pinning the
 * attackers down is exactly the situation the hold exists to cover.
 *
 * <p>Deliberately not the analytics AFK check. That reads movement, so it is
 * answered by walking in a circle; this asks for work.
 */
@EventBusSubscriber(modid = Moveearth_addtional.MODID, bus = EventBusSubscriber.Bus.GAME)
public final class SiegeActivityTracker {

    /** Last game time, per player, at which they did something. */
    private static final Map<UUID, Long> LAST_ACTIVE = new HashMap<>();

    private SiegeActivityTracker() { }

    @SubscribeEvent
    public static void onBlockBroken(BlockEvent.BreakEvent event) {
        if (event.getPlayer() instanceof ServerPlayer player) {
            record(player);
        }
    }

    @SubscribeEvent
    public static void onDamage(LivingDamageEvent.Post event) {
        LivingEntity hurt = event.getEntity();
        if (hurt instanceof ServerPlayer player) {
            record(player);
        }
        if (event.getSource().getEntity() instanceof ServerPlayer attacker) {
            record(attacker);
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            LAST_ACTIVE.remove(player.getUUID());
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        LAST_ACTIVE.clear();
    }

    private static void record(ServerPlayer player) {
        if (player.level() instanceof ServerLevel level) {
            LAST_ACTIVE.put(player.getUUID(), level.getGameTime());
        }
    }

    /**
     * Whether this player has worked recently enough to hold a siege open.
     *
     * <p>A player nobody has seen act is not counted. That is the safe way
     * round: the effect of being counted is that somebody else's nation stays
     * locked in a war, so silence should end it rather than extend it.
     */
    public static boolean activeRecently(ServerPlayer player) {
        Long last = LAST_ACTIVE.get(player.getUUID());
        if (last == null) {
            return false;
        }
        long now = player.level().getGameTime();
        return SiegeTimerPolicy.pressing(now - last,
                S2TerritoryConfig.siegeContestActivityTicks());
    }
}
