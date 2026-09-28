package com.ruskserver.moveearth_addtional.s2.reinforcement;

import com.ruskserver.moveearth_addtional.s2.nation.NationSavedData;
import com.ruskserver.moveearth_addtional.s2.notification.NationNotificationSavedData;
import com.ruskserver.moveearth_addtional.s2.notification.NationNotificationService;
import com.ruskserver.moveearth_addtional.s2.territory.TerritorySavedData;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.BlockEvent;

/**
 * Unreinforced blocks stay breakable by design, but until now a nation lost farms and machines
 * without any sign of it: no Siege, no alert, no history. Foreign breaking in controlled land is
 * reported to the owner, once per breaker and nation every few minutes with the count since.
 */
@EventBusSubscriber(modid = com.ruskserver.moveearth_addtional.Moveearth_addtional.MODID,
        bus = EventBusSubscriber.Bus.GAME)
public final class TerritoryIntrusionNotifier {
    private static final Map<Key, Pending> pending = new HashMap<>();

    private record Key(UUID breaker, UUID nation) { }
    private record Pending(long lastNoticeMillis, int unreported) { }

    private TerritoryIntrusionNotifier() { }

    /** Lowest priority: report only breaks no protection cancelled. Reinforced blocks are Siege. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onBreak(BlockEvent.BreakEvent event) {
        if (event.isCanceled() || !(event.getLevel() instanceof ServerLevel level)
                || !(event.getPlayer() instanceof ServerPlayer player)) return;
        if (ReinforcementSavedData.get(level).get(event.getPos()).isPresent()) return;
        brokeUnreinforced(player, level, event.getPos());
    }

    static void brokeUnreinforced(ServerPlayer breaker, ServerLevel level, BlockPos pos) {
        if (breaker.isCreative() || breaker.isSpectator()) return;
        var server = level.getServer();
        TerritorySavedData.CoreRecord core = TerritorySavedData.get(server)
                .controllingCore(server, level.dimension().location(), pos).orElse(null);
        if (core == null) return;
        NationSavedData nations = NationSavedData.get(server);
        UUID breakerNation = nations.nationIdFor(breaker.getUUID()).orElse(null);
        if (breakerNation != null && (breakerNation.equals(core.nationId())
                || nations.isAllied(breakerNation, core.nationId()))) return;
        Key key = new Key(breaker.getUUID(), core.nationId());
        long now = System.currentTimeMillis();
        Pending previous = pending.get(key);
        int count = (previous == null ? 0 : previous.unreported()) + 1;
        if (!TerritoryIntrusionPolicy.shouldNotify(previous == null ? 0L : previous.lastNoticeMillis(), now)) {
            pending.put(key, new Pending(previous.lastNoticeMillis(), count));
            return;
        }
        pending.put(key, new Pending(now, 0));
        String name = breaker.getGameProfile().getName();
        // Core position, not the block: the Discord outbox deduplicates by position.
        NationNotificationService.publish(server, List.of(core.nationId()),
                NationNotificationSavedData.EventType.TERRITORY_INTRUSION, core.dimension(), core.pos(),
                Component.translatable("message.moveearth_addtional.territory.intrusion", name, count,
                        pos.getX(), pos.getY(), pos.getZ()),
                List.of(name, Integer.toString(count)));
    }

    public static void clear() { pending.clear(); }
}
