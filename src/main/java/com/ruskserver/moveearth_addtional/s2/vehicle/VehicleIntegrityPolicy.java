package com.ruskserver.moveearth_addtional.s2.vehicle;

import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * What {@code VehicleIntegrityService} does with what it finds on a loaded Sable body. Pure, so it is tested
 * without a server.
 */
public final class VehicleIntegrityPolicy {
    /** Sweeps in a row a loaded craft must be missing its core before its record is dropped. */
    public static final int MISSING_CORE_STRIKES = 3;

    private VehicleIntegrityPolicy() {
    }

    public enum PlotRecord {
        /** The record already points at this body. */
        KEEP,
        /** The core stands here but the record names another body: point it at this one. */
        RELINK,
        /**
         * The record names another body, yet its core position lies in this body's plot. Plots are not shared,
         * so the body it named is gone (removed without its blocks breaking) and Sable gave its plot to this
         * one; the record is a ghost that would make whatever block now sits there count as its core.
         */
        GHOST
    }

    /**
     * @param recordBody the body the record names
     * @param thisBody   the body whose plot holds the record's core position
     * @param coreHere   whether a core carrying the record's id stands at that position
     */
    public static PlotRecord plotRecord(UUID recordBody, UUID thisBody, boolean coreHere) {
        if (thisBody.equals(recordBody)) return PlotRecord.KEEP;
        return coreHere ? PlotRecord.RELINK : PlotRecord.GHOST;
    }

    /**
     * Whether a core block that lost its record gets it back. Only on the body that still shows it was this
     * vehicle's (it carries the id, or is stamped with the core's nation), and only for a nation that exists:
     * a stray copy set down elsewhere proves nothing.
     */
    public static boolean restoresCore(UUID coreVehicle, UUID coreNation, UUID bodyVehicle, UUID bodyNation,
                                       boolean nationExists) {
        if (coreVehicle == null || coreNation == null || !nationExists) return false;
        return coreVehicle.equals(bodyVehicle) || coreNation.equals(bodyNation);
    }

    /**
     * The nation an unowned body most likely belongs to, read from the storage on it: the single existing nation
     * that placed storage there, or null when none or several did.
     */
    public static UUID inferredNation(Map<UUID, Integer> storageOwners, Predicate<UUID> nationExists) {
        UUID found = null;
        for (UUID owner : storageOwners.keySet()) {
            if (owner == null || !nationExists.test(owner)) continue;
            if (found != null && !found.equals(owner)) return null;
            found = owner;
        }
        return found;
    }

    /** Whether a record whose loaded body has been missing its core for {@code strikes} sweeps is dropped. */
    public static boolean dropsMissingCore(int strikes) {
        return strikes >= MISSING_CORE_STRIKES;
    }
}
