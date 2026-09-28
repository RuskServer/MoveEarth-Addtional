package com.ruskserver.moveearth_addtional.economy;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Coalesces a player's browse-only market refreshes. Each snapshot expires
 * orders and walks up to 256 stations, so a held refresh button could cost a
 * lot; requests inside the cooldown are not dropped but folded into one send,
 * with the latest selection, once it ends. Trading actions are never throttled.
 */
final class MarketBrowseThrottle {
    static final int COOLDOWN_TICKS = 10;

    private final Map<UUID, Long> lastSent = new HashMap<>();
    private final Map<UUID, UUID> pending = new HashMap<>();

    /** True when the snapshot should go out now; otherwise the selection waits for {@link #due}. */
    boolean request(UUID player, UUID selection, long tick) {
        Long last = lastSent.get(player);
        if (last == null || tick - last >= COOLDOWN_TICKS) {
            lastSent.put(player, tick);
            pending.remove(player);
            return true;
        }
        pending.put(player, selection);
        return false;
    }

    /** Deferred selections whose cooldown has ended, as player-selection pairs; they count as sent. */
    List<Map.Entry<UUID, UUID>> due(long tick) {
        List<Map.Entry<UUID, UUID>> ready = new ArrayList<>();
        var iterator = pending.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            Long last = lastSent.get(entry.getKey());
            if (last != null && tick - last < COOLDOWN_TICKS) continue;
            ready.add(Map.entry(entry.getKey(), entry.getValue()));
            lastSent.put(entry.getKey(), tick);
            iterator.remove();
        }
        return ready;
    }

    void forget(UUID player) {
        lastSent.remove(player);
        pending.remove(player);
    }
}
