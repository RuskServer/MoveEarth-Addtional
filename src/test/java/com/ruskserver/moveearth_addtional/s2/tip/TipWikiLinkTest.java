package com.ruskserver.moveearth_addtional.s2.tip;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TipWikiLinkTest {
    @Test
    void wikiUrlMustBeEmptyOrAWebAddress() {
        assertTrue(TipWikiLink.isValid(""));
        assertTrue(TipWikiLink.isValid(null));
        assertTrue(TipWikiLink.isValid("https://wiki.example.org/moveearth"));
        assertTrue(TipWikiLink.isValid("http://example.org"));
        assertFalse(TipWikiLink.isValid("javascript:alert(1)"));
        assertFalse(TipWikiLink.isValid("file:///etc/passwd"));
    }
}
