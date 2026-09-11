package com.ruskserver.moveearth_addtional.s2.nation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RoleNamePolicyTest {
    @Test
    void normalizesWhitespaceAndAcceptsJapaneseNames() {
        RoleNamePolicy.Validation result = RoleNamePolicy.validate("  建築   担当  ");
        assertTrue(result.valid());
        assertEquals("建築 担当", result.name());
    }

    @Test
    void rejectsTooShortAndUnsupportedCharacters() {
        assertEquals("length", RoleNamePolicy.validate("A").reason());
        assertEquals("characters", RoleNamePolicy.validate("Admin@Role").reason());
    }

    @Test
    void acceptsConfiguredBoundaryLengths() {
        assertTrue(RoleNamePolicy.validate("AB").valid());
        assertTrue(RoleNamePolicy.validate("A".repeat(RoleNamePolicy.MAX_LENGTH)).valid());
        assertFalse(RoleNamePolicy.validate("A".repeat(RoleNamePolicy.MAX_LENGTH + 1)).valid());
    }
}
