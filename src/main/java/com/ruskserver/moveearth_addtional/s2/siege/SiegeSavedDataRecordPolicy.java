package com.ruskserver.moveearth_addtional.s2.siege;

import java.util.UUID;

/** Validation shared by defensive SavedData readers and dependency-free tests. */
public final class SiegeSavedDataRecordPolicy {
    private SiegeSavedDataRecordPolicy() {
    }

    public static boolean acceptsPair(boolean firstPresent, boolean secondPresent,
                                      UUID first, UUID second, long remaining,
                                      boolean requireDistinct) {
        return firstPresent && secondPresent && first != null && second != null
                && remaining > 0L && (!requireDistinct || !first.equals(second));
    }
}
