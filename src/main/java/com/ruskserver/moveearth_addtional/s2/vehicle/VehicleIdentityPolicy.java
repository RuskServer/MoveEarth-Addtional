package com.ruskserver.moveearth_addtional.s2.vehicle;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * Which vehicle a set of connected Sable bodies belongs to.
 *
 * <p>Bodies keep the vehicle id they were bound to even after that vehicle's
 * record is removed (its core was dismantled). Re-placing a core binds only the
 * body it sits on, so a turret joined by a bearing still carries the dead id.
 * Counting that dead id as a second claimant made the whole craft ambiguous and
 * silently stripped its protection; ids without a record govern nothing and are
 * ignored. Two <em>live</em> ids stay ambiguous.
 */
public final class VehicleIdentityPolicy {
    private VehicleIdentityPolicy() { }

    /** The single live vehicle among the ids found on the chain, or empty when none or several. */
    public static Optional<UUID> resolve(Collection<UUID> candidates, Predicate<UUID> recordExists) {
        UUID resolved = null;
        for (UUID candidate : new LinkedHashSet<>(candidates)) {
            if (candidate == null || !recordExists.test(candidate)) continue;
            if (resolved != null) return Optional.empty();
            resolved = candidate;
        }
        return Optional.ofNullable(resolved);
    }

    /** Ids found on the chain whose vehicle record no longer exists. */
    public static Set<UUID> stale(Collection<UUID> candidates, Predicate<UUID> recordExists) {
        Set<UUID> stale = new LinkedHashSet<>();
        for (UUID candidate : candidates) {
            if (candidate != null && !recordExists.test(candidate)) stale.add(candidate);
        }
        return stale;
    }
}
