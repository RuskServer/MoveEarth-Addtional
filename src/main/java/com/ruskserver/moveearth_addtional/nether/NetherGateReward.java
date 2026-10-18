package com.ruskserver.moveearth_addtional.nether;

/**
 * Who takes a cleared gate's shards. Pure, so it is tested without a server.
 *
 * <p>The player who landed the final blow takes them, when they are of the gate's nation or an ally. The gate is
 * the nation's fight, so an outsider who steals the last kill gets nothing: the shards go to the nearest citizen
 * by the generator instead. With nobody to give them to, they drop on the generator as they always did.
 */
public final class NetherGateReward {
    public enum Recipient { LAST_ATTACKER, NEAREST_CITIZEN, GENERATOR }

    private NetherGateReward() { }

    /**
     * @param attackerOnline   the player who killed the last enemy is known and still online
     * @param attackerFriendly that player is of the gate's nation or an ally of it
     * @param citizenNearby    a citizen of the gate's nation is by the generator
     */
    public static Recipient recipient(boolean attackerOnline, boolean attackerFriendly, boolean citizenNearby) {
        if (attackerOnline && attackerFriendly) return Recipient.LAST_ATTACKER;
        return citizenNearby ? Recipient.NEAREST_CITIZEN : Recipient.GENERATOR;
    }
}
