package com.ruskserver.moveearth_addtional.territory.domain;

import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TerritoryMembershipTest {
    private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID MEMBER = UUID.fromString("00000000-0000-0000-0000-000000000002");

    @Test
    void ownerIsAlwaysIncluded() {
        TerritoryMembership membership = new TerritoryMembership(TerritoryOwnerId.of(OWNER), Set.of());

        assertTrue(membership.includes(OWNER, "Owner"));
    }

    @Test
    void existingWhitelistNamesAreUsedWithoutSeparateNationData() {
        TerritoryMembership membership = new TerritoryMembership(
                TerritoryOwnerId.of(OWNER), Set.of("Member"));

        assertTrue(membership.includes(MEMBER, "Member"));
        assertFalse(membership.includes(MEMBER, "member"));
        assertFalse(membership.includes(MEMBER, "Outsider"));
    }
}
