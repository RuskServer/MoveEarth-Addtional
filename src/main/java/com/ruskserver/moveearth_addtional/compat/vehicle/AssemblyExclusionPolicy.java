package com.ruskserver.moveearth_addtional.compat.vehicle;

import java.util.UUID;

/**
 * Which blocks a Sable assembly must leave in the world.
 *
 * <p>Sable lifts whatever Create's structure search reaches (glue, slime,
 * honey glue, chassis, attached blocks), and nothing on that path asks whose
 * block it is. Excluding a block leaves it where it stands, as ore deposits
 * always were; the craft assembles without it.
 */
public final class AssemblyExclusionPolicy {
    private AssemblyExclusionPolicy() { }

    public enum Kind {
        /** Anything without S2 ownership. */
        ORDINARY,
        /**
         * Never moves: territory cores, market stations, storage wreckage and
         * ore deposits are tied to their location, not to a craft.
         */
        FIXTURE,
        /**
         * Active reinforcement or a live vehicle core: moves only with the side
         * that owns it where it stands.
         */
        NATION_PROTECTED
    }

    /**
     * @param blockOwner     nation owning the block where it stands (vehicle nation inside a Sable
     *                       body, controlling territory otherwise), or null when nobody does
     * @param assemblingSide owner of the assembly anchor by the same rule, or null
     */
    public static boolean excluded(Kind kind, UUID blockOwner, UUID assemblingSide) {
        return switch (kind) {
            case ORDINARY -> false;
            case FIXTURE -> true;
            case NATION_PROTECTED -> blockOwner != null && !blockOwner.equals(assemblingSide);
        };
    }
}
