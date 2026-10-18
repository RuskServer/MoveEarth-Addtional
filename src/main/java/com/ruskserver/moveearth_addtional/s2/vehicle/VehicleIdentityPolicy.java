package com.ruskserver.moveearth_addtional.s2.vehicle;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
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
    /**
     * The vehicle governing one body of a connected chain. A vehicle governs only while its core's body is in
     * the chain; ids carried by fragments that split off the core are ownership, not governance. Among the
     * vehicles whose core is present, the body's own id wins: two crafts tied by a rope or docking connector
     * each keep their own, where counting both for the whole chain used to strip both of protection.
     *
     * @param ownBody    the body asked about
     * @param bound      vehicle id carried by each body of the chain that has one
     * @param chain      every body of the chain
     * @param coreBodyOf the body a live vehicle's core sits on, or null when the record is gone or its core is
     *                   not on a body
     */
    public static Optional<UUID> governing(UUID ownBody, Map<UUID, UUID> bound, Set<UUID> chain,
                                           Function<UUID, UUID> coreBodyOf) {
        Set<UUID> present = new LinkedHashSet<>();
        for (UUID vehicle : bound.values()) {
            UUID coreBody = vehicle == null ? null : coreBodyOf.apply(vehicle);
            if (coreBody != null && chain.contains(coreBody)) present.add(vehicle);
        }
        UUID own = bound.get(ownBody);
        if (own != null && present.contains(own)) return Optional.of(own);
        return present.size() == 1 ? Optional.of(present.iterator().next()) : Optional.empty();
    }

    /**
     * Whether a body carrying {@code carried} should be handed to {@code governing}: the id is dead, or it is a
     * same-nation vehicle whose core is not in this chain (a fragment rejoined to, or re-cored as, another craft).
     * Another nation's live id is never taken over.
     */
    public static boolean rebindable(UUID carried, UUID governing, boolean carriedLive, UUID carriedCoreBody,
                                     Set<UUID> chain, UUID carriedNation, UUID governingNation) {
        if (carried == null || carried.equals(governing)) return false;
        if (!carriedLive) return true;
        return (carriedCoreBody == null || !chain.contains(carriedCoreBody))
                && carriedNation != null && carriedNation.equals(governingNation);
    }
}
