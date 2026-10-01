package com.ruskserver.moveearth_addtional.s2.siege;

/** Pure decision behind {@link SiegeService.AttackBatch}: which attempts on a core are repeats. */
public final class SiegeBatchPolicy {
    private SiegeBatchPolicy() { }

    /**
     * Whether registering this attempt again cannot change Siege state. An effective hit repeats an
     * earlier effective hit (same rolling timer). A plain attempt repeats any earlier attempt or hit: it
     * never opens more than the first attempt did, and after a hit the Siege is already rolling or was
     * refused for a reason a plain attempt is refused for too.
     */
    public static boolean repeats(boolean attemptRecorded, boolean effectiveRecorded, boolean effective) {
        return effective ? effectiveRecorded : attemptRecorded || effectiveRecorded;
    }
}
