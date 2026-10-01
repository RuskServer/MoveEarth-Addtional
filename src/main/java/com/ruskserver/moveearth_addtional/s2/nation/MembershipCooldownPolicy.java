package com.ruskserver.moveearth_addtional.s2.nation;

import com.ruskserver.moveearth_addtional.s2.time.OpenTimePolicy;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * Cooldown after a player leaves, is kicked from, or disbands a nation. Durations are in
 * server-opening ticks. While it runs the player can neither join nor found a nation (a kick only
 * blocks founding), and Siege checks still bind them to their former nation's peace and alliances.
 * Ordinary members of a disbanded nation chose nothing and get no cooldown; see {@link #onDisband}.
 *
 * <p>Each lost membership is its own {@link Entry}. A later loss never replaces an earlier binding
 * that still runs: otherwise founding and disbanding a throwaway nation, or joining another nation
 * and being kicked again, would re-point the binding away from the nation whose truces it enforces.
 */
public final class MembershipCooldownPolicy {
    /** Four hours of server-opening time. */
    public static final long COOLDOWN_OPEN_TICKS = 4L * 60L * 60L * 20L;
    /**
     * Most entries kept per player. Reaching it needs this many lost memberships inside one
     * cooldown; the earliest entry (the binding) and the newest (the longest join lock) always stay.
     */
    public static final int MAX_ENTRIES = 16;

    /**
     * One lost membership.
     *
     * @param joinLocked false for a kick: the player may join another nation at once, but still may
     *                   not found one while the binding runs
     */
    public record Entry(UUID formerNationId, long endsAt, boolean joinLocked) { }

    private MembershipCooldownPolicy() { }

    public static long endsAt(long openNow) {
        return OpenTimePolicy.advance(openNow, COOLDOWN_OPEN_TICKS);
    }

    public static long remaining(long endsAt, long openNow) {
        return endsAt <= 0L ? 0L : Math.max(0L, endsAt - Math.max(0L, openNow));
    }

    public static boolean active(long endsAt, long openNow) {
        return remaining(endsAt, openNow) > 0L;
    }

    /**
     * Adds a newly lost membership after the entries that still run, dropping the ones that have
     * run out. Earlier running entries are never replaced, so the earliest binding holds until it
     * expires.
     */
    public static List<Entry> record(List<Entry> existing, Entry added, long openNow) {
        List<Entry> result = new ArrayList<>();
        if (existing != null) {
            for (Entry entry : existing) {
                if (entry != null && active(entry.endsAt(), openNow)) result.add(entry);
            }
        }
        if (added != null && active(added.endsAt(), openNow)) result.add(added);
        // Entries are in the order they started, so index 1 is the soonest to end after the binding.
        while (result.size() > MAX_ENTRIES) result.remove(1);
        return List.copyOf(result);
    }

    /** Open-time ticks before the player may join (or found) a nation again; 0 when free. */
    public static long joinLockRemaining(List<Entry> entries, long openNow) {
        long remaining = 0L;
        if (entries == null) return remaining;
        for (Entry entry : entries) {
            if (entry.joinLocked()) remaining = Math.max(remaining, remaining(entry.endsAt(), openNow));
        }
        return remaining;
    }

    /**
     * The binding that applies now: the earliest entry that still runs and whose nation still
     * exists. Later bindings take over once it expires.
     */
    public static Optional<Entry> binding(List<Entry> entries, long openNow, Predicate<UUID> nationExists) {
        if (entries == null) return Optional.empty();
        for (Entry entry : entries) {
            if (entry.formerNationId() != null && active(entry.endsAt(), openNow)
                    && nationExists.test(entry.formerNationId())) return Optional.of(entry);
        }
        return Optional.empty();
    }

    /** Open-time ticks the current {@link #binding} still runs; 0 when unbound. */
    public static long bindingRemaining(List<Entry> entries, long openNow, Predicate<UUID> nationExists) {
        return binding(entries, openNow, nationExists).map(entry -> remaining(entry.endsAt(), openNow)).orElse(0L);
    }

    /** Why a nationless player may not found a nation, or ALLOWED. */
    public static FoundingDecision founding(List<Entry> entries, long openNow, Predicate<UUID> nationExists) {
        if (joinLockRemaining(entries, openNow) > 0L) return FoundingDecision.MEMBERSHIP_COOLDOWN;
        // A kicked player still bound to a truce must not found a nation: disbanding it at once
        // would otherwise record a newer loss and look like a way out of the binding.
        return binding(entries, openNow, nationExists).isPresent()
                ? FoundingDecision.FORMER_NATION_BOUND : FoundingDecision.ALLOWED;
    }

    public enum FoundingDecision { ALLOWED, MEMBERSHIP_COOLDOWN, FORMER_NATION_BOUND }

    /**
     * The nation whose ceasefires and alliances bind an attacker: their current (or contracted)
     * nation, else the nation they left while its cooldown still runs and it still exists.
     */
    public static UUID ceasefireNation(UUID currentNation, UUID formerNation, boolean cooldownActive,
                                       boolean formerNationExists) {
        if (currentNation != null) return currentNation;
        return cooldownActive && formerNationExists ? formerNation : null;
    }

    /**
     * Whether a player still bound to {@code formerNation} may not attack {@code defenderNation}.
     * Attacking the former nation itself is not covered: that is not a ceasefire or alliance.
     */
    public static boolean formerNationBlocks(UUID formerNation, UUID defenderNation,
                                             boolean allied, boolean peaceTruce) {
        return formerNation != null && defenderNation != null && !formerNation.equals(defenderNation)
                && (allied || peaceTruce);
    }

    /**
     * The cooldown a disbandment leaves on one member of the nation, if any.
     *
     * <p>Only the owner who disbanded is locked out of joining and founding: that keeps a
     * found-and-disband cycle from being free. Every other member chose nothing, like a kicked
     * player, so they may join or found at once. They do not even get a kick's binding: disbanding
     * is refused while the nation has an alliance, a peace truce or a Siege, and {@link #binding}
     * ignores nations that no longer exist, so a binding to the disbanded nation could never apply.
     * Bindings the member still serves from an earlier loss are kept by {@link #record}.
     */
    public static Optional<Entry> onDisband(boolean owner, UUID nationId, long openNow) {
        return owner ? Optional.of(new Entry(nationId, endsAt(openNow), true)) : Optional.empty();
    }

    /** What still restricts a nationless player after losing a membership. */
    public enum Restriction { NONE, JOIN_LOCKED, FOUNDING_BOUND }

    /** The restriction to tell a player about: a join lock outranks a founding-only binding. */
    public static Restriction restriction(long joinLockRemaining, long bindingRemaining) {
        if (joinLockRemaining > 0L) return Restriction.JOIN_LOCKED;
        return bindingRemaining > 0L ? Restriction.FOUNDING_BOUND : Restriction.NONE;
    }

    /** Whole minutes left, rounded up so "0" is never shown while a cooldown still runs. */
    public static long remainingMinutes(long ticks) {
        return ticks <= 0L ? 0L : (ticks + 1199L) / 1200L;
    }
}
