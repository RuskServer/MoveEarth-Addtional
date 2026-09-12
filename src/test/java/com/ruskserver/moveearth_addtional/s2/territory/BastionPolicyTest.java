package com.ruskserver.moveearth_addtional.s2.territory;

import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BastionPolicyTest {
    private static final UUID OWNER = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID FOREIGNER = UUID.fromString("20000000-0000-0000-0000-000000000002");

    @Test
    void allowsActionsOutsideControlledTerritory() {
        assertFalse(BastionPolicy.isRestricted(Optional.empty(), Optional.empty(), false, false, false));
    }

    @Test
    void allowsMembersOfTheControllingNation() {
        assertFalse(BastionPolicy.isRestricted(Optional.of(OWNER), Optional.of(OWNER), false, true, false));
    }

    @Test
    void rejectsForeignAndNationlessPlayers() {
        assertTrue(BastionPolicy.isRestricted(Optional.of(OWNER), Optional.of(FOREIGNER), false, true, false));
        assertTrue(BastionPolicy.isRestricted(Optional.of(OWNER), Optional.empty(), false, false, false));
    }

    @Test
    void allowsAlliesOnlyWhenTheirRoleHasBastionAccess() {
        assertFalse(BastionPolicy.isRestricted(Optional.of(OWNER), Optional.of(FOREIGNER), true, true, false));
        assertTrue(BastionPolicy.isRestricted(Optional.of(OWNER), Optional.of(FOREIGNER), true, false, false));
        assertTrue(BastionPolicy.isRestricted(Optional.of(OWNER), Optional.of(OWNER), false, false, false));
    }

    @Test
    void allowsAdministrativeBypass() {
        assertFalse(BastionPolicy.isRestricted(Optional.of(OWNER), Optional.of(FOREIGNER), false, false, true));
    }
}
