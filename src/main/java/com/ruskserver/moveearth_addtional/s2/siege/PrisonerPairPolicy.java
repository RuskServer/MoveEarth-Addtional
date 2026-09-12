package com.ruskserver.moveearth_addtional.s2.siege;

import java.util.UUID;

/** Pure bilateral ownership rule shared by prisoner persistence and tests. */
public final class PrisonerPairPolicy {
    private PrisonerPairPolicy() { }

    public static boolean matches(UUID homeNation, UUID holdingNation,
                                  UUID firstNation, UUID secondNation) {
        if (homeNation == null || holdingNation == null || firstNation == null || secondNation == null) {
            return false;
        }
        return homeNation.equals(firstNation) && holdingNation.equals(secondNation)
                || homeNation.equals(secondNation) && holdingNation.equals(firstNation);
    }
}
