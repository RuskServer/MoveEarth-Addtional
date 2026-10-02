package com.ruskserver.moveearth_addtional.s2.vehicle;

import java.util.UUID;

/**
 * Pure rules that give vehicles the protections territory already has.
 *
 * <p>Vehicle blocks live in Sable plot coordinates where no territory exists, so every rule keyed on the
 * territory at a position (peace truce, rebuilding truce, offline defense) used to see "nobody's land".
 */
public final class VehicleProtectionPolicy {
    /** One hit notice per vehicle and attacker this often. */
    public static final long HIT_NOTICE_INTERVAL_MILLIS = 300_000L;

    private VehicleProtectionPolicy() { }

    /** A target on a vehicle is defended by the vehicle's owner; otherwise by whoever controls the land. */
    public static UUID defendingNation(UUID vehicleNation, UUID territoryNation) {
        return vehicleNation != null ? vehicleNation : territoryNation;
    }

    /** Offline defense covers a vehicle only while its physical position is inside its own nation's territory. */
    public static boolean offlineDefenseApplies(UUID vehicleNation, UUID territoryNationAtWorldPos) {
        return vehicleNation != null && vehicleNation.equals(territoryNationAtWorldPos);
    }

    /**
     * A fallen nation's rebuilding truce shields its vehicles only at home, like offline defense. Applied
     * everywhere, it made a fallen nation's tank untouchable for a whole day while it went on attacking.
     */
    public static boolean settlementTruceApplies(boolean nationTruce, UUID vehicleNation,
                                                 UUID territoryNationAtWorldPos) {
        return nationTruce && offlineDefenseApplies(vehicleNation, territoryNationAtWorldPos);
    }

    /**
     * Divisor for damage to a vehicle. {@code territoryDivisor} is the territory core's divisor with its
     * rolling-Siege suppression already applied. Long absence weakens a territory but must not make a parked
     * vehicle weaker than full strength, so a weakened nation's vehicle simply takes full damage.
     */
    public static int offlineDivisor(boolean applies, boolean longAbsenceWeakened, int territoryDivisor) {
        if (!applies || longAbsenceWeakened) return 1;
        return Math.max(1, territoryDivisor);
    }

    /** The same rule as reinforcement friendly fire: own or allied fire never wears a vehicle down. */
    public static boolean friendly(UUID attackerSide, UUID vehicleNation, boolean allied) {
        return attackerSide != null && vehicleNation != null && (attackerSide.equals(vehicleNation) || allied);
    }

    public static boolean hitNoticeDue(long lastNoticeMillis, long nowMillis) {
        return lastNoticeMillis <= 0L || nowMillis < lastNoticeMillis
                || nowMillis - lastNoticeMillis >= HIT_NOTICE_INTERVAL_MILLIS;
    }
}
