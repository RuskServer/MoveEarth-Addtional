package com.ruskserver.moveearth_addtional.region;

import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Notices when a player crosses into another region, and remembers that they did.
 *
 * <p>Without this, a region that has no gold is indistinguishable from a bug.
 * A player digs, finds nothing, digs somewhere else, finds nothing, and the
 * only honest conclusion available to them is that ore generation is broken.
 * The crossing is the moment where saying so costs nothing and explains
 * everything after it.
 *
 * <p>Nothing is recorded. The hub describes the region a player is standing in
 * and no other, so there is nothing to have learned and nothing to keep.
 *
 * <p>Ten ticks, staggered by player, following the territory tracker next door.
 * The region lookup is cached per chunk, so the cost is a map read for a player
 * who has not moved far.
 */
@EventBusSubscriber(modid = Moveearth_addtional.MODID, bus = EventBusSubscriber.Bus.GAME)
public final class RegionPresenceEvents {

    private static final int CHECK_INTERVAL_TICKS = 10;

    /** Last region seen per player. Absent means "not looked yet", not "nowhere". */
    private static final Map<UUID, Integer> LAST = new HashMap<>();

    private RegionPresenceEvents() { }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        int phase = Math.floorMod(player.getUUID().hashCode(), CHECK_INTERVAL_TICKS);
        if ((player.tickCount + phase) % CHECK_INTERVAL_TICKS != 0) {
            return;
        }
        observe(player, true);
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            // Recorded but not announced: logging in inside a region is not a
            // crossing, and greeting someone with where they already were reads
            // as noise rather than as information.
            observe(player, false);
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            LAST.remove(player.getUUID());
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        LAST.clear();
    }

    private static void observe(ServerPlayer player, boolean announce) {
        if (!RegionProfiles.ready() || !Level.OVERWORLD.equals(player.level().dimension())) {
            return;
        }
        int region = RegionResolver.regionAt(player.getBlockX(), player.getBlockZ());
        if (region == RegionGrid.NONE) {
            // Open ocean belongs to nobody. Forgetting here means stepping back
            // ashore announces the region again, which is the useful moment.
            LAST.remove(player.getUUID());
            return;
        }
        Integer previous = LAST.put(player.getUUID(), region);
        if (announce && previous != null && previous != region) {
            player.displayClientMessage(describe(region), true);
        }
    }

    private static Component describe(int region) {
        List<String> exclusives = RegionProfiles.exclusivesOf(region);
        Component name = Component.translatable("message.moveearth_addtional.region.name", region);
        if (exclusives.isEmpty()) {
            return Component.translatable("message.moveearth_addtional.region.entered_plain", name)
                    .withStyle(ChatFormatting.GRAY);
        }
        return Component.translatable("message.moveearth_addtional.region.entered", name,
                        RegionMaterialNames.list(exclusives))
                .withStyle(ChatFormatting.GRAY);
    }
}
