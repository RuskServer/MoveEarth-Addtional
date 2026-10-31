package com.ruskserver.moveearth_addtional.compat.yigd;

import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class GraveAccessPolicyTest {
    private final UUID player = UUID.randomUUID(), home = UUID.randomUUID(), enemy = UUID.randomUUID();
    private GraveAccessPolicy.Ownership locked() { return new GraveAccessPolicy.Ownership(player, home, false); }

    @Test void protectedGraveUsesCitizensAlliesAndSiegeGrants() {
        var owner = locked().placed(true);
        assertFalse(owner.allows(UUID.randomUUID(), enemy, false, false, false));
        assertTrue(owner.allows(UUID.randomUUID(), home, false, false, false));
        assertTrue(owner.allows(UUID.randomUUID(), enemy, true, false, false));
        assertTrue(owner.allows(UUID.randomUUID(), enemy, false, true, false));
        assertTrue(owner.allows(player, null, false, false, false));
        assertTrue(owner.allows(UUID.randomUUID(), null, false, false, true));
    }

    @Test void outsidePlacementPermanentlyUnlocksEvenWhenReturnedHome() {
        var extracted = locked().placed(false).placed(true);
        assertTrue(extracted.unlocked());
        assertEquals(home, extracted.nation());
        assertTrue(extracted.allows(UUID.randomUUID(), null, false, false, false));
    }

    @Test void nationlessDeathCannotBecomeProtectedLater() {
        var owner = new GraveAccessPolicy.Ownership(player, null, false).placed(true);
        assertTrue(owner.unlocked());
        assertTrue(owner.allows(UUID.randomUUID(), enemy, false, false, false));
    }
}
