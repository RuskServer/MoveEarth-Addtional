package com.ruskserver.moveearth_addtional.compat.cbc;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * Who answers for a Create Big Cannons shot. CBC itself never sets an owner on what it fires, so the
 * responsible party is chosen here at fire time, in this order:
 * <ol>
 *   <li>the real player controlling the cannon (fake players never count);</li>
 *   <li>the real player who placed the cannon mount, online or not: whoever installs an automatic gun
 *       answers for it;</li>
 *   <li>no player, but the nation owning the vehicle the mount is on;</li>
 *   <li>no player, but the nation controlling the territory where the mount stands.</li>
 * </ol>
 * A shot none of these covers is unattributed and may not wear down reinforcement, territory cores or
 * vehicle cores and armour; it still hits ordinary blocks and entities.
 */
public final class CbcShotAttributionPolicy {
    private CbcShotAttributionPolicy() { }

    public enum Basis { PASSENGER, PLACER, VEHICLE_NATION, TERRITORY_NATION, NONE }

    /** {@code actorId} is set for the player bases, {@code nationId} only for the nation-only bases. */
    public record Decision(Basis basis, UUID actorId, UUID nationId) {
        public static final Decision NONE = new Decision(Basis.NONE, null, null);

        public boolean attributed() {
            return mayDamageProtected(actorId, nationId);
        }
    }

    /**
     * @param realPassenger   the controlling passenger when that is a real player, else null
     * @param realPlacer      the recorded placer of the cannon mount (always a real player), else null
     * @param vehicleNation   nation owning the vehicle the mount is on; only asked when no player answers
     * @param territoryNation nation controlling the mount's territory; only asked when nothing above answers
     */
    public static Decision decide(UUID realPassenger, UUID realPlacer,
                                  Supplier<UUID> vehicleNation, Supplier<UUID> territoryNation) {
        if (realPassenger != null) return new Decision(Basis.PASSENGER, realPassenger, null);
        if (realPlacer != null) return new Decision(Basis.PLACER, realPlacer, null);
        UUID vehicle = vehicleNation == null ? null : vehicleNation.get();
        if (vehicle != null) return new Decision(Basis.VEHICLE_NATION, null, vehicle);
        UUID territory = territoryNation == null ? null : territoryNation.get();
        if (territory != null) return new Decision(Basis.TERRITORY_NATION, null, territory);
        return Decision.NONE;
    }

    /** CBC damage with neither an actor nor a nation must leave reinforcement and cores untouched. */
    public static boolean mayDamageProtected(UUID actorId, UUID nationId) {
        return actorId != null || nationId != null;
    }
}
