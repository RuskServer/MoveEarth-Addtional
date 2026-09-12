package com.ruskserver.moveearth_addtional.s2.siege;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PrisonerPairPolicyTest {
    @Test
    void matchesPrisonersHeldInEitherDirection() {
        UUID green = UUID.randomUUID();
        UUID blue = UUID.randomUUID();
        assertTrue(PrisonerPairPolicy.matches(green, blue, green, blue));
        assertTrue(PrisonerPairPolicy.matches(blue, green, green, blue));
    }

    @Test
    void rejectsThirdNationAndMissingIds() {
        UUID green = UUID.randomUUID();
        UUID blue = UUID.randomUUID();
        assertFalse(PrisonerPairPolicy.matches(green, UUID.randomUUID(), green, blue));
        assertFalse(PrisonerPairPolicy.matches(null, blue, green, blue));
    }
}
