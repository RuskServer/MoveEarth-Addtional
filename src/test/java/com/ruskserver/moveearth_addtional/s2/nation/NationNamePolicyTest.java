package com.ruskserver.moveearth_addtional.s2.nation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class NationNamePolicyTest {
    @Test
    void acceptsJapaneseNameAndNormalizesTag() {
        var result = NationNamePolicy.validate("  東方 連邦  ", "  me2 ");
        assertTrue(result.valid());
        assertEquals("東方 連邦", result.name());
        assertEquals("ME2", result.tag());
    }

    @Test
    void rejectsFormattingAndCommandCharacters() {
        assertFalse(NationNamePolicy.validate("Bad§Name", "BAD").valid());
        assertFalse(NationNamePolicy.validate("Bad/Name", "BAD").valid());
        assertFalse(NationNamePolicy.validate("Valid Name", "A!").valid());
    }

    @Test
    void enforcesLengths() {
        assertFalse(NationNamePolicy.validate("ab", "TAG").valid());
        assertFalse(NationNamePolicy.validate("Valid", "A").valid());
        assertFalse(NationNamePolicy.validate("Valid", "TOOLONG").valid());
    }
}
