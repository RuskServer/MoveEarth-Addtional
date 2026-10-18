package com.ruskserver.moveearth_addtional.s2.nation;

/**
 * When storage the world generated may be opened by anyone. Pure, so it is unit tested.
 *
 * <p>Storage outside a nation's own land is otherwise off limits, which also locked the loot chests,
 * barrels and chest minecarts of structures in the wilderness. Those are recognised by the loot table
 * they still carry until first opened, or, for the ones a structure fills in its template rather than
 * from a loot table, by standing inside one of the structure's pieces; once opened they are remembered,
 * so the rest of their contents can still be taken by whoever comes later. Players cannot place storage
 * in the wilderness, so nothing a player built is opened up by this.
 */
public final class StructureLootPolicy {
    private StructureLootPolicy() { }

    /**
     * @param ownedByNation  the storage is recorded as some existing nation's
     * @param onVehicle      it rides a vehicle, whose own rules apply
     * @param inTerritory    it stands in any nation's territory, whose rules apply
     * @param lootPending    it still carries a structure loot table, not yet rolled
     * @param remembered     it was recognised as structure storage when first opened
     * @param inStructure    it stands inside a generated structure's piece
     */
    public static boolean openToAll(boolean ownedByNation, boolean onVehicle, boolean inTerritory,
                                    boolean lootPending, boolean remembered, boolean inStructure) {
        return !ownedByNation && !onVehicle && !inTerritory && (lootPending || remembered || inStructure);
    }
}
