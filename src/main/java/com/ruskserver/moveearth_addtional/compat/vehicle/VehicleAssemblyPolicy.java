package com.ruskserver.moveearth_addtional.compat.vehicle;

import java.util.Set;
import java.util.UUID;

/**
 * What a Sable assembly may lift, whether it may go ahead at all, and which
 * vehicle the new body belongs to.
 *
 * <p>Nothing is dropped silently. Blocks the policy leaves behind are counted
 * per reason so the players around the assembler are told; reinforcement
 * always travels with its block, inactive while no live core governs the body
 * (see {@link #reinforcementInactive}); and an assembly that would tear the
 * assembling nation's own protected hull, or bind one body to two cores, is
 * refused where the caller can refuse (Simulated's assembler) instead of
 * producing a half-built craft or an orphaned, still-billed core record.
 */
public final class VehicleAssemblyPolicy {
    private VehicleAssemblyPolicy() { }

    /** Where one candidate block ends up. */
    public enum Placement {
        /** Lifted with the craft. */
        KEPT,
        /** Left behind: tied to its location (territory core, station, deposit...). */
        FIXTURE,
        /** Left behind: another nation's active reinforcement or live core. */
        FOREIGN,
        /**
         * Would be left behind although it belongs to the nation that asked for
         * the assembly: its blocks stand on that nation's land while the
         * assembler does not. Lifting it would hand protected blocks to an
         * unowned craft; leaving it would tear the nation's own hull.
         */
        OWN
    }

    public enum Refusal {
        NONE,
        /** {@link Placement#OWN} blocks would stay behind. */
        OWN_BLOCKS_LEFT_BEHIND,
        /** Two or more live vehicle cores in one lifted set. */
        MULTIPLE_CORES,
        /** A lifted core differs from the vehicle governing the body it is lifted from. */
        CORE_CONFLICT
    }

    /**
     * @param blockOwner     nation owning the block where it stands, or null
     * @param assemblingSide owner of the assembler's position by the same rule, or null
     * @param actorNation    nation of the player who asked for the assembly, or null when
     *                       the assembly was not started by a player (bearings, Sable splits)
     */
    public static Placement place(AssemblyExclusionPolicy.Kind kind, UUID blockOwner, UUID assemblingSide,
                                  UUID actorNation) {
        if (!AssemblyExclusionPolicy.excluded(kind, blockOwner, assemblingSide)) return Placement.KEPT;
        if (kind == AssemblyExclusionPolicy.Kind.FIXTURE) return Placement.FIXTURE;
        return actorNation != null && actorNation.equals(blockOwner) ? Placement.OWN : Placement.FOREIGN;
    }

    /** Counts of blocks left behind, per reason. */
    public record Tally(int fixtures, int foreign, int own) {
        public static final Tally EMPTY = new Tally(0, 0, 0);

        public Tally with(Placement placement) {
            return switch (placement) {
                case KEPT -> this;
                case FIXTURE -> new Tally(fixtures + 1, foreign, own);
                case FOREIGN -> new Tally(fixtures, foreign + 1, own);
                case OWN -> new Tally(fixtures, foreign, own + 1);
            };
        }

        public int total() { return fixtures + foreign + own; }

        public boolean any() { return total() > 0; }
    }

    /**
     * @param vehicleId vehicle the assembled body is bound to, or null for none
     * @param conflict  why no vehicle could be chosen, {@link Refusal#NONE} otherwise
     */
    public record Binding(UUID vehicleId, Refusal conflict) {
        public boolean conflicting() { return conflict != Refusal.NONE; }
    }

    /**
     * @param liftedCores live vehicle ids of the cores in the lifted set
     * @param inherited   vehicle governing the body the blocks are lifted from, or null
     */
    public static Binding binding(Set<UUID> liftedCores, UUID inherited) {
        if (liftedCores.size() > 1) return new Binding(null, Refusal.MULTIPLE_CORES);
        if (liftedCores.isEmpty()) return new Binding(inherited, Refusal.NONE);
        UUID core = liftedCores.iterator().next();
        if (inherited != null && !inherited.equals(core)) return new Binding(null, Refusal.CORE_CONFLICT);
        return new Binding(core, Refusal.NONE);
    }

    /** The first reason the assembly must not go ahead, or {@link Refusal#NONE}. */
    public static Refusal refusal(Tally tally, Binding binding) {
        if (tally.own() > 0) return Refusal.OWN_BLOCKS_LEFT_BEHIND;
        return binding.conflict();
    }

    /**
     * Reinforcement lifted onto a body no live core governs keeps its entries
     * but not its protection: protection follows the governing vehicle
     * ({@code SableVehicleTopology.at}), so it switches on by itself once a core
     * is registered on the body, and back to the territory's rules if the body
     * is set down again.
     */
    public static boolean reinforcementInactive(int liftedReinforced, UUID vehicleId) {
        return liftedReinforced > 0 && vehicleId == null;
    }
}
