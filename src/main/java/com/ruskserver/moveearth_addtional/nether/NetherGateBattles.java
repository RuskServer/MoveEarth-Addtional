package com.ruskserver.moveearth_addtional.nether;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Gate battles running in this server session, and the limits on opening another.
 * Every charged gate used to open as soon as a member came near, each wave adding
 * persistent strengthened mobs, so one nation with a row of gates could fill the
 * server with them.
 */
public final class NetherGateBattles {
    public enum Verdict { ALLOWED, NATION_LIMIT, SERVER_LIMIT, TOO_CLOSE }

    /** {@code dimension} is the level's id; only equality matters. */
    public record Battle(UUID encounter, UUID nation, String dimension, int x, int y, int z) { }

    private static final Map<UUID, Battle> ACTIVE = new ConcurrentHashMap<>();

    private NetherGateBattles() { }

    static void register(Battle battle) {
        ACTIVE.put(battle.encounter(), battle);
    }

    static void unregister(UUID encounter) {
        if (encounter != null) ACTIVE.remove(encounter);
    }

    static Collection<Battle> active() {
        return List.copyOf(ACTIVE.values());
    }

    static void clear() {
        ACTIVE.clear();
    }

    /**
     * Whether a gate of {@code nation} at the given spot may open now. The nation's own
     * limit is reported first, since that is the one its members can do something about.
     */
    public static Verdict canOpen(Collection<Battle> active, UUID nation, String dimension, int x, int y, int z,
                                  int perNationLimit, int serverLimit, int minimumDistance) {
        int nationCount = 0;
        boolean tooClose = false;
        long minimumSqr = (long) Math.max(0, minimumDistance) * Math.max(0, minimumDistance);
        for (Battle battle : active) {
            if (battle.nation().equals(nation)) nationCount++;
            if (minimumSqr > 0L && battle.dimension().equals(dimension)) {
                long dx = battle.x() - x;
                long dz = battle.z() - z;
                if (dx * dx + dz * dz < minimumSqr) tooClose = true;
            }
        }
        if (nationCount >= Math.max(1, perNationLimit)) return Verdict.NATION_LIMIT;
        if (active.size() >= Math.max(1, serverLimit)) return Verdict.SERVER_LIMIT;
        if (tooClose) return Verdict.TOO_CLOSE;
        return Verdict.ALLOWED;
    }

    /** A battle nobody has been near for {@code timeoutTicks} (inclusive) is closed. */
    public static boolean deserted(long lastPresenceTick, long now, int timeoutTicks) {
        return now - lastPresenceTick >= timeoutTicks;
    }
}
