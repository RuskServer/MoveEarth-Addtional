package com.ruskserver.moveearth_addtional.s2.nation;

/**
 * Where a player falls in the tab list by their nation: your own nation first, then allies, neutral nations,
 * hostile ones, and players with no nation last. Within a group the server's own order (TAB's teams) still
 * applies. Pure, so it is tested without a client.
 */
public final class NationTabOrder {
    private NationTabOrder() {
    }

    /** Lower sorts first. {@code inNation} is false for a player with no nation, whatever the relation says. */
    public static int group(NationNameplateRelation relation, boolean inNation) {
        if (!inNation || relation == null) return 4;
        return switch (relation) {
            case OWN_NATION -> 0;
            case ALLY -> 1;
            case NEUTRAL -> 2;
            case HOSTILE -> 3;
        };
    }
}
