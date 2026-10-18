package com.ruskserver.moveearth_addtional.detector;

import java.util.UUID;

/**
 * Who a player detector belongs to and how it treats the players it sees. Pure, so it is tested without a
 * server.
 *
 * <p>A detector placed by a nation member belongs to the nation: its citizens are never intruders, allies are
 * visitors, its upkeep comes from the treasury and its alerts go to every citizen online. A player with no
 * nation owns theirs alone, and it passes to the nation they join.
 */
public final class DetectorAccessPolicy {
    public enum Relation { MEMBER, VISITOR, INTRUDER }

    private DetectorAccessPolicy() {
    }

    /**
     * @param ownerNation  the nation that owns the detector, or null for one owned by a player
     * @param owner        the player who owns it when no nation does
     * @param playerNation the seen player's nation, or null
     * @param allied       whether the seen player's nation is allied to {@code ownerNation}
     */
    public static Relation relation(UUID ownerNation, UUID owner, UUID player, UUID playerNation, boolean allied) {
        if (ownerNation == null) return player.equals(owner) ? Relation.MEMBER : Relation.INTRUDER;
        if (ownerNation.equals(playerNation)) return Relation.MEMBER;
        return allied ? Relation.VISITOR : Relation.INTRUDER;
    }

    /**
     * Whether a player may name, pay for, claim or remove the detector. A nation's detector takes a citizen
     * with Bastion access or treasury rights, since its upkeep comes from the treasury.
     */
    public static boolean mayManage(UUID ownerNation, UUID owner, UUID player, UUID playerNation,
                                    boolean nationRights, boolean operator) {
        if (operator) return true;
        if (ownerNation == null) return player.equals(owner);
        return ownerNation.equals(playerNation) && nationRights;
    }

    /** Whether a player may place or claim a detector: one for their nation needs the same rights as managing it. */
    public static boolean mayPlace(UUID playerNation, boolean nationRights, boolean operator) {
        return operator || playerNation == null || nationRights;
    }

    /** Whether a citizen may open the detector's screen to read it, even without the rights to change it. */
    public static boolean mayView(UUID ownerNation, UUID owner, UUID player, UUID playerNation) {
        return ownerNation == null ? player.equals(owner) : ownerNation.equals(playerNation);
    }
}
