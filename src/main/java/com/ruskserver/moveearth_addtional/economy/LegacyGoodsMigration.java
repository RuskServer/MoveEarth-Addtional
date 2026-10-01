package com.ruskserver.moveearth_addtional.economy;

/**
 * What to do with market orders, claims, wreckage and event rewards found inside an older ledger
 * file, from before they moved to their own goods file.
 */
public enum LegacyGoodsMigration {
    /** The ledger holds none. */
    NONE,
    /** No goods file existed yet: the ledger copy is the only one and moves over. */
    IMPORT,
    /**
     * A goods file already exists, so the import already happened and the goods file has been
     * written since; the ledger copy is a stale leftover from a crash before the next ledger save.
     */
    DISCARD;

    public static LegacyGoodsMigration decide(boolean ledgerHasGoods, boolean goodsFileExisted) {
        if (!ledgerHasGoods) return NONE;
        return goodsFileExisted ? DISCARD : IMPORT;
    }
}
